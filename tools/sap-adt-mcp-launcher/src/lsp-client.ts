import { type ChildProcess } from "node:child_process";
import {
  createClientPipeTransport,
  createMessageConnection,
  generateRandomPipeName,
  ParameterStructures,
  Trace,
  TraceFormat,
  type MessageConnection,
} from "./rpc.ts";
import type { McpLog } from "./log.ts";
import { redactSecrets } from "./log.ts";
import {
  DEFAULT_LOGON_TIMEOUT_MS,
  ensureDestinationLoggedOn,
  registerLogonHandlers,
} from "./logon-handlers.ts";
import {
  LSP_METHOD_DESTINATIONS_CREATE_PROJECT,
  LSP_METHOD_DESTINATIONS_GET_STORE_PATH,
  LSP_METHOD_DESTINATIONS_INIT,
  LSP_METHOD_DESTINATIONS_LIST,
  type DestinationsInitParams,
} from "./types.ts";
import { sleep, spawnAdtLsc } from "./process.ts";
import type { AdtLsInstall } from "./types.ts";

export type LspSession = {
  connection: MessageConnection;
  child: ChildProcess;
  pipeName: string;
};

const LSP_INIT_TIMEOUT_MS = 120_000;
const LAUNCHER_VERSION = "0.1.0";

/** Match SAP ADT VS Code extension — adt-lsc NPEs on logon without userAgentInfos. */
export function buildLspInitializeParams(install: AdtLsInstall): object {
  const clientCapabilities = {
    textDocument: { codeLens: { dynamicRegistration: true } },
  };
  return {
    processId: process.pid,
    rootUri: null,
    capabilities: clientCapabilities,
    clientInfo: {
      name: "openadt-sap-adt-mcp-launcher",
      version: LAUNCHER_VERSION,
    },
    initializationOptions: {
      capabilities: clientCapabilities,
      userAgentInfos: [
        { name: "ADTVSCode", version: install.version },
        { name: "OpenADT", version: LAUNCHER_VERSION },
      ],
    },
  };
}

export type ConnectAdtLanguageServerOptions = {
  workspaceFolderUris?: string[];
  destinationsStorePath?: string;
  fileUris?: string[];
  /**
   * Register each destination as an ABAP workspace project (VS Code:
   * "Add Destination as Folder to Workspace").
   */
  createProjectIds?: string[];
  /** After projects exist, attempt logon (opens browser / Secure Login when SAP asks). */
  ensureLoggedOnIds?: string[];
  logonTimeoutMs?: number;
  log?: McpLog;
};

export async function connectAdtLanguageServer(
  install: AdtLsInstall,
  workspace: string,
  options: ConnectAdtLanguageServerOptions = {},
): Promise<LspSession> {
  const log = options.log;
  const pipeName = generateRandomPipeName();
  log?.info(`LSP pipe: ${pipeName}`);
  log?.info(`adt-lsc: ${install.adtLscPath}`);
  log?.info(`workspace -data: ${workspace}`);

  const transport = await createClientPipeTransport(pipeName);
  const child = spawnAdtLsc(install, workspace, pipeName, {
    onStderrLine: log ? (line) => log.adtLscStderr(line) : undefined,
  });

  let exited = false;
  child.on("exit", (code, signal) => {
    exited = true;
    log?.warn(`adt-lsc exited code=${code ?? "?"} signal=${signal ?? ""}`);
  });
  child.on("error", (err) => {
    log?.error(`adt-lsc error: ${err.message}`);
  });

  let reader;
  let writer;
  try {
    [reader, writer] = await withTimeout(
      transport.onConnected(),
      LSP_INIT_TIMEOUT_MS,
      "LSP pipe connect",
    );
  } catch (err) {
    log?.error(
      `LSP pipe connect failed: ${err instanceof Error ? err.message : String(err)}`,
    );
    killQuiet(child);
    throw err;
  }
  const connection = createMessageConnection(
    reader,
    writer,
    log ? createLspConnectionLogger(log) : undefined,
  );
  registerLogonHandlers(connection, log);
  if (log) {
    connection.trace(
      Trace.Verbose,
      {
        log: (data: unknown) => {
          const text =
            typeof data === "string" ? data : JSON.stringify(data, null, 2);
          log.trace(redactSecrets(text));
        },
      },
      { traceFormat: TraceFormat.Text, sendNotification: false },
    );
    connection.onUnhandledNotification(
      (notification: { method: string; params?: unknown }) => {
        log.info(
          `LSP notification ← ${notification.method} ${summarizeParams(notification.params)}`,
        );
      },
    );
  }
  connection.listen();

  const initParams = buildLspInitializeParams(install);

  try {
    log?.info("LSP → initialize");
    const initResult = await withTimeout(
      connection.sendRequest("initialize", initParams),
      LSP_INIT_TIMEOUT_MS,
      "LSP initialize",
    );
    log?.info(`LSP ← initialize ${summarizeParams(initResult)}`);
    connection.sendNotification("initialized", {});

    const destInit: DestinationsInitParams = {
      destinationsStorePath: options.destinationsStorePath ?? "",
      workspaceFolderUris: options.workspaceFolderUris ?? [],
      fileUris: options.fileUris ?? [],
    };
    log?.info(
      `LSP → ${LSP_METHOD_DESTINATIONS_INIT} ${summarizeParams(destInit)}`,
    );
    await withTimeout(
      connection.sendRequest(
        LSP_METHOD_DESTINATIONS_INIT,
        ParameterStructures.byName,
        destInit,
      ),
      LSP_INIT_TIMEOUT_MS,
      LSP_METHOD_DESTINATIONS_INIT,
    );
    log?.info(`LSP ← ${LSP_METHOD_DESTINATIONS_INIT} ok`);

    const logonIds = new Set(options.ensureLoggedOnIds ?? []);
    const createOnlyIds = (options.createProjectIds ?? []).filter(
      (id) => !logonIds.has(id),
    );
    for (const destinationId of createOnlyIds) {
      // Best-effort: an already-registered destination that fails
      // createProject should not abort logon. createProjectAndLogon (below)
      // still runs the createProject+logon pair with a retry.
      try {
        await createProjectOnce(connection, destinationId, log);
      } catch (err) {
        log?.warn(
          `${LSP_METHOD_DESTINATIONS_CREATE_PROJECT} ${destinationId}: ${formatError(err)}`,
        );
      }
    }
    for (const destinationId of options.ensureLoggedOnIds ?? []) {
      await createProjectAndLogon(connection, destinationId, {
        logonTimeoutMs: options.logonTimeoutMs ?? DEFAULT_LOGON_TIMEOUT_MS,
        log,
      });
    }

    if (log) {
      await logDestinationDiagnostics(connection, log);
    }
  } catch (err) {
    connection.dispose();
    killQuiet(child);
    if (exited) {
      throw new Error(
        `adt-lsc exited before LSP handshake (${install.adtLscPath})`,
        { cause: err },
      );
    }
    throw err;
  }

  return { connection, child, pipeName };
}

