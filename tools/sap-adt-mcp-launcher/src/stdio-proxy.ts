import {
  attachMcpStdoutEncoder,
  McpStdioDecoder,
  McpStdioEncoder,
  writeMcpStdioMessage,
} from "./mcp-framing.ts";
import { mcpUrl, waitForMcpHttp } from "./mcp.ts";

/** Parse MCP HTTP response body (JSON or SSE `data:` lines). */
export function parseMcpHttpResponseBody(
  contentType: string,
  body: string,
): string[] {
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

export function jsonRpcErrorResponse(
  requestBody: string,
  code: number,
  message: string,
): object | undefined {
  try {
    const parsed = JSON.parse(requestBody) as { id?: unknown };
    if (parsed.id === undefined) {
      return undefined;
    }
    return {
      jsonrpc: "2.0",
      id: parsed.id,
      error: { code, message },
    };
  } catch {
    return undefined;
  }
}

export async function postMcpHttpMessage(
  port: number,
  token: string,
  body: string,
  sessionId?: string,
  options?: { timeoutMs?: number },
): Promise<{ messages: string[]; sessionId?: string; status: number }> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "User-Agent": "openadt-mcp-client",
    Authorization: `Bearer ${token}`,
  };
  if (sessionId) {
    headers["Mcp-Session-Id"] = sessionId;
  }

  const timeoutMs = options?.timeoutMs ?? 60_000;
  const res = await fetch(mcpUrl(port), {
    method: "POST",
    headers,
    body,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const nextSessionId = res.headers.get("Mcp-Session-Id") ?? sessionId;
  const text = await res.text();
  const contentType = res.headers.get("content-type") ?? "";
  const messages = parseMcpHttpResponseBody(contentType, text);
  return {
    messages,
    sessionId: nextSessionId ?? undefined,
    status: res.status,
  };
}

export type StdioMcpBridge = {
  /** Begin reading stdin immediately; queue until run(). */
  start(): void;
  /** Wait for HTTP MCP, flush queue, forward until stdin closes and forwards drain. */
  run(port: number, token: string): Promise<void>;
  /** Reply with JSON-RPC errors to all queued requests that have an id. */
  failPending(code: number, message: string): void;
  /** Wait until queued stdio writes finish (call before process exit). */
  flush(): Promise<void>;
};

type Backend = { port: number; token: string };

/** Transparent stdio MCP bridge to local SAP ADT HTTP MCP. */
export function createStdioMcpBridge(): StdioMcpBridge {
  const queue = new PendingBodyQueue(256);
  const chain = new ForwardChain();
  const lifecycle = new BridgeLifecycle();
  let backend: Backend | undefined;

  const decoder = new McpStdioDecoder();
  const encoder = new McpStdioEncoder();
  decoder.on("transport", (mode) => {
    encoder.setTransport(mode);
  });
  attachMcpStdoutEncoder(encoder);

  const replyError = async (
    body: string,
    code: number,
    message: string,
  ): Promise<void> => {
    const err = jsonRpcErrorResponse(body, code, message);
    if (err) {
      await writeMcpStdioMessage(encoder, err);
    }
  };

  const forwardHttpOne = (body: string): void => {
    if (!backend) {
      return;
    }
    const http = backend;
    chain.append(async () => {
      try {
        const result = await postMcpHttpMessage(
          http.port,
          http.token,
          body,
          chain.sessionId,
        );
        chain.captureSessionId(result.sessionId);
        if (result.messages.length === 0 && result.status >= 400) {
          await replyError(body, -32000, `MCP HTTP ${result.status}`);
          return;
        }
        for (const msg of result.messages) {
          await writeMcpStdioMessage(encoder, msg);
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.error(`[openadt-mcp] stdio proxy: ${message}`);
        await replyError(body, -32000, message);
      }
    });
  };

  const enqueuePending = (body: string): void => {
    const dropped = queue.enqueueOrDrop(body);
    if (dropped) {
      chain.append(() =>
        replyError(
          dropped,
          -32000,
          "MCP backend buffer full during SAP logon; retry after initialize",
        ),
      );
    }
  };

  const drainPending = (): void => {
    for (const body of queue.takeAll()) {
      forwardHttpOne(body);
    }
  };

  const replyAllPending = (code: number, message: string): void => {
    const queued = queue.takeAll();
    if (queued.length === 0) {
      return;
    }
    chain.append(async () => {
      for (const body of queued) {
        await replyError(body, code, message);
      }
    });
  };

  const scheduleCloseWhenIdle = (): void => {
    if (!lifecycle.canClose()) {
      return;
    }
    chain.tail().finally(() => lifecycle.resolveClose());
  };

  decoder.on("data", (body: string) => {
    if (lifecycle.failed) {
      chain.append(() =>
        replyError(body, -32000, "MCP backend failed to start"),
      );
      return;
    }
    if (!lifecycle.ready) {
      enqueuePending(body);
      return;
    }
    forwardHttpOne(body);
  });
  decoder.on("error", (err: Error) => {
    console.error(`[openadt-mcp] stdio stdin decode error: ${err.message}`);
    lifecycle.markStdinEnded();
    scheduleCloseWhenIdle();
  });

  const detachStdin = (): void => {
    process.stdin.unpipe(decoder);
    decoder.removeAllListeners("data");
    decoder.removeAllListeners("error");
  };

  return {
    start() {
      if (!lifecycle.tryStart()) {
        return;
      }
      process.stdin.pipe(decoder);
      process.stdin.on("end", () => {
        detachStdin();
        lifecycle.markStdinEnded();
        scheduleCloseWhenIdle();
      });
      process.stdin.on("error", (err) => {
        detachStdin();
        lifecycle.markStdinEnded();
        console.error(`[openadt-mcp] stdio stdin error: ${err.message}`);
        scheduleCloseWhenIdle();
      });
      if (process.stdin.isPaused()) {
        process.stdin.resume();
      }
      console.error(
        "[openadt-mcp] stdio: reading client input (SAP logon may take a minute)…",
      );
    },
    async run(port: number, token: string) {
      if (!lifecycle.started) {
        throw new Error("Call start() before run()");
      }
      backend = { port, token };
      void waitForMcpHttp(port, token, {
        timeoutMs: 300_000,
        intervalMs: 500,
      }).then(async (httpReady) => {
        if (!httpReady) {
          console.error(
            `[openadt-mcp] MCP HTTP not ready at ${mcpUrl(port)} after 5min`,
          );
          lifecycle.markFailed();
          replyAllPending(
            -32000,
            "MCP HTTP backend failed to start (SAP logon timeout)",
          );
          scheduleCloseWhenIdle();
          return;
        }
        lifecycle.markReady();
        drainPending();
        scheduleCloseWhenIdle();
      });
      return lifecycle.closePromise!;
    },
    failPending(code: number, message: string) {
      lifecycle.markFailed();
      replyAllPending(code, message);
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

  enqueueOrDrop(body: string): string | undefined {
    if (this.bodies.length >= this.limit) {
      return this.bodies.shift();
    }
    this.bodies.push(body);
    return undefined;
  }

  takeAll(): string[] {
    return this.bodies.splice(0, this.bodies.length);
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
    this.chain = this.chain.then(step);
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
