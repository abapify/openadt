/** SAP ADT language server custom LSP methods (adt-ls-client-protocol, not on npm). */
export const LSP_METHOD_DESTINATIONS_INIT =
  "adtLs/destinations/initializeService";
export const LSP_METHOD_DESTINATIONS_ENSURE_LOGON =
  "adtLs/destinations/ensureLoggedOn";
export const LSP_METHOD_DESTINATIONS_CREATE_PROJECT =
  "adtLs/destinations/createProject";
export const LSP_METHOD_DESTINATIONS_LIST = "adtLs/destinations/list";
export const LSP_METHOD_DESTINATIONS_GET_STORE_PATH =
  "adtLs/destinations/getStorePath";
export const LSP_METHOD_DESTINATIONS_GET_LOGON_INFO =
  "adtLs/destinations/getLogonInfo";
export const LSP_METHOD_DESTINATIONS_REQUEST_BROWSER_LOGON =
  "adtLs/destinations/requestBrowserBasedLogon";
export const LSP_METHOD_DESTINATIONS_REQUEST_LOGON_INPUT =
  "adtLs/destinations/requestLogonInput";
export const LSP_METHOD_DESTINATIONS_STOP_LOGON =
  "adtLs/destinations/stopLogonAttempt";
export const LSP_METHOD_DESTINATIONS_LOGON_STATE_CHANGED =
  "adtLs/destinations/logonStateChanged";
export const LSP_METHOD_MCP_START = "adtLs/mcp/startMCPServer";
export const LSP_METHOD_MCP_STOP = "adtLs/mcp/stopMCPServer";
export const LSP_METHOD_MCP_SET_DESTINATION = "adtLs/mcp/setDestination";

export const DEFAULT_MCP_PORT = 2236;
export const MARKETPLACE_URL =
  "https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode";

export type AdtLsInstall = {
  extensionRoot: string;
  adtLscPath: string;
  adtLsRoot: string;
  version: string;
};

export type DestinationsInitParams = {
  destinationsStorePath: string;
  workspaceFolderUris: string[];
  fileUris: string[];
};

export type McpStartParams = {
  port: number;
  token: string;
};

export type McpStartResult = {
  port: number;
  token: string;
  version?: string;
};

export type DestinationImportMode =
  | "auto"
  | "adtls"
  | "gui"
  | "openadt"
  | "none";

/** Default `serve --import-from`: ADT LS store (Eclipse/ADT logon destinations). */
export const DEFAULT_IMPORT_FROM: DestinationImportMode = "adtls";

export type McpServeConfig = {
  port: number;
  /** When true, --port (or OPENADT_MCP_PORT) was set explicitly on the CLI. */
  explicitPort: boolean;
  workspace: string;
  /** When true, --workspace was set explicitly on the CLI. */
  explicitWorkspace: boolean;
  importFrom: DestinationImportMode;
  destination?: string;
  json: boolean;
  showToken: boolean;
  foreground: boolean;
  verbose: boolean;
  logFile?: string;
  logonTimeoutMs: number;
  /** Stdio MCP transport: proxy stdin/stdout to local HTTP MCP (no token in agent config). */
  stdio: boolean;
  /** Monolithic mode: own adt-lsc, kill on exit. Default false (shared). */
  standalone: boolean;
};

export type McpRuntimeState = {
  url: string;
  port: number;
  token: string;
  version?: string;
  extensionVersion: string;
  adtLscPath: string;
  workspace: string;
};
