import {
  attachMcpStdoutEncoder,
  McpStdioDecoder,
  McpStdioEncoder,
  writeMcpStdioMessage,
} from "./mcp-framing.ts";
import { mcpUrl } from "./mcp.ts";
import {
  augmentInstructions,
  getGuidancePrompt,
  guidanceEnabled,
  guidancePromptDefs,
  isGuidancePrompt,
} from "./guidance.ts";
import {
  handleReadToolCall,
  isReadTool,
  readEnabled,
  readToolDefs,
  type ReadObjectBackend,
} from "./read-object.ts";

/** Parse MCP HTTP response body (JSON or SSE `data:` lines). */
export function parseMcpHttpResponseBody({
  contentType,
  body,
}: {
  contentType: string;
  body: string;
}): string[] {
  const trimmed = body.trim();
  if (!trimmed) {
    return [];
  }
  if (contentType.includes("text/event-stream")) {
    return parseSseMessages(trimmed);
  }
  return [trimmed];
}

/** Extract `data:` payloads from a Server-Sent Events body. */
function parseSseMessages(body: string): string[] {
  const messages: string[] = [];
  for (const line of body.split(/\r?\n/)) {
    if (!line.startsWith("data:")) {
      continue;
    }
    const payload = line.slice(5).trimStart();
    if (payload && payload !== "[DONE]") {
      messages.push(payload);
    }
  }
  return messages;
}

/** One MCP HTTP endpoint, including bearer token and optional session id. */
export class McpHttpEndpoint {
  static forConfig(
    port: number,
    token: string,
    sessionId?: string,
  ): McpHttpEndpoint {
    return new McpHttpEndpoint(mcpUrl(port), token, sessionId);
  }
  constructor(
    readonly url: string,
    readonly token: string,
    readonly sessionId: string | undefined,
  ) {}
  withSessionId(sessionId: string | undefined): McpHttpEndpoint {
    return new McpHttpEndpoint(this.url, this.token, sessionId);
  }
}

/** One MCP JSON-RPC body posted to or received from the HTTP endpoint. */
export class McpStdioMessage {
  constructor(readonly body: string) {}
}

/** Parsed JSON-RPC request (must have an `id`; notifications are filtered out). */
export class JsonRpcRequest {
  private constructor(readonly id: string | number | null) {}
  static parse(body: string): JsonRpcRequest | undefined {
    try {
      const parsed = JSON.parse(body) as { id?: unknown };
      if (parsed.id === undefined) return undefined;
      return new JsonRpcRequest(parsed.id as string | number | null);
    } catch {
      return undefined;
    }
  }
}

/** JSON-RPC error payload. */
export class JsonRpcError {
  constructor(
    readonly code: number,
    readonly message: string,
  ) {}
}

export function jsonRpcErrorResponse(
  request: JsonRpcRequest,
  error: JsonRpcError,
): object {
  return {
    jsonrpc: "2.0",
    id: request.id,
    error: { code: error.code, message: error.message },
  };
}

/** Minimal parse of a JSON-RPC request/notification body. */
type ParsedRpc = {
  id: string | number | null | undefined;
  method: string;
  params: Record<string, unknown>;
};

function parseRpc(body: string): ParsedRpc | undefined {
  try {
    const p = JSON.parse(body) as {
      id?: unknown;
      method?: unknown;
      params?: unknown;
    };
    if (typeof p.method !== "string") {
      return undefined;
    }
    const params =
      p.params && typeof p.params === "object" && !Array.isArray(p.params)
        ? (p.params as Record<string, unknown>)
        : {};
    return { id: p.id as ParsedRpc["id"], method: p.method, params };
  } catch {
    return undefined;
  }
}

/** Extract `name` + string-valued `arguments` from a prompts/get request. */
function promptGetParams(params: Record<string, unknown>): {
  name: string;
  args: Record<string, string>;
} {
  const name = typeof params.name === "string" ? params.name : "";
  const rawArgs =
    params.arguments && typeof params.arguments === "object"
      ? (params.arguments as Record<string, unknown>)
      : {};
  const args: Record<string, string> = {};
  for (const [k, v] of Object.entries(rawArgs)) {
    if (typeof v === "string") {
      args[k] = v;
    }
  }
  return { name, args };
}

