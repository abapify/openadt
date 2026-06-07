import { homedir } from "node:os";
import { join } from "node:path";
import {
  DEFAULT_IMPORT_FROM,
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
  const state: ServeArgvState = {
    port: DEFAULT_MCP_PORT,
    explicitPort: false,
    workspace: DEFAULT_WORKSPACE,
    explicitWorkspace: false,
    importFrom: DEFAULT_IMPORT_FROM,
    destination: undefined,
    json: false,
    showToken: false,
    foreground: true,
    verbose: false,
    logFile: undefined,
    logonTimeoutMs: DEFAULT_LOGON_TIMEOUT_MS,
    stdio: false,
    standalone: false,
    restart: false,
  };

  const handlers = buildServeArgvHandlers();
  for (let i = 0; i < argv.length; ) {
    const arg = argv[i]!;
    const handler = handlers.find((h) => h.matches(arg));
    if (!handler) {
      throw new Error(`Unknown argument: ${arg}`);
    }
    i = handler.apply(arg, argv, i, state);
  }

  finalizeServeArgv(state);

  return state;
}

type ServeArgvState = {
  port: number;
  explicitPort: boolean;
  workspace: string;
  explicitWorkspace: boolean;
  importFrom: DestinationImportMode;
  destination: string | undefined;
  json: boolean;
  showToken: boolean;
  foreground: boolean;
  verbose: boolean;
  logFile: string | undefined;
  logonTimeoutMs: number;
  stdio: boolean;
  /** When true, --stdio is monolithic (own adt-lsc, kill on exit). */
  standalone: boolean;
  /** When true (shared stdio), stop an existing daemon first so a fresh one spawns. */
  restart: boolean;
};

type ServeArgvHandler = {
  matches: (arg: string) => boolean;
  apply: (
    arg: string,
    argv: string[],
    i: number,
    state: ServeArgvState,
  ) => number;
};

const IMPORT_FROM_MODES: readonly DestinationImportMode[] = [
  "auto",
  "adtls",
  "gui",
  "openadt",
  "none",
];

function buildServeArgvHandlers(): ServeArgvHandler[] {
  return [
    ...importFromArgvHandlers(),
    ...booleanFlagArgvHandlers(),
    ...valuedArgvHandlers(),
  ];
}

function importFromArgvHandlers(): ServeArgvHandler[] {
  const setMode = (mode: DestinationImportMode) => (s: ServeArgvState) => {
    s.importFrom = mode;
  };
  return [
    flag(setMode("gui"), ["--gui", "--import-from=gui"]),
    flagValue(setMode("openadt"), ["--import-from=openadt"]),
    flagValue(setMode("adtls"), ["--import-from=adtls"]),
    flagValue(setMode("auto"), ["--import-from=auto"]),
    flag(setMode("none"), ["--no-gui", "--import-from=none"]),
    consumeNext(
      (_arg, argv, i, s) => {
        const value = argv[++i]!.toLowerCase();
        if (!IMPORT_FROM_MODES.includes(value as DestinationImportMode)) {
          throw new Error(
            `Invalid --import-from: ${value} (use auto, adtls, gui, openadt, or none)`,
          );
        }
        s.importFrom = value as DestinationImportMode;
        return i;
      },
      ["--import-from"],
    ),
  ];
}

function booleanFlagArgvHandlers(): ServeArgvHandler[] {
  const boolFlag = (
    apply: (s: ServeArgvState) => void,
    forms: readonly string[],
  ) => flag(apply, forms);
  return [
    boolFlag(
      (s) => {
        s.json = true;
      },
      ["--json"],
    ),
    boolFlag(
      (s) => {
        s.showToken = true;
      },
      ["--show-token"],
    ),
    boolFlag(
      (s) => {
        s.stdio = true;
      },
      ["--stdio"],
    ),
    boolFlag(
      (s) => {
        s.standalone = true;
      },
      ["--standalone"],
    ),
    boolFlag(
      (s) => {
        s.restart = true;
      },
      ["--restart"],
    ),
    boolFlag(
      (s) => {
        s.verbose = true;
      },
      ["--verbose", "-v"],
    ),
    boolFlag(
      (s) => {
        s.foreground = true;
      },
      ["--foreground"],
    ),
  ];
}

function valuedArgvHandlers(): ServeArgvHandler[] {
  return [
    stringValue(
      (_arg, value, s) => {
        s.logFile = value;
      },
      ["--log-file"],
    ),
    secondsValue(
      (_arg, value, s) => {
        s.logonTimeoutMs = value * 1000;
      },
      ["--logon-timeout"],
    ),
    numberValue(
      (_arg, value, s) => {
        s.port = value;
        s.explicitPort = true;
      },
      ["--port"],
    ),
    stringValue(
      (_arg, value, s) => {
        s.workspace = value;
        s.explicitWorkspace = true;
      },
      ["--workspace"],
    ),
    stringValue(
      (_arg, value, s) => {
        s.destination = value;
      },
      ["--destination"],
    ),
  ];
}

