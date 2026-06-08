/**
 * Read ABAP object source by name — emulates VS Code's `adt-vscode.openAbapObject`
 * without VS Code, by reusing the same `adt-lsc` LSP methods the extension uses.
 *
 * The by-name chain (all over the existing LSP connection; verified against a4h by
 * the sibling arc-1-lsp project, see docs/plans/2026-06-07-mcp-read-abap-object.md):
 *
 *   adtLs/repository/quickSearch { destination, pattern, maxResults, types }
 *       → references[] { name, type, uri = ADT path }
 *   adtLs/repository/getLsUri    { destination, adtUri }      → { uri = repotree/AFF URI }
 *   adtLs/fileSystem/readFile    { uri }                      → { content }
 *
 * Param names are exact: search is `pattern` (not `query`); resolve key is `adtUri`
 * (not `uri`). readFile accepts ONLY the canonical repotree/AFF URI returned by
 * getLsUri — never hand-build it. readFile serves real source only for modern
 * clean-core/RAP types (CLAS/INTF/DDLS/DCLS/SRVB/DDLX/BDEF/SRVD/DRAS/CDS); classic
 * types (PROG/TABL/FUGR/…) return a placeholder ("not supported in ADT in VS Code")
 * — we surface that as-is (same as VS Code), never work around it.
 *
 * Robustness (per design): everything is async, and we NEVER surface an empty
 * result — an empty quickSearch / empty source masks a cold RIS index or a dead SAP
 * session as "not found". Instead we retry with backoff until a deadline and then
 * fail with a timeout error. The backend is pre-warmed after logon (see prewarm).
 *
 * Set OPENADT_MCP_NO_READ=1 to disable the read tools entirely.
 */
import { ParameterStructures, type MessageConnection } from "./rpc.ts";
import { sleep } from "./process.ts";
import {
  LSP_METHOD_FILESYSTEM_READ_FILE,
  LSP_METHOD_REPOSITORY_GET_LS_URI,
  LSP_METHOD_REPOSITORY_QUICK_SEARCH,
  type AdtObjectReference,
  type QuickSearchResult,
} from "./types.ts";

/** Whether the read tools are enabled (default on). */
export function readEnabled(): boolean {
  return !process.env.OPENADT_MCP_NO_READ;
}

/** adt-ls returns this placeholder (not source) for types it can't serve headless. */
export function isUnsupportedPlaceholder(content: string): boolean {
  return /not supported in ADT in VS Code/i.test(content);
}

/**
 * Minimal LSP request surface — decouples this module from `MessageConnection`
 * for testing and lets an HTTP-backed implementation reuse the same wrappers.
 */
export type LspRequester = <T>(method: string, params: object) => Promise<T>;

/** Adapt an `adt-lsc` `MessageConnection` to an `LspRequester` (named params). */
export function connectionRequester(
  connection: MessageConnection,
): LspRequester {
  return <T>(method: string, params: object): Promise<T> =>
    connection.sendRequest(
      method,
      ParameterStructures.byName,
      params,
    ) as Promise<T>;
}

// ---- LSP wrappers (1:1 with the adt-lsc methods) ---------------------------

export function quickSearch(
  req: LspRequester,
  params: {
    destination: string;
    pattern: string;
    maxResults?: number;
    types?: string[];
  },
): Promise<QuickSearchResult> {
  return req<QuickSearchResult>(LSP_METHOD_REPOSITORY_QUICK_SEARCH, {
    destination: params.destination,
    pattern: params.pattern,
    maxResults: params.maxResults ?? 50,
    types: params.types ?? [],
  });
}

export async function getLsUri(
  req: LspRequester,
  destination: string,
  adtUri: string,
): Promise<string> {
  const r = await req<{ uri?: string }>(LSP_METHOD_REPOSITORY_GET_LS_URI, {
    destination,
    adtUri,
  });
  if (!r.uri) {
    throw new Error(`getLsUri returned no uri for ${adtUri}`);
  }
  return r.uri;
}

export async function readFile(
  req: LspRequester,
  uri: string,
): Promise<string> {
  const r = await req<{ content?: string }>(LSP_METHOD_FILESYSTEM_READ_FILE, {
    uri,
  });
  return r.content ?? "";
}

