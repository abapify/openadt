#!/usr/bin/env bun
/**
 * Headless launcher for SAP ADT MCP (adt-lsc + adtLs/mcp/startMCPServer).
 * See specs/mcp.md.
 */
import { locateAdtLs } from "./locate.ts";
import {
  parseListArgv,
  parsePrintConfigArgv,
  parseServeArgv,
  parseStatusArgv,
  parseSubcommandArgv,
} from "./config.ts";
import {
  endpointFilePath,
  listEndpoints,
  mcpEndpointsDir,
  removeEndpoint,
  resolveEndpointPort,
  stopTrackedMcpServers,
  writeEndpoint,
} from "./endpoint-store.ts";
import { resolveDestinationImport } from "./gui-import.ts";
import { createMcpLog, eclipseWorkspaceLogPath } from "./log.ts";
import { isVsCodeAdtWorkspacePath, type WorkspacePath } from "./runtime-env.ts";
import { connectAdtLanguageServer, disposeLspSession } from "./lsp-client.ts";
import { killProcessTree } from "./process.ts";
import {
  mcpHttpClientConfig,
  generateMcpToken,
  isPortInUseMessage,
  mcpUrl,
  probeMcpHttp,
  redactToken,
  setMcpDestination,
  startMcpServer,
  stopMcpServer,
  waitForMcpHttp,
} from "./mcp.ts";
import { MARKETPLACE_URL, type McpServeConfig } from "./types.ts";
import {
  createStdioMcpBridge,
  McpHttpEndpoint,
  type StdioMcpBridge,
} from "./stdio-proxy.ts";

const EXIT_OK = 0;
const EXIT_NO_EXTENSION = 1;
const EXIT_LSC_START = 2;
const EXIT_LSP_MCP = 3;
const EXIT_PORT_IN_USE = 4;

function usage(): void {
  console.error(`Usage: openadt mcp <command>

Commands:
  serve         Start SAP ADT language server and MCP HTTP endpoint
  status        Probe MCP HTTP endpoint
  list          List active MCP endpoints (one store file per port)
  print-config  Emit HTTP MCP client JSON (url + headers) from endpoint store

  serve --stdio   Stdio MCP transport (proxies stdin/stdout to local HTTP MCP)

Install SAP ADT for VS Code: ${MARKETPLACE_URL}
`);
}

function extensionMissing(): never {
  console.error(
    `SAP ADT VS Code extension not found (sapse.adt-vscode).\n` +
      `Install from: ${MARKETPLACE_URL}\n` +
      `Override launcher path with ADT_LS_PATH for development.`,
  );
  process.exit(EXIT_NO_EXTENSION);
}

type ServerState = {
  url: string;
  port: number;
  token: string;
  endpointFile: string;
  version?: string;
  extensionVersion: string;
  adtLscPath: string;
  workspace: string;
  importFrom: string;
  importSource?: string;
  destinations: string[];
};

type LspSession = Awaited<ReturnType<typeof connectAdtLanguageServer>>;

function printImportNotices(
  cfg: McpServeConfig,
  gui: ReturnType<typeof resolveDestinationImport>,
): void {
  if (gui.imported.length === 0) {
    if (cfg.importFrom !== "none") {
      console.error(
        "No destinations to import.\n" +
          "ADT LS store: log on in VS Code (creates ~/.adtls/destinations.json), or\n" +
          "GUI: Add Destination as Folder to Workspace, or `openadt setup` for ~/.openadt fallback.",
      );
    }
    return;
  }
  if (cfg.json) {
    return;
  }
  const ids = gui.imported.map((d) => d.id).join(", ");
  const via = gui.importSource ?? cfg.importFrom;
  console.error(
    `Imported ${gui.imported.length} destination(s) from ${via}: ${ids}`,
  );
  if (gui.fileUris.length > 0) {
    console.error(
      `Registered ${gui.fileUris.length} destination file(s) for adt-lsc.`,
    );
  } else if (gui.imported.length > 0) {
    console.error(
      "[openadt-mcp] Warning: no destination file URIs — logon may fail. Update openadt from git clone.",
    );
  }
  warnWorkspaceMismatch(cfg, gui);
}

function warnWorkspaceMismatch(
  cfg: McpServeConfig,
  gui: ReturnType<typeof resolveDestinationImport>,
): void {
  if (!cfg.explicitWorkspace) return;
  if (!isVsCodeAdtWorkspacePath(cfg.workspace as WorkspacePath)) return;
  if (gui.workspace === cfg.workspace) return;
  console.error(
    `Using separate adt-lsc workspace: ${gui.workspace}\n` +
      "(VS Code adtWorkspace is not used as -data to avoid lock conflicts.)",
  );
}

