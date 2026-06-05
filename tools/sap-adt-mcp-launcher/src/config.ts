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
  const state: ServeArgvState = {
    port: DEFAULT_MCP_PORT,
    workspace: DEFAULT_WORKSPACE,
    explicitWorkspace: false,
    importFrom: "auto",
    destination: undefined,
    json: false,
    showToken: false,
    foreground: true,
    verbose: false,
    logFile: undefined,
    logonTimeoutMs: DEFAULT_LOGON_TIMEOUT_MS,
    stdio: false,
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
    flag(
      (s) => {
        s.importFrom = "gui";
      },
      ["--gui", "--import-from=gui"],
    ),
    flagValue(
      (s) => {
        s.importFrom = "openadt";
      },
      ["--import-from=openadt"],
    ),
    flagValue(
      (s) => {
        s.importFrom = "adtls";
      },
      ["--import-from=adtls"],
    ),
    flagValue(
      (s) => {
        s.importFrom = "auto";
      },
      ["--import-from=auto"],
    ),
    flag(
      (s) => {
        s.importFrom = "none";
      },
      ["--no-gui", "--import-from=none"],
    ),
    consumeNext(
      (arg, argv, i, s) => {
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
    flag(
      (s) => {
        s.json = true;
      },
      ["--json"],
    ),
    flag(
      (s) => {
        s.showToken = true;
      },
      ["--show-token"],
    ),
    flag(
      (s) => {
        s.stdio = true;
      },
      ["--stdio"],
    ),
    flag(
      (s) => {
        s.verbose = true;
      },
      ["--verbose", "-v"],
    ),
    stringValue(
      (arg, value, s) => {
        s.logFile = value;
      },
      ["--log-file"],
    ),
    secondsValue(
      (arg, value, s) => {
        s.logonTimeoutMs = value * 1000;
      },
      ["--logon-timeout"],
    ),
    flag(
      (s) => {
        s.foreground = true;
      },
      ["--foreground"],
    ),
    numberValue(
      (arg, value, s) => {
        s.port = value;
      },
      ["--port"],
    ),
    stringValue(
      (arg, value, s) => {
        s.workspace = value;
        s.explicitWorkspace = true;
      },
      ["--workspace"],
    ),
    stringValue(
      (arg, value, s) => {
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
    if (arg === "--json") {
      json = true;
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
    if (arg === "--token" && i + 1 < argv.length) {
      token = argv[++i]!;
      continue;
    }
    if (arg.startsWith("--token=")) {
      token = arg.slice("--token=".length);
      continue;
    }
  }

  if (port !== undefined && !isValidPort(port)) {
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
    if (arg === "--port" && i + 1 < argv.length) {
      port = Number(argv[++i]!);
      continue;
    }
    if (arg.startsWith("--port=")) {
      port = Number(arg.slice("--port=".length));
      continue;
    }
  }

  if (port !== undefined && !isValidPort(port)) {
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
