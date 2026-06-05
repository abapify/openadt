import { homedir } from "node:os";
import { join } from "node:path";
import {
  DEFAULT_MCP_PORT,
  type DestinationImportMode,
  type McpServeConfig,
} from "./types.ts";
import { DEFAULT_LOGON_TIMEOUT_MS } from "./logon-handlers.ts";

export const DEFAULT_WORKSPACE = join(
  homedir(),
  ".openadt",
  "adt-ls-workspace",
);
export const PID_FILE = join(homedir(), ".openadt", "adt-ls-mcp.pid");

export function parseServeArgv(argv: string[]): McpServeConfig {
  let port = DEFAULT_MCP_PORT;
  let workspace = DEFAULT_WORKSPACE;
  let explicitWorkspace = false;
  let importFrom: DestinationImportMode = "auto";
  let destination: string | undefined;
  let json = false;
  let showToken = false;
  let foreground = true;
  let verbose = false;
  let logFile: string | undefined;
  let logonTimeoutMs = DEFAULT_LOGON_TIMEOUT_MS;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    if (arg === "--gui" || arg === "--import-from=gui") {
      importFrom = "gui";
      continue;
    }
    if (arg === "--import-from=openadt") {
      importFrom = "openadt";
      continue;
    }
    if (arg === "--import-from=adtls") {
      importFrom = "adtls";
      continue;
    }
    if (arg === "--import-from=auto") {
      importFrom = "auto";
      continue;
    }
    if (arg === "--no-gui" || arg === "--import-from=none") {
      importFrom = "none";
      continue;
    }
    if (arg === "--import-from" && i + 1 < argv.length) {
      const value = argv[++i]!.toLowerCase();
      if (
        value === "auto" ||
        value === "adtls" ||
        value === "gui" ||
        value === "openadt" ||
        value === "none"
      ) {
        importFrom = value;
        continue;
      }
      throw new Error(
        `Invalid --import-from: ${value} (use auto, adtls, gui, openadt, or none)`,
      );
    }
    if (arg === "--json") {
      json = true;
      continue;
    }
    if (arg === "--show-token") {
      showToken = true;
      continue;
    }
    if (arg === "--verbose" || arg === "-v") {
      verbose = true;
      continue;
    }
    if (arg === "--log-file" && i + 1 < argv.length) {
      logFile = argv[++i]!;
      continue;
    }
    if (arg.startsWith("--log-file=")) {
      logFile = arg.slice("--log-file=".length);
      continue;
    }
    if (arg === "--logon-timeout" && i + 1 < argv.length) {
      logonTimeoutMs = Number(argv[++i]!) * 1000;
      continue;
    }
    if (arg.startsWith("--logon-timeout=")) {
      logonTimeoutMs = Number(arg.slice("--logon-timeout=".length)) * 1000;
      continue;
    }
    if (arg === "--foreground") {
      foreground = true;
      continue;
    }
    if (arg === "--port" && i + 1 < argv.length) {
      port = Number(argv[++i]!);
      continue;
    }
    if (arg.startsWith("--port=")) {
      port = Number(arg.slice("--port=".length));
      continue;
    }
    if (arg === "--workspace" && i + 1 < argv.length) {
      workspace = argv[++i]!;
      explicitWorkspace = true;
      continue;
    }
    if (arg.startsWith("--workspace=")) {
      workspace = arg.slice("--workspace=".length);
      explicitWorkspace = true;
      continue;
    }
    if (arg === "--destination" && i + 1 < argv.length) {
      destination = argv[++i]!;
      continue;
    }
    if (arg.startsWith("--destination=")) {
      destination = arg.slice("--destination=".length);
      continue;
    }
  }

  if (port < 1 || port > 65535 || !Number.isFinite(port)) {
    throw new Error(`Invalid --port: ${port}`);
  }

  if (!Number.isFinite(logonTimeoutMs) || logonTimeoutMs < 5_000) {
    throw new Error(`Invalid --logon-timeout (seconds must be >= 5)`);
  }

  if (!verbose && process.env.MCP_DEBUG) {
    verbose = true;
  }

  return {
    port,
    workspace,
    explicitWorkspace,
    importFrom,
    destination,
    json,
    showToken,
    foreground,
    verbose,
    logFile,
    logonTimeoutMs,
  };
}

export function parseStatusArgv(argv: string[]): {
  port?: number;
  token?: string;
  json: boolean;
} {
  let port: number | undefined;
  let token: string | undefined;
  let json = false;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    const portArg = readStringFlag(argv, i, "port");
    if (portArg !== null) {
      if ("missing" in portArg) throwMissingFlag("port");
      port = Number(portArg.value);
      i = portArg.next;
      continue;
    }
    if (arg === "--json") {
      json = true;
      continue;
    }
    const tokenArg = readStringFlag(argv, i, "token");
    if (tokenArg !== null) {
      if ("missing" in tokenArg) throwMissingFlag("token");
      token = tokenArg.value;
      i = tokenArg.next;
      continue;
    }
  }

  if (
    port !== undefined &&
    (port < 1 || port > 65535 || !Number.isFinite(port))
  ) {
    throw new Error(`Invalid --port: ${port}`);
  }

  return { port, token, json };
}

export function parsePrintConfigArgv(argv: string[]): {
  port?: number;
  json: boolean;
} {
  let port: number | undefined;
  let json = false;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    if (arg === "--json") {
      json = true;
      continue;
    }
    const portArg = readStringFlag(argv, i, "port");
    if (portArg !== null) {
      if ("missing" in portArg) throwMissingFlag("port");
      port = Number(portArg.value);
      i = portArg.next;
      continue;
    }
  }

  if (
    port !== undefined &&
    (port < 1 || port > 65535 || !Number.isFinite(port))
  ) {
    throw new Error(`Invalid --port: ${port}`);
  }

  return { port, json };
}

export function parseListArgv(argv: string[]): { json: boolean } {
  return { json: argv.includes("--json") };
}

export interface ParsedSubcommand {
  readonly name: string;
  readonly argv: string[];
}

export function parseSubcommandArgv(
  argv: string[],
): ParsedSubcommand | undefined {
  const sub = argv[0];
  if (!sub || sub === "--help" || sub === "-h") {
    return undefined;
  }
  return { name: sub, argv: argv.slice(1) };
}

type FlagResult = { value: string; next: number } | { missing: true } | null;

/** Read {@code --name value} or {@code --name=value} from argv. */
function readStringFlag(argv: string[], i: number, name: string): FlagResult {
  const arg = argv[i];
  const prefix = `--${name}=`;
  if (arg === `--${name}`) {
    const next = argv[i + 1];
    if (next === undefined) return { missing: true };
    return { value: next, next: i + 1 };
  }
  if (arg !== undefined && arg.startsWith(prefix)) {
    return { value: arg.slice(prefix.length), next: i };
  }
  return null;
}

function throwMissingFlag(name: string): never {
  throw new Error(`Missing value for --${name}`);
}