function printServeState(state: ServerState, cfg: McpServeConfig): void {
  if (cfg.stdio) {
    console.error(`SAP ADT MCP stdio proxy → ${state.url}`);
    return;
  }
  if (cfg.json) {
    const out = { ...state };
    if (!cfg.showToken) out.token = redactToken(out.token);
    console.log(JSON.stringify(out, null, 2));
    return;
  }
  console.log(`SAP ADT MCP listening at ${state.url}`);
  console.log(`Client JSON: openadt mcp print-config --port ${state.port}`);
  console.log(`Endpoint store: ${state.endpointFile}`);
  console.log(
    `Extension: ${state.extensionVersion} · Workspace: ${cfg.workspace}`,
  );
  if (cfg.showToken) console.log(`Bearer token: ${state.token}`);
  console.log("Press Ctrl+C to stop.");
}

function failStdioAndExit(
  bridge: StdioMcpBridge | undefined,
  code: number,
  message: string,
): Promise<number> {
  if (bridge) {
    console.error(`[openadt-mcp] ${message}`);
    bridge.failPending(-32000, message);
    return bridge.flush().then(() => code);
  }
  console.error(message);
  return Promise.resolve(code);
}

async function shutdown(
  session: LspSession | undefined,
  endpointPort: number | undefined,
  endpointWritten: boolean,
): Promise<void> {
  if (session) {
    try {
      await stopMcpServer(session.connection);
    } catch {
      /* server may already be down */
    }
    killProcessTree(session.child);
    await disposeLspSession(session);
  }
  if (endpointWritten && endpointPort !== undefined) {
    removeEndpoint(endpointPort);
  }
}

async function waitForIdleHttpServe(session: LspSession): Promise<void> {
  await new Promise<void>((resolve) => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      process.off("SIGINT", onSignal);
      process.off("SIGTERM", onSignal);
      session.child.off("exit", onExit);
      resolve();
    };
    const onSignal = () => {
      console.error("[openadt-mcp] Shutting down…");
      finish();
    };
    const onExit = () => {
      console.error("[openadt-mcp] adt-lsc exited; shutting down.");
      finish();
    };
    process.on("SIGINT", onSignal);
    process.on("SIGTERM", onSignal);
    session.child.on("exit", onExit);
  });
}

async function cmdServe(argv: string[]): Promise<number> {
  const cfg = parseServeArgv(argv);

  const bridge = cfg.stdio ? createStdioMcpBridge() : undefined;
  if (bridge) {
    bridge.start();
  }

  const install = locateAdtLs();
  if (!install) {
    return failStdioAndExit(
      bridge,
      EXIT_NO_EXTENSION,
      "SAP ADT VS Code extension not found (sapse.adt-vscode). Install from marketplace or set ADT_LS_PATH.",
    );
  }

  const gui = resolveDestinationImport(
    cfg.workspace,
    cfg.importFrom,
    cfg.explicitWorkspace,
  );
  printImportNotices(cfg, gui);

  const stopped = await stopTrackedMcpServers({ onlyPort: cfg.port });
  if (stopped > 0 && !cfg.json) {
    console.error(
      `[openadt-mcp] Stopped ${stopped} previous MCP serve instance(s) on port ${cfg.port}.`,
    );
  }

  const log = createMcpLog({ verbose: cfg.verbose, logFile: cfg.logFile });
  logImportDiagnostics(log, gui, cfg);

  const token = generateMcpToken();
  const connectResult = await connectLanguageServer(install, cfg, gui, log);
  if ("exit" in connectResult) {
    log?.dispose();
    return failStdioAndExit(bridge, connectResult.exit, connectResult.message);
  }
  const { session } = connectResult;

  let endpointWritten = false;
  let endpointPort: number | undefined;
  try {
    const started = await startMcpHttpAndApplyDestination(session.connection, {
      port: cfg.port,
      token,
      destination: cfg.destination,
      log,
    });
    endpointPort = started.port;
    const endpoint = buildEndpointRecord(started, session, gui, cfg);
    writeEndpoint(endpoint);
    endpointWritten = true;
    printServeState(
      toServerState({
        endpoint,
        install,
        cfg,
        gui,
        version: started.version,
      }),
      cfg,
    );

    if (cfg.stdio && bridge) {
      await runStdioBridgeOrHttpLoop(bridge, session, started);
      return EXIT_OK;
    }
    await waitForIdleHttpServe(session);
    return EXIT_OK;
  } catch (err) {
    return failServeError(bridge, cfg, err);
  } finally {
    await shutdown(session, endpointPort, endpointWritten);
    log?.dispose();
  }
}