/**
 * Inject launcher guidance into a backend response: append the workflow
 * cheat-sheet to `initialize.instructions`, and merge our prompts into
 * `prompts/list`. Returns the (possibly rewritten) message body. Untouched
 * for any other method, or when the response id does not match the request.
 */
function injectGuidance(
  msg: string,
  reqId: ParsedRpc["id"],
  method: string | undefined,
): string {
  if (!method || (method !== "initialize" && method !== "prompts/list")) {
    return msg;
  }
  let parsed: { id?: unknown; result?: Record<string, unknown> };
  try {
    parsed = JSON.parse(msg);
  } catch {
    return msg;
  }
  if (parsed.id !== reqId || !parsed.result) {
    return msg;
  }
  if (method === "initialize") {
    parsed.result.instructions = augmentInstructions(
      parsed.result.instructions as string | undefined,
    );
  } else {
    const existing = Array.isArray(parsed.result.prompts)
      ? parsed.result.prompts
      : [];
    parsed.result.prompts = [...existing, ...guidancePromptDefs()];
  }
  return JSON.stringify(parsed);
}

/** Extract `name` + `arguments` object from a tools/call request. */
function toolCallParams(params: Record<string, unknown>): {
  name: string;
  args: Record<string, unknown>;
} {
  const name = typeof params.name === "string" ? params.name : "";
  const args =
    params.arguments && typeof params.arguments === "object"
      ? (params.arguments as Record<string, unknown>)
      : {};
  return { name, args };
}

/**
 * Merge our read tools into a backend `tools/list` response. No-op for any other
 * method, when read is disabled, when no read backend is wired, or when the
 * response id does not match the request.
 */
function injectReadTools(
  msg: string,
  reqId: ParsedRpc["id"],
  method: string | undefined,
  hasBackend: boolean,
): string {
  if (!hasBackend || !readEnabled() || method !== "tools/list") {
    return msg;
  }
  let parsed: { id?: unknown; result?: Record<string, unknown> };
  try {
    parsed = JSON.parse(msg);
  } catch {
    return msg;
  }
  if (parsed.id !== reqId || !parsed.result) {
    return msg;
  }
  const existing = Array.isArray(parsed.result.tools)
    ? parsed.result.tools
    : [];
  parsed.result.tools = [...existing, ...readToolDefs()];
  return JSON.stringify(parsed);
}

/** Result of one HTTP POST to the MCP endpoint. */
export class McpHttpResponse {
  constructor(
    readonly messages: string[],
    readonly sessionId: string | undefined,
    readonly status: number,
  ) {}
}

type McpPostOptions = { timeoutMs?: number };

export async function postMcpHttpMessage(
  endpoint: McpHttpEndpoint,
  message: McpStdioMessage,
  options: McpPostOptions = {},
): Promise<McpHttpResponse> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "User-Agent": "openadt-mcp-client",
    Authorization: `Bearer ${endpoint.token}`,
  };
  if (endpoint.sessionId) {
    headers["Mcp-Session-Id"] = endpoint.sessionId;
  }

  const timeoutMs = options.timeoutMs ?? 60_000;
  const res = await fetch(endpoint.url, {
    method: "POST",
    headers,
    body: message.body,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const nextSessionId = res.headers.get("Mcp-Session-Id") ?? endpoint.sessionId;
  const text = await res.text();
  const contentType = res.headers.get("content-type") ?? "";
  const messages = parseMcpHttpResponseBody({ contentType, body: text });
  return new McpHttpResponse(messages, nextSessionId ?? undefined, res.status);
}

type McpWaitOptions = { timeoutMs?: number; intervalMs?: number };

/** Poll the MCP HTTP endpoint until it accepts a request. */
export async function waitForMcpHttp(
  endpoint: McpHttpEndpoint,
  options: McpWaitOptions = {},
): Promise<boolean> {
  const timeoutMs = options.timeoutMs ?? 30_000;
  const intervalMs = options.intervalMs ?? 250;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await probeMcpHttp(endpoint)) {
      return true;
    }
    await sleep(intervalMs);
  }
  return false;
}