async function createProjectOnce(
  connection: MessageConnection,
  destinationId: string,
  log?: McpLog,
): Promise<void> {
  log?.info(`LSP → ${LSP_METHOD_DESTINATIONS_CREATE_PROJECT} ${destinationId}`);
  await withTimeout(
    connection.sendRequest(
      LSP_METHOD_DESTINATIONS_CREATE_PROJECT,
      destinationId,
    ),
    LSP_INIT_TIMEOUT_MS,
    LSP_METHOD_DESTINATIONS_CREATE_PROJECT,
  );
  log?.info(
    `LSP ← ${LSP_METHOD_DESTINATIONS_CREATE_PROJECT} ${destinationId} ok`,
  );
}

function isRetryableLogonError(message: string): boolean {
  return (
    /does not exist/i.test(message) ||
    /JCO_ERROR_RESOURCE/i.test(message) ||
    /Internal error/i.test(message)
  );
}

async function createProjectAndLogon(
  connection: MessageConnection,
  destinationId: string,
  options: { logonTimeoutMs: number; log?: McpLog },
): Promise<void> {
  const { logonTimeoutMs, log } = options;
  const delaysMs = [750, 1_500];
  let lastError: Error | undefined;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      await runLogonAttempt({
        connection,
        destinationId,
        logonTimeoutMs,
        preLogonDelayMs: delaysMs[attempt]!,
        log,
      });
      return;
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(formatError(err));
      if (shouldRetryLogon(attempt, lastError)) {
        logRetryWarning(destinationId, lastError, log);
        await sleep(1_000);
        continue;
      }
      throw lastError;
    }
  }
  throw lastError ?? new Error(`Logon failed for ${destinationId}`);
}

function shouldRetryLogon(attempt: number, error: Error): boolean {
  return attempt === 0 && isRetryableLogonError(error.message);
}

function logRetryWarning(
  destinationId: string,
  error: Error,
  log: McpLog | undefined,
): void {
  log?.warn(
    `Logon for ${destinationId} failed (${error.message}); retrying createProject+logon once.`,
  );
  console.error(
    `[openadt-mcp] Logon failed (${error.message}); retrying once…`,
  );
}

async function runLogonAttempt(input: {
  connection: MessageConnection;
  destinationId: string;
  logonTimeoutMs: number;
  preLogonDelayMs: number;
  log: McpLog | undefined;
}): Promise<void> {
  await createProjectOnce(input.connection, input.destinationId, input.log);
  await sleep(input.preLogonDelayMs);
  await ensureDestinationLoggedOn(input.connection, input.destinationId, {
    timeoutMs: input.logonTimeoutMs,
    log: input.log,
  });
}

export async function logDestinationDiagnostics(
  connection: MessageConnection,
  log: McpLog,
): Promise<void> {
  for (const method of [
    LSP_METHOD_DESTINATIONS_LIST,
    LSP_METHOD_DESTINATIONS_GET_STORE_PATH,
  ]) {
    try {
      const result = await connection.sendRequest(method);
      log.info(`${method}: ${summarizeParams(result)}`);
    } catch (err) {
      log.warn(`${method}: ${formatError(err)}`);
    }
  }
}

export async function disposeLspSession(session: LspSession): Promise<void> {
  try {
    session.connection.dispose();
  } catch {
    /* ignore */
  }
  killQuiet(session.child);
}

function createLspConnectionLogger(log: McpLog) {
  return {
    error: (message: string) => log.error(`jsonrpc: ${message}`),
    warn: (message: string) => log.warn(`jsonrpc: ${message}`),
    info: (message: string) => log.trace(`jsonrpc: ${message}`),
    log: (message: string) => log.trace(`jsonrpc: ${message}`),
  };
}

function summarizeParams(value: unknown): string {
  if (value === undefined) {
    return "";
  }
  try {
    const text = JSON.stringify(value);
    return redactSecrets(text.length > 800 ? `${text.slice(0, 800)}…` : text);
  } catch {
    return String(value);
  }
}

function formatError(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  return String(err);
}

function killQuiet(child: ChildProcess): void {
  try {
    child.kill();
  } catch {
    /* ignore */
  }
}

function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  label: string,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`${label} timed out after ${ms}ms`)),
      ms,
    );
    promise.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e) => {
        clearTimeout(timer);
        reject(e);
      },
    );
  });
}