function logImportDiagnostics(
  log: ReturnType<typeof createMcpLog>,
  gui: ReturnType<typeof resolveDestinationImport>,
  cfg: McpServeConfig,
): void {
  if (!log) {
    return;
  }
  console.error(`MCP debug log: ${log.logPath}`);
  console.error(
    `adt-lsc Eclipse log: ${eclipseWorkspaceLogPath(gui.workspace)}`,
  );
  log.info(`import source: ${gui.importSource ?? cfg.importFrom}`);
  log.info(`destinations store: ${gui.destinationsStorePath ?? "(none)"}`);
  log.info(
    `workspace folders: ${gui.workspaceFolderUris.join(", ") || "(none)"}`,
  );
}

async function startMcpHttpAndApplyDestination(
  connection: LspSession["connection"],
  options: {
    port: number;
    token: string;
    destination: string | undefined;
    log: ReturnType<typeof createMcpLog>;
  },
): Promise<{ port: number; token: string; version?: string }> {
  options.log?.info(`LSP → adtLs/mcp/startMCPServer port=${options.port}`);
  const started = await startMcpServer(connection, {
    port: options.port,
    token: options.token,
  });
  options.log?.info(
    `LSP ← adtLs/mcp/startMCPServer port=${started.port} version=${started.version ?? "?"}`,
  );
  try {
    const ready = await waitForMcpHttp(started.port, started.token);
    if (!ready) {
      throw new Error(
        `MCP HTTP not ready at ${mcpUrl(started.port)} within timeout`,
      );
    }
  } catch (err) {
    options.log?.error(
      `MCP HTTP not ready at port ${started.port}: ${formatError(err)}`,
    );
    throw err;
  }
  if (options.destination) {
    options.log?.info(`LSP → adtLs/mcp/setDestination ${options.destination}`);
    await setMcpDestination(connection, options.destination);
    options.log?.info(`LSP ← adtLs/mcp/setDestination ok`);
  }
  return started;
}

type EndpointRecord = ReturnType<typeof buildEndpointRecord>;

function buildEndpointRecord(
  started: { port: number; token: string; version?: string },
  session: LspSession,
  gui: ReturnType<typeof resolveDestinationImport>,
  cfg: McpServeConfig,
) {
  return {
    port: started.port,
    url: mcpUrl(started.port),
    token: started.token,
    pid: process.pid,
    adtLscPid: session.child.pid ?? undefined,
    startedAt: new Date().toISOString(),
    destination: cfg.destination,
    destinations: gui.imported.map((d) => d.id),
    workspace: gui.workspace,
  };
}

function toServerState(input: {
  endpoint: EndpointRecord;
  install: NonNullable<ReturnType<typeof locateAdtLs>>;
  cfg: McpServeConfig;
  gui: ReturnType<typeof resolveDestinationImport>;
  version: string | undefined;
}): ServerState {
  const { endpoint, install, cfg, gui, version } = input;
  return {
    url: endpoint.url,
    port: endpoint.port,
    token: endpoint.token,
    endpointFile: endpointFilePath(endpoint.port),
    version,
    extensionVersion: install.version,
    adtLscPath: install.adtLscPath,
    workspace: gui.workspace,
    importFrom: cfg.importFrom,
    importSource: gui.importSource,
    destinations: endpoint.destinations,
  };
}

async function runStdioBridgeOrHttpLoop(
  bridge: StdioMcpBridge,
  session: LspSession,
  started: { port: number; token: string },
): Promise<void> {
  const runPromise = bridge.run(
    McpHttpEndpoint.forConfig(started.port, started.token),
  );
  await Promise.race([
    runPromise,
    waitForIdleHttpServe(session).then(() => runPromise),
  ]);
}

function failServeError(
  bridge: StdioMcpBridge | undefined,
  cfg: McpServeConfig,
  err: unknown,
): Promise<number> {
  const msg = formatError(err);
  if (isPortInUseMessage(msg)) {
    return failStdioAndExit(
      bridge,
      EXIT_PORT_IN_USE,
      `Port ${cfg.port} is already in use.`,
    );
  }
  return failStdioAndExit(
    bridge,
    EXIT_LSP_MCP,
    `adtLs/mcp/startMCPServer failed: ${msg}`,
  );
}

