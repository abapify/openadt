#!/usr/bin/env bun
/**
 * Headless launcher for SAP ADT MCP (adt-lsc + adtLs/mcp/startMCPServer).
 * See specs/mcp.md, specs/mcp-shared-backend.md.
 */
import { locateAdtLs } from "./locate.ts";
import {
  parseBridgeArgv,
  parseListArgv,
  parsePrintConfigArgv,
  parseServeArgv,
  parseStatusArgv,
  parseStopArgv,
  parseSubcommandArgv,
} from "./config.ts";
import {
  endpointFilePath,
  findHealthyEndpoint,
  listEndpoints,
  mcpEndpointsDir,
  removeEndpoint,
  resolveEndpointPort,
  stopTrackedMcpServers,
  writeEndpoint,
} from "./endpoint-store.ts";
import {
  ensureSharedBackend,
  type EnsureSharedBackendResult,
} from "./ensure-backend.ts";
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
import {
  DEFAULT_IMPORT_FROM,
  MARKETPLACE_URL,
  type McpServeConfig,
} from "./types.ts";
import {
  createStdioMcpBridge,
  McpHttpEndpoint,
  type StdioMcpBridge,
} from "./stdio-proxy.ts";
import {
  connectionRequester,
  HttpReadBackend,
  LspReadBackend,
  prewarm,
  readEnabled,
} from "./read-object.ts";
import { startReadAuxServer, type ReadAuxServer } from "./read-server.ts";
import { DEFAULT_LOGON_TIMEOUT_MS } from "./logon-handlers.ts";

const EXIT_OK = 0;
const EXIT_NO_EXTENSION = 1;
const EXIT_LSC_START = 2;
const EXIT_LSP_MCP = 3;
const EXIT_PORT_IN_USE = 4;
const EXIT_AMBIGUOUS = 5;
const EXIT_LOCK_TIMEOUT = 6;
const EXIT_SPAWN_FAILED = 7;
const EXIT_NO_BACKEND = 8;

