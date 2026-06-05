#!/usr/bin/env bun
/**
 * Headless launcher for SAP ADT MCP (adt-lsc + adtLs/mcp/startMCPServer).
 * See specs/mcp.md.
 */
import { locateAdtLs } from "./locate.ts";
import {
  parsePrintConfigArgv,
  parseServeArgv,
  parseStatusArgv,
  parseSubcommandArgv,
} from "./config.ts";
import { resolveDestinationImport } from "./gui-import.ts";
import { createMcpLog, eclipseWorkspaceLogPath } from "./log.ts";
import { isVsCodeAdtWorkspacePath } from "./runtime-env.ts";
import { connectAdtLanguageServer, disposeLspSession } from "./lsp-client.ts";
import { clearPidFile, killProcessTree, writePidFile } from "./process.ts";
import {
  cursorMcpSnippet,
  generateMcpToken,
  isPortInUseMessage,
  mcpUrl,
  probeMcpHttp,
  redactToken,
  setMcpDestination,
  startMcpServer,
  stopMcpServer,
} from "./mcp.ts";
import { MARKETPLACE_URL } from "./types.ts";

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
  print-config  Emit Cursor mcpServers JSON snippet

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

async function cmdServe(argv: string[]): Promise<number> {
  const cfg = parseServeArgv(argv);
  const install = locateAdtLs();
  if (!install) {
    extensionMissing();
  }

  const gui = resolveDestinationImport(
    cfg.workspace,
    cfg.importFrom,
    cfg.explicitWorkspace,
  );

  if (gui.imported.length === 0 && cfg.importFrom !== "none") {
    console.error(
      "No destinations to import.\n" +
        "ADT LS store: log on in VS Code (creates ~/.adtls/destinations.json), or\n" +
        "GUI: Add Destination as Folder to Workspace, or `openadt setup` for ~/.openadt fallback.",
    );
  } else if (gui.imported.length > 0 && !cfg.json) {
    const ids = gui.imported.map((d) => d.id).join(", ");
    const via = gui.importSource ?? cfg.importFrom;
    console.error(
      `Imported ${gui.imported.length} destination(s) from ${via}: ${ids}`,
    );
    if (
      cfg.explicitWorkspace &&
      isVsCodeAdtWorkspacePath(cfg.workspace) &&
      gui.workspace !== cfg.workspace
    ) {
      console.error(
        `Using separate adt-lsc workspace: ${gui.workspace}\n` +
          "(VS Code adtWorkspace is not used as -data to avoid lock conflicts.)",
      );
    }
  }

  const log = createMcpLog({ verbose: cfg.verbose, logFile: cfg.logFile });
  if (log) {
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

  const token = generateMcpToken();
  let session;
  try {
    const destinationIds = gui.imported.map((d) => d.id);
    session = await connectAdtLanguageServer(install, gui.workspace, {
      workspaceFolderUris: gui.workspaceFolderUris,
      destinationsStorePath: gui.destinationsStorePath ?? "",
      createProjectIds: destinationIds,
      ensureLoggedOnIds: destinationIds,
      logonTimeoutMs: cfg.logonTimeoutMs,
      log,
    });
  } catch (err) {
    console.error(
      `Failed to start adt-lsc or LSP handshake: ${formatError(err)}`,
    );
    log?.dispose();
    return EXIT_LSC_START;
  }

  if (session.child.pid) {
    writePidFile(session.child.pid);
  }

  try {
    log?.info(`LSP → adtLs/mcp/startMCPServer port=${cfg.port}`);
    const started = await startMcpServer(session.connection, {
      port: cfg.port,
      token,
    });
    log?.info(
      `LSP ← adtLs/mcp/startMCPServer port=${started.port} version=${started.version ?? "?"}`,
    );
    if (cfg.destination) {
      log?.info(`LSP → adtLs/mcp/setDestination ${cfg.destination}`);
      await setMcpDestination(session.connection, cfg.destination);
      log?.info(`LSP ← adtLs/mcp/setDestination ok`);
    }

    const state = {
      url: mcpUrl(started.port),
      port: started.port,
      token: started.token,
      version: started.version,
      extensionVersion: install.version,
      adtLscPath: install.adtLscPath,
      workspace: gui.workspace,
      importFrom: cfg.importFrom,
      importSource: gui.importSource,
      destinations: gui.imported.map((d) => d.id),
    };

    if (cfg.json) {
      const out = { ...state };
      if (!cfg.showToken) {
        out.token = redactToken(out.token);
      }
      console.log(JSON.stringify(out, null, 2));
    } else {
      console.log(`SAP ADT MCP listening at ${state.url}`);
      console.log(
        `Bearer token: ${cfg.showToken ? state.token : redactToken(state.token)}`,
      );
      console.log(
        `Extension: ${install.version} · Workspace: ${cfg.workspace}`,
      );
      if (!cfg.showToken) {
        console.log("Re-run with --show-token to print the full token.");
      }
      console.log("Press Ctrl+C to stop.");
    }

    await waitForShutdown(session, cfg.foreground);
    return EXIT_OK;
  } catch (err) {
    const msg = formatError(err);
    if (isPortInUseMessage(msg)) {
      console.error(`Port ${cfg.port} is already in use.`);
      return EXIT_PORT_IN_USE;
    }
    console.error(`adtLs/mcp/startMCPServer failed: ${msg}`);
    return EXIT_LSP_MCP;
  } finally {
    clearPidFile();
    if (session) {
      await disposeLspSession(session);
    }
    log?.dispose();
  }
}

async function waitForShutdown(
  session: Awaited<ReturnType<typeof connectAdtLanguageServer>>,
  _foreground: boolean,
): Promise<void> {
  await new Promise<void>((resolve) => {
    let settled = false;
    const onSignal = async () => {
      if (settled) return;
      settled = true;
      process.off("SIGINT", onSignal);
      process.off("SIGTERM", onSignal);
      session.child.off("exit", onExit);
      try {
        await stopMcpServer(session.connection);
      } catch {
        /* server may already be down */
      }
      killProcessTree(session.child);
      resolve();
    };
    const onExit = () => {
      if (settled) return;
      settled = true;
      process.off("SIGINT", onSignal);
      process.off("SIGTERM", onSignal);
      console.error("[openadt-mcp] adt-lsc exited; shutting down.");
      resolve();
    };
    process.on("SIGINT", onSignal);
    process.on("SIGTERM", onSignal);
    session.child.on("exit", onExit);
  });
}

async function cmdStatus(argv: string[]): Promise<number> {
  const { port, token, json } = parseStatusArgv(argv);
  const ok = await probeMcpHttp(port, token);
  if (json) {
    console.log(JSON.stringify({ port, url: mcpUrl(port), reachable: ok }));
  } else if (ok) {
    console.log(`MCP reachable at ${mcpUrl(port)}`);
  } else {
    console.error(`MCP not reachable at ${mcpUrl(port)}`);
  }
  return ok ? EXIT_OK : 1;
}

function cmdPrintConfig(argv: string[]): number {
  const { port, showToken, json } = parsePrintConfigArgv(argv);
  const token = showToken
    ? generateMcpToken()
    : "<run openadt mcp serve --show-token>";
  const snippet = cursorMcpSnippet(port, token);
  if (json) {
    console.log(JSON.stringify(snippet, null, 2));
  } else {
    console.log(JSON.stringify(snippet, null, 2));
    console.error(
      "\nReplace the Bearer token with the value from `openadt mcp serve --show-token`.",
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
    case "print-config":
      return cmdPrintConfig(subcmd.argv);
    default:
      usage();
      return 1;
  }
})();

process.exit(code);