type ConnectResult =
  | { session: LspSession }
  | { exit: number; message: string };

async function connectLanguageServer(
  install: NonNullable<ReturnType<typeof locateAdtLs>>,
  cfg: McpServeConfig,
  gui: ReturnType<typeof resolveDestinationImport>,
  log: ReturnType<typeof createMcpLog>,
): Promise<ConnectResult> {
  try {
    const destinationIds = gui.imported.map((d) => d.id);
    if (gui.imported.length > 0 && gui.fileUris.length === 0) {
      throw new Error(
        "Destination import produced no fileUris for adt-lsc — update openadt launcher (git pull) and retry.",
      );
    }
    // Always logon synchronously before starting MCP (SAP MCP has 30s request timeout)
    if (destinationIds.length > 0) {
      console.error(
        "[openadt-mcp] SAP logon in progress — approve SSO / Secure Login if prompted.",
      );
    }
    const session = await connectAdtLanguageServer(install, gui.workspace, {
      workspaceFolderUris: gui.workspaceFolderUris,
      destinationsStorePath: gui.destinationsStorePath ?? "",
      fileUris: gui.fileUris,
      createProjectIds: destinationIds,
      ensureLoggedOnIds: destinationIds, // Always ensure logon before MCP start
      logonTimeoutMs: cfg.logonTimeoutMs,
      log,
    });
    return { session };
  } catch (err) {
    const message = formatError(err);
    console.error(`Failed to start adt-lsc or LSP handshake: ${message}`);
    return { exit: EXIT_LSC_START, message };
  }
}

async function cmdStatus(argv: string[]): Promise<number> {
  const {
    port: requestedPort,
    token: explicitToken,
    json,
  } = parseStatusArgv(argv);
  const resolved = resolveEndpointPort(requestedPort);
  if (!resolved.ok) {
    console.error(resolved.message);
    return 1;
  }
  const { port, record } = resolved;
  const token = explicitToken ?? record.token;
  const ok = await probeMcpHttp(port, token);
  if (json) {
    console.log(
      JSON.stringify({
        port,
        url: mcpUrl(port),
        reachable: ok,
        endpointFile: endpointFilePath(port),
      }),
    );
  } else if (ok) {
    console.log(`MCP reachable at ${mcpUrl(port)}`);
  } else {
    console.error(`MCP not reachable at ${mcpUrl(port)}`);
  }
  return ok ? EXIT_OK : 1;
}

function cmdList(argv: string[]): number {
  const { json } = parseListArgv(argv);
  const endpoints = listEndpoints();
  if (json) {
    console.log(
      JSON.stringify(
        endpoints.map((e) => ({
          port: e.port,
          url: e.url,
          destinations: e.destinations,
          destination: e.destination,
          pid: e.pid,
          startedAt: e.startedAt,
          endpointFile: endpointFilePath(e.port),
        })),
        null,
        2,
      ),
    );
  } else if (endpoints.length === 0) {
    console.error(`No active MCP endpoints in ${mcpEndpointsDir()}`);
  } else {
    for (const e of endpoints) {
      const dests = e.destinations.join(", ") || "(none)";
      console.log(`${e.port}\t${e.url}\t${dests}`);
    }
  }
  return endpoints.length > 0 ? EXIT_OK : 1;
}

function cmdPrintConfig(argv: string[]): number {
  const { port: requestedPort, json } = parsePrintConfigArgv(argv);
  const resolved = resolveEndpointPort(requestedPort);
  if (!resolved.ok) {
    console.error(resolved.message);
    return 1;
  }
  const { port, record } = resolved;
  const snippet = mcpHttpClientConfig(port, record.token);
  console.log(JSON.stringify(snippet, null, 2));
  if (!json) {
    console.error(
      `\nFrom endpoint ${endpointFilePath(port)} · active servers: list`,
    );
  }
  return EXIT_OK;
}

function formatError(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  return String(err);
}

const subcmd = parseSubcommandArgv(process.argv.slice(2));

if (!subcmd) {
  usage();
  process.exit(EXIT_OK);
}

const code = await (async (): Promise<number> => {
  switch (subcmd.name) {
    case "serve":
      return cmdServe(subcmd.argv);
    case "status":
      return cmdStatus(subcmd.argv);
    case "list":
      return cmdList(subcmd.argv);
    case "print-config":
      return cmdPrintConfig(subcmd.argv);
    default:
      usage();
      return 1;
  }
})();

process.exit(code);