// ---- "never return empty" retry-until-deadline -----------------------------

export class ReadTimeoutError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ReadTimeoutError";
  }
}

export type RetryOptions = { timeoutMs?: number; intervalMs?: number };

const DEFAULT_TIMEOUT_MS = 25_000; // under SAP MCP's 30s request timeout
const DEFAULT_INTERVAL_MS = 600;

/**
 * Run `fn` until it returns a non-empty value or the deadline passes. An empty
 * result (or a transient throw) is retried with a fixed interval; if still empty
 * at the deadline we throw `ReadTimeoutError` rather than surface the empty value.
 */
export async function retryUntilNonEmpty<T>(
  fn: () => Promise<T>,
  isEmpty: (value: T) => boolean,
  label: string,
  options: RetryOptions = {},
): Promise<T> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const intervalMs = options.intervalMs ?? DEFAULT_INTERVAL_MS;
  const deadline = Date.now() + timeoutMs;
  let lastError: unknown;
  for (;;) {
    try {
      const value = await fn();
      if (!isEmpty(value)) {
        return value;
      }
    } catch (err) {
      lastError = err;
    }
    if (Date.now() + intervalMs >= deadline) {
      const cause = lastError ? `; last error: ${formatError(lastError)}` : "";
      throw new ReadTimeoutError(
        `${label}: no result within ${timeoutMs}ms — object may be missing, ` +
          `or the SAP backend is still warming / the session expired${cause}`,
      );
    }
    await sleep(intervalMs);
  }
}