function usage(): void {
  console.error(`Usage: openadt mcp <command>

Commands:
  serve         Start SAP ADT language server and MCP HTTP endpoint
  serve --stdio Stdio MCP transport; shared (ensure + attach) by default
  stop          Stop MCP backend(s) tracked in endpoint store
  bridge        Attach stdio to existing healthy backend
  status        Probe MCP HTTP endpoint
  list          List active MCP endpoints (one store file per port)
  print-config  Emit HTTP MCP client JSON (url + headers) from endpoint store

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

  // Route based on mode:
  // - `serve --stdio`           → shared (ensure + attach; default)
  // - `serve --stdio --standalone` → monolithic (own adt-lsc, kill on exit)
  // - `serve` (no --stdio)      → monolithic HTTP-only daemon
  if (cfg.stdio && !cfg.standalone) {
    return cmdServeSharedStdio(cfg);
  }
  return cmdServeStandalone(cfg);
}

/**
 * Standalone (monolithic) path: owns adt-lsc, kills on exit.
 * Used when `--standalone` is set, or for `serve` without --stdio.
 */
async function cmdServeStandalone(cfg: McpServeConfig): Promise<number> {
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

  const log = createMcpLog({ verbose: cfg.verbose, logFile: cfg.logFile });
  logImportDiagnostics(log, gui, cfg);

  const token = generateMcpToken();
  const connectResult = await connectLanguageServer(install, cfg, gui, log);
  if ("exit" in connectResult) {
    log?.dispose();
    return failStdioAndExit(bridge, connectResult.exit, connectResult.message);
  }
  const { session } = connectResult;
  const readBackend = buildStandaloneReadBackend(session);
  if (bridge && readBackend) {
    bridge.setReadBackend(readBackend);
  }

  return runStandaloneServeOrFail({
    bridge,
    session,
    readBackend,
    cfg,
    gui,
    log,
    token,
    install,
  });
}

function buildStandaloneReadBackend(
  session: LspSession,
): LspReadBackend | undefined {
  if (!readEnabled()) return undefined;
  return new LspReadBackend(connectionRequester(session.connection));
}

async function runStandaloneServeOrFail(input: {
  bridge: StdioMcpBridge | undefined;
  session: LspSession;
  readBackend: LspReadBackend | undefined;
  cfg: McpServeConfig;
  gui: ReturnType<typeof resolveDestinationImport>;
  log: ReturnType<typeof createMcpLog>;
  token: string;
  install: NonNullable<ReturnType<typeof locateAdtLs>>;
}): Promise<number> {
  const { bridge, session, readBackend, cfg, gui, log, token, install } = input;
  let auxServer: ReadAuxServer | undefined;
  let endpointPort: number | undefined;
  try {
    await stopPreviousServe(cfg);
    const result = await runStandaloneServe({
      bridge,
      session,
      readBackend,
      cfg,
      gui,
      log,
      token,
      install,
    });
    auxServer = result.auxServer;
    endpointPort = result.endpointPort;
    return result.exit;
  } catch (err) {
    return failServeError(bridge, cfg, err);
  } finally {
    auxServer?.stop();
    await shutdown(session, endpointPort, endpointPort !== undefined);
    log?.dispose();
  }
}

async function stopPreviousServe(cfg: McpServeConfig): Promise<void> {
  const stopped = await stopTrackedMcpServers({ onlyPort: cfg.port });
  if (stopped > 0 && !cfg.json) {
    console.error(
      `[openadt-mcp] Stopped ${stopped} previous MCP serve instance(s) on port ${cfg.port}.`,
    );
  }
}

type StandaloneServeResult = {
  endpointPort: number;
  auxServer: ReadAuxServer | undefined;
  exit: number;
};

async function runStandaloneServe(input: {
  bridge: StdioMcpBridge | undefined;
  session: LspSession;
  readBackend: LspReadBackend | undefined;
  cfg: McpServeConfig;
  gui: ReturnType<typeof resolveDestinationImport>;
  log: ReturnType<typeof createMcpLog>;
  token: string;
  install: NonNullable<ReturnType<typeof locateAdtLs>>;
}): Promise<StandaloneServeResult> {
  const { bridge, session, readBackend, cfg, gui, log, token, install } = input;
  const started = await startMcpHttpAndApplyDestination(session.connection, {
    port: cfg.port,
    token,
    destination: cfg.destination,
    log,
  });
  const auxServer = await maybeStartAuxServer(readBackend, cfg, log, session);
  prewarmIfNeeded(readBackend, gui, session);
  const endpoint = buildEndpointRecord({
    started,
    session,
    gui,
    cfg,
    mode: cfg.stdio ? "standalone" : "daemon",
    aux: auxServer,
  });
  writeEndpoint(endpoint);
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
  await serveUntilIdle(cfg, bridge, session, started);
  return { endpointPort: started.port, auxServer, exit: EXIT_OK };
}

async function maybeStartAuxServer(
  readBackend: LspReadBackend | undefined,
  cfg: McpServeConfig,
  log: ReturnType<typeof createMcpLog> | undefined,
  session: LspSession,
): Promise<ReadAuxServer | undefined> {
  if (!readBackend || cfg.stdio) return undefined;
  const aux = await startReadAuxServer(readBackend);
  log?.info(`read endpoint at ${aux.url}`);
  return aux;
}

function prewarmIfNeeded(
  readBackend: LspReadBackend | undefined,
  gui: ReturnType<typeof resolveDestinationImport>,
  session: LspSession,
): void {
  if (!readBackend || gui.imported.length === 0) return;
  const req = connectionRequester(session.connection);
  void Promise.allSettled(
    gui.imported.map((d) => prewarm(req, { destination: d.id })),
  );
}

async function serveUntilIdle(
  cfg: McpServeConfig,
  bridge: StdioMcpBridge | undefined,
  session: LspSession,
  started: { port: number; token: string },
): Promise<void> {
  if (cfg.stdio && bridge) {
    await runStdioBridgeOrHttpLoop(bridge, session, started);
    return;
  }
  await waitForIdleHttpServe(session);
}

/**
 * Shared stdio path: ensure a healthy backend, attach stdio bridge.
 * Bridge exit does NOT kill the backend.
 */
async function cmdServeSharedStdio(cfg: McpServeConfig): Promise<number> {
  const bridge = createStdioMcpBridge();
  bridge.start();

  const serveArgs = collectStandaloneServeArgs(cfg).filter(
    (a) => a !== "--stdio" && a !== "--standalone",
  );

  await maybeRestartSharedDaemon(cfg);

  try {
    const ensured = await ensureSharedBackend({
      // Only filter by port when the user asked for a specific one. Otherwise
      // attach to the single healthy endpoint in the store regardless of its
      // port (per specs/mcp-shared-backend.md §Attach resolution), so we never
      // spawn a duplicate adt-lsc next to an existing backend on another port.
      preferredPort: cfg.explicitPort ? cfg.port : undefined,
      serveArgs,
    });
    announceSharedAttach(cfg, ensured.url);
    wireReadBackendFromDaemon(cfg, bridge, ensured);
    await bridge.run(McpHttpEndpoint.forConfig(ensured.port, ensured.token));
    return EXIT_OK;
  } catch (err) {
    return exitCodeForEnsureError(err, bridge);
  }
}

async function maybeRestartSharedDaemon(cfg: McpServeConfig): Promise<void> {
  if (!cfg.restart) return;
  const stopped = await stopTrackedMcpServers({
    onlyPort: cfg.explicitPort ? cfg.port : undefined,
  });
  if (cfg.json) return;
  console.error(
    stopped > 0
      ? `[openadt-mcp] --restart: stopped ${stopped} backend(s); spawning a fresh one.`
      : `[openadt-mcp] --restart: no running backend to stop; spawning fresh.`,
  );
}

function announceSharedAttach(cfg: McpServeConfig, url: string): void {
  if (cfg.json) return;
  console.error(`[openadt-mcp] Attached to shared backend at ${url}`);
}

function wireReadBackendFromDaemon(
  cfg: McpServeConfig,
  bridge: StdioMcpBridge,
  ensured: { record: EnsureSharedBackendResult["record"] },
): void {
  if (!readEnabled()) return;
  const http = httpReadBackendFor(ensured.record);
  if (http) {
    bridge.setReadBackend(http);
    return;
  }
  warnReadToolsUnavailable(cfg);
}

function httpReadBackendFor(
  record: EnsureSharedBackendResult["record"],
): HttpReadBackend | undefined {
  const { auxUrl, auxToken } = record;
  if (auxUrl && auxToken) {
    return new HttpReadBackend(auxUrl, auxToken);
  }
  return undefined;
}

function warnReadToolsUnavailable(cfg: McpServeConfig): void {
  if (cfg.json) return;
  console.error(
    "[openadt-mcp] read tools unavailable (daemon has no read endpoint; restart the backend with: openadt mcp stop)",
  );
}

/** Map an `ensureSharedBackend` error code to the corresponding CLI exit code. */
function exitCodeForEnsureError(
  err: unknown,
  bridge: StdioMcpBridge,
): Promise<number> {
  const code = (err as NodeJS.ErrnoException).code;
  const message = `[openadt-mcp] ${formatError(err)}`;
  if (code === "OPENADT_MCP_AMBIGUOUS")
    return failStdioAndExit(bridge, EXIT_AMBIGUOUS, message);
  if (code === "OPENADT_MCP_TIMEOUT")
    return failStdioAndExit(bridge, EXIT_LOCK_TIMEOUT, message);
  if (code === "OPENADT_MCP_SPAWN_FAILED")
    return failStdioAndExit(bridge, EXIT_SPAWN_FAILED, message);
  return failStdioAndExit(
    bridge,
    EXIT_LSP_MCP,
    `ensure backend failed: ${formatError(err)}`,
  );
}

/**
 * Collect serve arguments that should be forwarded to the detached daemon.
 * Strips stdio-related flags (the daemon does not need them).
 */
function collectStandaloneServeArgs(cfg: McpServeConfig): string[] {
  const args: string[] = [];
  if (cfg.destination) {
    args.push("--destination", cfg.destination);
  }
  if (cfg.importFrom !== DEFAULT_IMPORT_FROM) {
    args.push(`--import-from=${cfg.importFrom}`);
  }
  if (cfg.explicitWorkspace) {
    args.push("--workspace", cfg.workspace);
  }
  if (cfg.logonTimeoutMs !== DEFAULT_LOGON_TIMEOUT_MS) {
    args.push("--logon-timeout", String(Math.floor(cfg.logonTimeoutMs / 1000)));
  }
  if (cfg.verbose) {
    args.push("--verbose");
  }
  if (cfg.logFile) {
    args.push("--log-file", cfg.logFile);
  }
  return args;
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
  const started = await startMcpHttp(connection, options);
  if (options.destination) {
    await applyDestination(connection, options.destination, options.log);
  }
  return started;
}

async function startMcpHttp(
  connection: LspSession["connection"],
  options: {
    port: number;
    token: string;
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
  return started;
}

async function applyDestination(
  connection: LspSession["connection"],
  destination: string,
  log: ReturnType<typeof createMcpLog>,
): Promise<void> {
  log?.info(`LSP → adtLs/mcp/setDestination ${destination}`);
  await setMcpDestination(connection, destination);
  log?.info(`LSP ← adtLs/mcp/setDestination ok`);
}

type EndpointRecord = ReturnType<typeof buildEndpointRecord>;

type EndpointRecordInput = {
  started: { port: number; token: string; version?: string };
  session: LspSession;
  gui: ReturnType<typeof resolveDestinationImport>;
  cfg: McpServeConfig;
  mode: "daemon" | "standalone";
  aux: { url: string; token: string } | undefined;
};

function buildEndpointRecord(input: EndpointRecordInput) {
  const { started, session, gui, cfg, mode, aux } = input;
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
    mode,
    auxUrl: aux?.url,
    auxToken: aux?.token,
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

/**
 * Stop MCP backend(s) tracked in the endpoint store.
 * Mirrors the legacy `stopTrackedMcpServers` behavior; exposes it as a CLI subcommand.
 */
async function cmdStop(argv: string[]): Promise<number> {
  const { port, json } = parseStopArgv(argv);
  const stopped = await stopTrackedMcpServers({ onlyPort: port });
  if (json) {
    console.log(JSON.stringify({ stopped, port: port ?? null }));
  } else if (stopped > 0) {
    const target = port !== undefined ? ` on port ${port}` : "";
    console.log(`Stopped ${stopped} MCP backend(s)${target}.`);
  } else {
    const target = port !== undefined ? ` on port ${port}` : "";
    console.log(`No active MCP backends${target}; nothing to stop.`);
  }
  return EXIT_OK;
}

/**
 * Attach stdio to an existing healthy backend (no spawn).
 * Fails if no healthy backend is found.
 */
async function cmdBridge(argv: string[]): Promise<number> {
  const { port, stdio, json } = parseBridgeArgv(argv);
  if (!stdio) {
    console.error("bridge subcommand requires --stdio");
    return 1;
  }

  const result = await findHealthyEndpoint(port);
  if (result.status === "none" || result.status === "unhealthy") {
    console.error("No healthy MCP backend. Start one with: openadt mcp serve");
    return EXIT_NO_BACKEND;
  }
  if (result.status === "ambiguous") {
    const ports = result.records.map((r) => String(r.port)).join(", ");
    console.error(
      `Multiple active MCP endpoints (ports ${ports}). ` +
        `Use: openadt mcp bridge --stdio --port <port>`,
    );
    return EXIT_AMBIGUOUS;
  }

  const record = result.record;
  if (!json) {
    console.error(
      `[openadt-mcp] Attached to existing backend at ${record.url}`,
    );
  }

  const bridge = createStdioMcpBridge();
  bridge.start();
  const http = httpReadBackendFor(record);
  if (http) {
    bridge.setReadBackend(http);
  } else if (!json) {
    console.error(
      "[openadt-mcp] read tools unavailable (daemon has no read endpoint; restart the backend with: openadt mcp stop)",
    );
  }
  await bridge.run(McpHttpEndpoint.forConfig(record.port, record.token));
  return EXIT_OK;
}

function formatError(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  return String(err);
}

const KNOWN_COMMANDS = [
  "serve",
  "stop",
  "bridge",
  "status",
  "list",
  "print-config",
] as const;

function levenshtein(a: string, b: string): number {
  const rows = Array.from({ length: a.length + 1 }, (_, i) => {
    const row = new Array<number>(b.length + 1).fill(0);
    row[0] = i;
    return row;
  });
  for (let j = 0; j <= b.length; j++) {
    rows[0]![j] = j;
  }
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      rows[i]![j] = Math.min(
        rows[i - 1]![j]! + 1,
        rows[i]![j - 1]! + 1,
        rows[i - 1]![j - 1]! + cost,
      );
    }
  }
  return rows[a.length]![b.length]!;
}

/** Closest known command within edit distance 2, or undefined. */
function suggestCommand(name: string): string | undefined {
  let best: string | undefined;
  let bestDist = 3;
  for (const cmd of KNOWN_COMMANDS) {
    const dist = levenshtein(name, cmd);
    if (dist < bestDist) {
      bestDist = dist;
      best = cmd;
    }
  }
  return best;
}

/**
 * Report an unknown subcommand. When invoked over stdio (an MCP client is
 * listening), exiting silently makes the client respawn us in a loop — so we
 * print a loud, actionable message that surfaces in the client's stderr pane.
 */
function unknownCommand(name: string, argv: string[]): void {
  const suggestion = suggestCommand(name);
  console.error(
    `Unknown command: '${name}'.` +
      (suggestion ? ` Did you mean '${suggestion}'?` : ""),
  );
  if (argv.includes("--stdio") && suggestion) {
    console.error(
      `Hint: your MCP client command should be \`openadt mcp ${suggestion} --stdio\`.`,
    );
  }
  console.error("");
  usage();
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
    case "stop":
      return cmdStop(subcmd.argv);
    case "bridge":
      return cmdBridge(subcmd.argv);
    case "status":
      return cmdStatus(subcmd.argv);
    case "list":
      return cmdList(subcmd.argv);
    case "print-config":
      return cmdPrintConfig(subcmd.argv);
    default:
      unknownCommand(subcmd.name, subcmd.argv);
      return 1;
  }
})();

process.exit(code);