function finalizeServeArgv(state: ServeArgvState): void {
  if (!isValidPort(state.port)) {
    throw new Error(`Invalid --port: ${state.port}`);
  }
  if (!Number.isFinite(state.logonTimeoutMs) || state.logonTimeoutMs < 5_000) {
    throw new Error(`Invalid --logon-timeout (seconds must be >= 5)`);
  }
  if (!state.verbose && process.env.MCP_DEBUG) {
    state.verbose = true;
  }
}

function flag(
  apply: (state: ServeArgvState) => void,
  forms: readonly string[],
): ServeArgvHandler {
  return {
    matches: (arg) => forms.includes(arg),
    apply: (arg, _argv, i, state) => {
      apply(state);
      return i + 1;
    },
  };
}

function flagValue(
  apply: (state: ServeArgvState) => void,
  forms: readonly string[],
): ServeArgvHandler {
  return {
    matches: (arg) => forms.some((form) => arg.startsWith(`${form}=`)),
    apply: (arg, _argv, i, state) => {
      apply(state);
      return i + 1;
    },
  };
}

function consumeNext(
  apply: (
    arg: string,
    argv: string[],
    i: number,
    state: ServeArgvState,
  ) => number,
  forms: readonly string[],
): ServeArgvHandler {
  return {
    matches: (arg) => forms.includes(arg),
    apply: (arg, argv, i, state) => apply(arg, argv, i, state) + 1,
  };
}

function stringValue(
  apply: (arg: string, value: string, state: ServeArgvState) => void,
  forms: readonly string[],
): ServeArgvHandler {
  const eqForm = `${forms[0]}=`;
  return {
    matches: (arg) => arg === forms[0] || arg.startsWith(eqForm),
    apply: (arg, argv, i, state) => {
      const value = arg.startsWith(eqForm)
        ? arg.slice(eqForm.length)
        : argv[++i]!;
      apply(arg, value, state);
      return i + 1;
    },
  };
}

function numberValue(
  apply: (arg: string, value: number, state: ServeArgvState) => void,
  forms: readonly string[],
): ServeArgvHandler {
  const eqForm = `${forms[0]}=`;
  return {
    matches: (arg) => arg === forms[0] || arg.startsWith(eqForm),
    apply: (arg, argv, i, state) => {
      const raw = arg.startsWith(eqForm)
        ? arg.slice(eqForm.length)
        : argv[++i]!;
      apply(arg, Number(raw), state);
      return i + 1;
    },
  };
}

function secondsValue(
  apply: (arg: string, value: number, state: ServeArgvState) => void,
  forms: readonly string[],
): ServeArgvHandler {
  return numberValue(apply, forms);
}

function isValidPort(value: number): boolean {
  return Number.isFinite(value) && value >= 1 && value <= 65535;
}

/** Read `--port` / `--port=N` from argv, or undefined if absent. */
function readPortFlag(
  argv: string[],
  i: number,
): { value: number; next: number } | undefined {
  const arg = argv[i]!;
  if (arg === "--port" && i + 1 < argv.length) {
    return { value: Number(argv[i + 1]), next: i + 2 };
  }
  if (arg.startsWith("--port=")) {
    return { value: Number(arg.slice("--port=".length)), next: i + 1 };
  }
  return undefined;
}

function isJsonFlag(arg: string): boolean {
  return arg === "--json";
}

export function parseStatusArgv(argv: string[]): {
  port?: number;
  token?: string;
  json: boolean;
} {
  let token: string | undefined;
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    if (arg === "--token" && i + 1 < argv.length) {
      token = argv[++i];
      continue;
    }
    if (arg.startsWith("--token=")) {
      token = arg.slice("--token=".length);
      continue;
    }
  }
  const { port, json } = parsePortAndJson(argv);
  return { port, token, json };
}

export function parsePrintConfigArgv(argv: string[]): {
  port?: number;
  json: boolean;
} {
  return parsePortAndJson(argv);
}

export function parseListArgv(argv: string[]): { json: boolean } {
  return { json: argv.includes("--json") };
}

export function parseStopArgv(argv: string[]): {
  port?: number;
  json: boolean;
} {
  return parsePortAndJson(argv);
}

export function parseBridgeArgv(argv: string[]): {
  port?: number;
  stdio: boolean;
  json: boolean;
} {
  let stdio = false;
  for (const arg of argv) {
    if (arg === "--stdio") {
      stdio = true;
    }
  }
  const { port, json } = parsePortAndJson(argv);
  return { port, stdio, json };
}

function parsePortAndJson(argv: string[]): { port?: number; json: boolean } {
  let port: number | undefined;
  let json = false;
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    if (isJsonFlag(arg)) {
      json = true;
      continue;
    }
    const portFlag = readPortFlag(argv, i);
    if (portFlag) {
      port = portFlag.value;
      i = portFlag.next - 1;
    }
  }
  if (port !== undefined && !isValidPort(port)) {
    throw new Error(`Invalid --port: ${port}`);
  }
  return { port, json };
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