function formatError(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

/** Last non-empty path segment of an ADT uri, upper-cased (a best-effort name). */
function lastUriSegment(uri: string): string {
  const segs = uri.split("/").filter(Boolean);
  return (segs[segs.length - 1] ?? uri).toUpperCase();
}

// ---- reference selection ---------------------------------------------------

/**
 * Pick the reference matching `name` (case-insensitive), optionally filtered by
 * `type`. Returns `{ match }` on a single best hit, or `{ candidates }` when the
 * name is ambiguous / no exact match (so the caller can disambiguate rather than
 * guess). `refs` is assumed non-empty.
 */
export function pickReference(
  refs: AdtObjectReference[],
  name: string,
  type?: string,
): { match: AdtObjectReference } | { candidates: AdtObjectReference[] } {
  const pool = poolForType(refs, type);
  const exact = exactNameMatches(pool, name);
  if (exact.length === 1) {
    return { match: exact[0]! };
  }
  if (exact.length === 0 && pool.length === 1) {
    return { match: pool[0]! };
  }
  return { candidates: exact.length > 0 ? exact : pool };
}

/** Narrow `refs` to those whose `type` matches (uppercased). Fall back to `refs` when no type or none match. */
function poolForType(
  refs: AdtObjectReference[],
  type: string | undefined,
): AdtObjectReference[] {
  if (!type) return refs;
  const target = type.toUpperCase();
  const filtered = refs.filter((r) => r.type?.toUpperCase() === target);
  return filtered.length > 0 ? filtered : refs;
}

function exactNameMatches(
  refs: AdtObjectReference[],
  name: string,
): AdtObjectReference[] {
  const target = name.toUpperCase();
  return refs.filter((r) => r.name?.toUpperCase() === target);
}

// ---- backend abstraction (transport-agnostic) ------------------------------

export type ReadObjectInput = {
  destination: string;
  /** Object name to resolve via quickSearch. Provide this OR `uri`. */
  objectName?: string;
  /** ADT type code (e.g. CLAS/OC) to narrow the name search. Not the display label. */
  objectType?: string;
  /** ADT path from a search result (e.g. /sap/bc/adt/oo/classes/cl_x) — read it directly. */
  uri?: string;
};

export type ReadObjectResult =
  | {
      kind: "source";
      reference: AdtObjectReference;
      content: string;
      /** adt-ls served a placeholder (classic type, not editable in VS Code). */
      unsupported: boolean;
    }
  | { kind: "ambiguous"; candidates: AdtObjectReference[] };

export type SearchInput = {
  destination: string;
  pattern: string;
  types?: string[];
  maxResults?: number;
};

/** Read ABAP objects over some transport (LSP directly, or HTTP to the daemon). */
export interface ReadObjectBackend {
  readObject(input: ReadObjectInput): Promise<ReadObjectResult>;
  search(input: SearchInput): Promise<AdtObjectReference[]>;
}

/** `ReadObjectBackend` backed directly by an `adt-lsc` LSP connection. */
export class LspReadBackend implements ReadObjectBackend {
  constructor(
    private readonly req: LspRequester,
    private readonly retry: RetryOptions = {},
  ) {}

  private async resolve(
    destination: string,
    pattern: string,
    types?: string[],
    maxResults?: number,
  ): Promise<AdtObjectReference[]> {
    const result = await retryUntilNonEmpty(
      () => quickSearch(this.req, { destination, pattern, types, maxResults }),
      (r) => (r.references?.length ?? 0) === 0,
      `quickSearch '${pattern}'`,
      this.retry,
    );
    return result.references ?? [];
  }

  async readObject(input: ReadObjectInput): Promise<ReadObjectResult> {
    // Precise path: an ADT uri from a search result — no name resolution / no
    // type-vocabulary needed (search→read composes on the uri).
    let ref: AdtObjectReference;
    if (input.uri) {
      ref = {
        name: input.objectName ?? lastUriSegment(input.uri),
        uri: input.uri,
      };
    } else {
      if (!input.objectName) {
        throw new Error("readObject requires objectName or uri");
      }
      // objectType is an ADT type code (CLAS/OC) — adt-ls's quickSearch `types`
      // filter expects codes, not the display label it returns ("Class").
      const refs = await this.resolve(
        input.destination,
        input.objectName,
        input.objectType ? [input.objectType] : undefined,
      );
      const picked = pickReference(refs, input.objectName);
      if ("candidates" in picked) {
        return { kind: "ambiguous", candidates: picked.candidates };
      }
      ref = picked.match;
    }
    if (!ref.uri) {
      throw new Error(`Object ${ref.name} has no ADT uri to resolve`);
    }
    const lsUri = await getLsUri(this.req, input.destination, ref.uri);
    const content = await retryUntilNonEmpty(
      () => readFile(this.req, lsUri),
      (c) => c.length === 0,
      `readFile ${ref.name}`,
      this.retry,
    );
    return {
      kind: "source",
      reference: ref,
      content,
      unsupported: isUnsupportedPlaceholder(content),
    };
  }

  async search(input: SearchInput): Promise<AdtObjectReference[]> {
    return this.resolve(
      input.destination,
      input.pattern,
      input.types,
      input.maxResults,
    );
  }
}

/**
 * `ReadObjectBackend` that forwards to a daemon's read endpoint over HTTP.
 * Used by the stdio bridge in shared mode, where the LSP connection lives in a
 * separate daemon process (see docs/plans/2026-06-07-mcp-read-abap-object.md).
 * The daemon owns the retry/timeout logic; this client just waits for it.
 */
export class HttpReadBackend implements ReadObjectBackend {
  constructor(
    private readonly url: string,
    private readonly token: string,
    private readonly timeoutMs = 60_000,
  ) {}

  private async post<T>(path: string, body: object): Promise<T> {
    const res = await fetch(`${this.url}${path}`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${this.token}`,
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(this.timeoutMs),
    });
    const data = (await res.json().catch(() => undefined)) as
      | (T & { error?: string })
      | undefined;
    if (!res.ok) {
      const msg = data?.error ?? `read endpoint HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data as T;
  }

  readObject(input: ReadObjectInput): Promise<ReadObjectResult> {
    return this.post<ReadObjectResult>("/read-object", input);
  }

  async search(input: SearchInput): Promise<AdtObjectReference[]> {
    const r = await this.post<{ references?: AdtObjectReference[] }>(
      "/search",
      input,
    );
    return r.references ?? [];
  }
}

/**
 * Best-effort warm-up of the RIS index for a destination, so the first real
 * `quickSearch` after logon doesn't return an empty (cold) result. Errors and
 * empty results are ignored — this only primes the backend.
 */
export async function prewarm(
  req: LspRequester,
  destination: string,
): Promise<void> {
  try {
    await quickSearch(req, { destination, pattern: "*", maxResults: 1 });
  } catch {
    /* best effort */
  }
}

// ---- MCP tool surface ------------------------------------------------------

export const READ_OBJECT_TOOL = "adt_read_object";
export const SEARCH_OBJECTS_TOOL = "adt_search_objects";

/** MCP tool definitions to merge into the backend's `tools/list`. */
export function readToolDefs(): object[] {
  return [readObjectToolDef(), searchObjectsToolDef()];
}

function readObjectToolDef(): object {
  return {
    name: READ_OBJECT_TOOL,
    description:
      "Read an ABAP object's source by name (emulates VS Code 'Open ABAP Object'). " +
      "Returns the source for modern/RAP types (classes, interfaces, CDS/DDLS, " +
      "service bindings, behavior/service definitions); classic types (programs, " +
      "tables, function groups) return an 'open in Eclipse' note from ADT.",
    inputSchema: {
      type: "object",
      properties: {
        destination: {
          type: "string",
          description: "Destination id, e.g. ABC_000_USER_EN.",
        },
        objectName: {
          type: "string",
          description:
            "Object name, e.g. CL_ABAP_TYPEDESCR (provide this or uri).",
        },
        objectType: {
          type: "string",
          description:
            "Optional ADT type code to narrow the name search, e.g. CLAS/OC, INTF/OI, " +
            "DDLS/DF. NOTE: this is the code adt-ls's search filters on — not the " +
            "display label ('Class') it returns.",
        },
        uri: {
          type: "string",
          description:
            "ADT object path from a search result (e.g. /sap/bc/adt/oo/classes/cl_x). " +
            "When given, the object is read directly — no name lookup or type needed.",
        },
        format: {
          type: "string",
          enum: ["source", "json"],
          description:
            "Text output: 'source' (default, raw ABAP) or 'json' (metadata + content). structuredContent is always JSON.",
        },
      },
      required: ["destination"],
    },
  };
}

function searchObjectsToolDef(): object {
  return {
    name: SEARCH_OBJECTS_TOOL,
    description:
      "Search ABAP repository objects by name pattern (RIS quick search). " +
      "Returns matching objects with their name, type and ADT path.",
    inputSchema: {
      type: "object",
      properties: {
        destination: { type: "string", description: "Destination id." },
        pattern: {
          type: "string",
          description:
            "Name pattern, e.g. CL_ABAP_TYPE* (RIS facet syntax supported).",
        },
        types: {
          type: "array",
          items: { type: "string" },
          description:
            "Optional ADT type codes to filter by, e.g. CLAS/OC, INTF/OI, DDLS/DF. " +
            "(The result's `type` is a display label like 'Class' — not a filter value.)",
        },
        maxResults: {
          type: "integer",
          description: "Maximum number of results (default 50).",
        },
        format: {
          type: "string",
          enum: ["json", "markdown", "compact"],
          description:
            "Text output format (default 'json'). structuredContent is always JSON.",
        },
      },
      required: ["destination", "pattern"],
    },
  };
}

/** Whether `name` is one of our injected read tools. */
export function isReadTool(name: string): boolean {
  return name === READ_OBJECT_TOOL || name === SEARCH_OBJECTS_TOOL;
}

/** MCP CallToolResult shape (subset). */
export type CallToolResult = {
  content: { type: "text"; text: string }[];
  /** Machine-readable payload (like SAP tools' structuredContent). */
  structuredContent?: unknown;
  isError?: boolean;
};

function textResult(text: string, isError = false): CallToolResult {
  return { content: [{ type: "text", text }], isError };
}

function jsonResult(text: string, structuredContent: unknown): CallToolResult {
  return { content: [{ type: "text", text }], structuredContent };
}

export type ReferencesFormat = "json" | "markdown" | "compact";

function parseReferencesFormat(value: unknown): ReferencesFormat {
  return value === "markdown" || value === "compact" ? value : "json";
}

/** Render a reference list as the chosen text format (structuredContent stays JSON). */
function renderReferences(
  refs: AdtObjectReference[],
  format: ReferencesFormat,
): string {
  if (format === "markdown") {
    const head = "| Name | Type | Description |\n|------|------|-------------|";
    const rows = refs.map(
      (r) => `| ${r.name} | ${r.type ?? "?"} | ${r.description ?? ""} |`,
    );
    return [head, ...rows].join("\n");
  }
  if (format === "compact") {
    return refs
      .map(
        (r) =>
          `${r.name}${r.description ? ` — ${r.description}` : ""} (${r.type ?? "?"})`,
      )
      .join("\n");
  }
  return JSON.stringify({ references: refs }, null, 2);
}

/** Run a read tool call against `backend` and shape the MCP CallToolResult. */
export async function handleReadToolCall(
  backend: ReadObjectBackend,
  name: string,
  args: Record<string, unknown>,
): Promise<CallToolResult> {
  try {
    if (name === READ_OBJECT_TOOL) {
      return await handleReadObjectTool(backend, args);
    }
    if (name === SEARCH_OBJECTS_TOOL) {
      return await handleSearchObjectsTool(backend, args);
    }
    return textResult(`Unknown read tool: ${name}`, true);
  } catch (err) {
    return textResult(formatError(err), true);
  }
}

async function handleReadObjectTool(
  backend: ReadObjectBackend,
  args: Record<string, unknown>,
): Promise<CallToolResult> {
  const input = parseReadObjectArgs(args);
  if (!input) {
    return textResult("destination and (objectName or uri) are required", true);
  }
  const result = await backend.readObject(input);
  if (result.kind === "ambiguous") {
    return jsonResult(
      `Multiple objects match '${input.objectName}'. Pass objectType (ADT code, e.g. CLAS/OC) ` +
        `or a uri to disambiguate. Candidates:\n` +
        JSON.stringify(result.candidates, null, 2),
      { ambiguous: true, candidates: result.candidates },
    );
  }
  return renderReadObject(result, args.format);
}

function parseReadObjectArgs(
  args: Record<string, unknown>,
): ReadObjectInput | undefined {
  const destination = stringField(args, "destination");
  const objectName = stringField(args, "objectName");
  const uri = stringField(args, "uri");
  if (!destination) return undefined;
  if (!objectName && !uri) return undefined;
  return {
    destination,
    objectName,
    objectType: stringField(args, "objectType"),
    uri,
  };
}

function stringField(
  args: Record<string, unknown>,
  key: string,
): string | undefined {
  const v = args[key];
  return typeof v === "string" ? v : undefined;
}

function renderReadObject(
  result: Extract<ReadObjectResult, { kind: "source" }>,
  format: unknown,
): CallToolResult {
  const r = result.reference;
  const meta = {
    name: r.name,
    type: r.type,
    uri: r.uri,
    unsupported: result.unsupported,
  };
  if (format === "json") {
    return jsonResult(
      JSON.stringify({ ...meta, content: result.content }, null, 2),
      meta,
    );
  }
  const header = `* ${r.name} (${r.type ?? "?"}) — ${r.uri ?? ""}`.trimEnd();
  return {
    content: [{ type: "text", text: `${header}\n\n${result.content}` }],
    structuredContent: meta,
  };
}

async function handleSearchObjectsTool(
  backend: ReadObjectBackend,
  args: Record<string, unknown>,
): Promise<CallToolResult> {
  const destination = String(args.destination ?? "");
  const pattern = String(args.pattern ?? "");
  if (!destination || !pattern) {
    return textResult("destination and pattern are required", true);
  }
  const types = Array.isArray(args.types)
    ? args.types.filter((t): t is string => typeof t === "string")
    : undefined;
  const maxResults =
    typeof args.maxResults === "number" ? args.maxResults : undefined;
  const references = await backend.search({
    destination,
    pattern,
    types,
    maxResults,
  });
  return jsonResult(
    renderReferences(references, parseReferencesFormat(args.format)),
    { references },
  );
}