async function probeMcpHttp(endpoint: McpHttpEndpoint): Promise<boolean> {
  try {
    const res = await fetch(endpoint.url, {
      method: "OPTIONS",
      headers: {
        "User-Agent": "openadt-mcp-client",
        Authorization: `Bearer ${endpoint.token}`,
      },
      signal: AbortSignal.timeout(10_000),
    });
    await res.arrayBuffer().catch(() => undefined);
    return true;
  } catch {
    return false;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export type StdioMcpBridge = {
  /** Begin reading stdin immediately; queue until run(). */
  start(): void;
  /**
   * Wire a read-object backend (LSP-backed). When set, the bridge advertises the
   * read tools in tools/list and answers their tools/call locally instead of
   * forwarding to SAP. Call before run().
   */
  setReadBackend(backend: ReadObjectBackend | undefined): void;
  /** Wait for HTTP MCP, flush queue, forward until stdin closes and forwards drain. */
  run(
    endpoint: McpHttpEndpoint,
    options?: { waitTimeoutMs?: number; pollIntervalMs?: number },
  ): Promise<void>;
  /** Reply with JSON-RPC errors to all queued requests that have an id. */
  failPending(code: number, message: string): void;
  /** Wait until queued stdio writes finish (call before process exit). */
  flush(): Promise<void>;
};

/** Transparent stdio MCP bridge to local SAP ADT HTTP MCP. */
export function createStdioMcpBridge(): StdioMcpBridge {
  const queue = new PendingBodyQueue(256);
  const chain = new ForwardChain();
  const lifecycle = new BridgeLifecycle();
  let backend: McpHttpEndpoint | undefined;
  let readBackend: ReadObjectBackend | undefined;

  const decoder = new McpStdioDecoder();
  const encoder = new McpStdioEncoder();
  decoder.on("transport", (mode) => {
    encoder.setTransport(mode);
  });
  attachMcpStdoutEncoder(encoder);

  const replyError = async (
    message: McpStdioMessage,
    error: JsonRpcError,
  ): Promise<void> => {
    const request = JsonRpcRequest.parse(message.body);
    if (request) {
      const err = jsonRpcErrorResponse(request, error);
      await writeMcpStdioMessage(encoder, err);
    }
  };

  const handleForwardError = async (
    message: McpStdioMessage,
    err: unknown,
  ): Promise<void> => {
    const errorMessage = err instanceof Error ? err.message : String(err);
    console.error(`[openadt-mcp] stdio proxy: ${errorMessage}`);
    await replyError(message, new JsonRpcError(-32000, errorMessage));
  };

  const forwardHttpOne = (message: McpStdioMessage): void => {
    if (!backend) {
      return;
    }
    const baseEndpoint = backend;
    const request = parseRpc(message.body);

    // Answer prompts/get for our injected prompts locally — the backend has
    // none, so forwarding would only produce an error.
    if (
      guidanceEnabled() &&
      request?.method === "prompts/get" &&
      request.id !== undefined
    ) {
      const { name, args } = promptGetParams(request.params);
      if (isGuidancePrompt(name)) {
        const result = getGuidancePrompt(name, args);
        chain.append(() =>
          writeMcpStdioMessage(encoder, {
            jsonrpc: "2.0",
            id: request.id,
            result,
          }),
        );
        return;
      }
    }

    // Answer tools/call for our read tools locally via the LSP-backed backend —
    // SAP's MCP cannot read object source, so these never reach the backend.
    if (
      readBackend &&
      readEnabled() &&
      request?.method === "tools/call" &&
      request.id !== undefined
    ) {
      const { name, args } = toolCallParams(request.params);
      if (isReadTool(name)) {
        const activeBackend = readBackend;
        chain.append(async () => {
          const result = await handleReadToolCall(activeBackend, name, args);
          await writeMcpStdioMessage(encoder, {
            jsonrpc: "2.0",
            id: request.id,
            result,
          });
        });
        return;
      }
    }

    // Methods whose backend response we rewrite to inject guidance.
    const injectMethod = guidanceEnabled() ? request?.method : undefined;

    chain.append(async () => {
      const endpoint = baseEndpoint.withSessionId(chain.sessionId);
      try {
        const result = await postMcpHttpMessage(endpoint, message);
        chain.captureSessionId(result.sessionId);
        if (result.messages.length === 0 && result.status >= 400) {
          await replyError(
            message,
            new JsonRpcError(-32000, `MCP HTTP ${result.status}`),
          );
          return;
        }
        for (const msg of result.messages) {
          const withGuidance = injectGuidance(msg, request?.id, injectMethod);
          const withReadTools = injectReadTools(
            withGuidance,
            request?.id,
            request?.method,
            readBackend !== undefined,
          );
          await writeMcpStdioMessage(encoder, withReadTools);
        }
      } catch (err) {
        await handleForwardError(message, err);
      }
    });
  };

  const enqueuePending = (message: McpStdioMessage): void => {
    const dropped = queue.enqueueOrDrop(message);
    if (dropped) {
      chain.append(() =>
        replyError(
          dropped,
          new JsonRpcError(
            -32000,
            "MCP backend buffer full during SAP logon; retry after initialize",
          ),
        ),
      );
    }
  };

  const drainPending = (): void => {
    for (const message of queue.takeAll()) {
      forwardHttpOne(message);
    }
  };

  const replyAllPending = (error: JsonRpcError): void => {
    const queued = queue.takeAll();
    if (queued.length === 0) {
      return;
    }
    chain.append(async () => {
      for (const message of queued) {
        await replyError(message, error);
      }
    });
  };

  const scheduleCloseWhenIdle = (): void => {
    if (!lifecycle.canClose()) {
      return;
    }
    chain.tail().finally(() => lifecycle.resolveClose());
  };

  const handleStdinMessage = (body: string): void => {
    const message = new McpStdioMessage(body);
    if (lifecycle.failed) {
      chain.append(() =>
        replyError(
          message,
          new JsonRpcError(-32000, "MCP backend failed to start"),
        ),
      );
      return;
    }
    if (!lifecycle.ready) {
      enqueuePending(message);
      return;
    }
    forwardHttpOne(message);
  };

  decoder.on("data", handleStdinMessage);
  decoder.on("error", (err: Error) => {
    console.error(`[openadt-mcp] stdio stdin decode error: ${err.message}`);
    lifecycle.markStdinEnded();
    scheduleCloseWhenIdle();
  });

  const waitAndDrain = async (
    endpoint: McpHttpEndpoint,
    options: { waitTimeoutMs?: number; pollIntervalMs?: number },
  ): Promise<void> => {
    try {
      const httpReady = await waitForMcpHttp(endpoint, {
        timeoutMs: options.waitTimeoutMs ?? 300_000,
        intervalMs: options.pollIntervalMs ?? 500,
      });
      if (lifecycle.failed) {
        return;
      }
      if (!httpReady) {
        console.error(
          `[openadt-mcp] MCP HTTP not ready at ${endpoint.url} after 5min`,
        );
        lifecycle.markFailed();
        replyAllPending(
          new JsonRpcError(
            -32000,
            "MCP HTTP backend failed to start (SAP logon timeout)",
          ),
        );
        scheduleCloseWhenIdle();
        return;
      }
      lifecycle.markReady();
      drainPending();
      scheduleCloseWhenIdle();
    } catch (err) {
      if (lifecycle.failed) {
        return;
      }
      const message = err instanceof Error ? err.message : String(err);
      console.error(`[openadt-mcp] MCP HTTP probe crashed: ${message}`);
      lifecycle.markFailed();
      replyAllPending(
        new JsonRpcError(-32000, "MCP HTTP probe failed before ready"),
      );
      scheduleCloseWhenIdle();
    }
  };

  let onStdinEnd: (() => void) | undefined;
  let onStdinError: ((err: Error) => void) | undefined;

  const detachStdin = (): void => {
    process.stdin.unpipe(decoder);
    if (onStdinEnd) {
      process.stdin.off("end", onStdinEnd);
      onStdinEnd = undefined;
    }
    if (onStdinError) {
      process.stdin.off("error", onStdinError);
      onStdinError = undefined;
    }
    decoder.removeAllListeners("data");
    decoder.removeAllListeners("error");
  };

  return {
    start() {
      if (!lifecycle.tryStart()) {
        return;
      }
      process.stdin.pipe(decoder);
      onStdinEnd = () => {
        detachStdin();
        lifecycle.markStdinEnded();
        scheduleCloseWhenIdle();
      };
      onStdinError = (err) => {
        detachStdin();
        lifecycle.markStdinEnded();
        console.error(`[openadt-mcp] stdio stdin error: ${err.message}`);
        scheduleCloseWhenIdle();
      };
      process.stdin.on("end", onStdinEnd);
      process.stdin.on("error", onStdinError);
      if (process.stdin.isPaused()) {
        process.stdin.resume();
      }
      console.error(
        "[openadt-mcp] stdio: reading client input (SAP logon may take a minute)…",
      );
    },
    setReadBackend(b: ReadObjectBackend | undefined) {
      readBackend = b;
    },
    async run(
      endpoint: McpHttpEndpoint,
      options?: { waitTimeoutMs?: number; pollIntervalMs?: number },
    ) {
      if (!lifecycle.started) {
        throw new Error("Call start() before run()");
      }
      backend = endpoint;
      void waitAndDrain(endpoint, options ?? {});
      return lifecycle.closePromise!;
    },
    failPending(code: number, message: string) {
      lifecycle.markFailed();
      replyAllPending(new JsonRpcError(code, message));
      scheduleCloseWhenIdle();
    },
    flush(): Promise<void> {
      return chain.tail();
    },
  };
}

/** FIFO queue of stdio bodies buffered while the HTTP backend is still starting. */
class PendingBodyQueue {
  private readonly bodies: string[] = [];
  constructor(private readonly limit: number) {}

  enqueueOrDrop(message: McpStdioMessage): McpStdioMessage | undefined {
    if (this.bodies.length >= this.limit) {
      const shifted = this.bodies.shift();
      if (shifted === undefined) return undefined;
      return new McpStdioMessage(shifted);
    }
    this.bodies.push(message.body);
    return undefined;
  }

  takeAll(): McpStdioMessage[] {
    return this.bodies
      .splice(0, this.bodies.length)
      .map((body) => new McpStdioMessage(body));
  }
}

/** Sequential promise chain that serialises HTTP forwards and writes. */
class ForwardChain {
  private chain: Promise<void> = Promise.resolve();
  private nextSessionId: string | undefined;

  get sessionId(): string | undefined {
    return this.nextSessionId;
  }

  append(step: () => Promise<void>): void {
    this.chain = this.chain.then(step, () => step());
  }

  tail(): Promise<void> {
    return this.chain;
  }

  captureSessionId(value: string | undefined): void {
    if (value) {
      this.nextSessionId = value;
    }
  }
}

/** Lifecycle flags + close-promise for a stdio MCP bridge. */
class BridgeLifecycle {
  ready = false;
  failed = false;
  started = false;
  stdinEnded = false;
  closePromise: Promise<void> | undefined;
  private resolveCloseFn: (() => void) | undefined;

  tryStart(): boolean {
    if (this.started) {
      return false;
    }
    this.started = true;
    this.closePromise = new Promise<void>((resolve) => {
      this.resolveCloseFn = resolve;
    });
    return true;
  }

  markReady(): void {
    this.ready = true;
  }

  markFailed(): void {
    this.failed = true;
    this.ready = true;
  }

  markStdinEnded(): void {
    this.stdinEnded = true;
  }

  canClose(): boolean {
    return this.stdinEnded && this.ready;
  }

  resolveClose(): void {
    this.resolveCloseFn?.();
  }
}
