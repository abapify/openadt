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

/** Transparent stdio MCP bridge to local SAP ADT HTTP MCP. */
export function createStdioMcpBridge(): StdioMcpBridge {
  let sessionId: string | undefined;
  let forwardChain = Promise.resolve();
  let ready = false;
  let backend: { port: number; token: string } | undefined;
  const queue = new PendingBodyQueue(256);
  let resolveClose: (() => void) | undefined;
  let closePromise: Promise<void> | undefined;
  let started = false;
  let stdinEnded = false;
  let failed = false;

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

  const appendForward = (step: () => Promise<void>): void => {
    forwardChain = forwardChain.then(step);
  };

  const forwardHttpOne = (body: string): void => {
    if (!backend) {
      return;
    }
    const http = backend;
    appendForward(async () => {
      try {
        const result = await postMcpHttpMessage(
          http.port,
          http.token,
          body,
          sessionId,
        );
        if (result.sessionId) {
          sessionId = result.sessionId;
        }
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
      appendForward(() =>
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
    appendForward(async () => {
      for (const body of queued) {
        await replyError(body, code, message);
      }
    });
  };

  const scheduleCloseWhenIdle = (): void => {
    if (!stdinEnded || !ready) {
      return;
    }
    forwardChain.finally(() => resolveClose?.());
  };

  decoder.on("data", (body: string) => {
    if (failed) {
      appendForward(() =>
        replyError(body, -32000, "MCP backend failed to start"),
      );
      return;
    }
    if (!ready) {
      enqueuePending(body);
      return;
    }
    forwardHttpOne(body);
  });
  decoder.on("error", (err: Error) => {
    console.error(`[openadt-mcp] stdio stdin decode error: ${err.message}`);
    stdinEnded = true;
    scheduleCloseWhenIdle();
  });

  const detachStdin = (): void => {
    process.stdin.unpipe(decoder);
    decoder.removeAllListeners("data");
    decoder.removeAllListeners("error");
  };

  return {
    start() {
      if (started) {
        return;
      }
      started = true;
      closePromise = new Promise((resolve) => {
        resolveClose = resolve;
      });
      process.stdin.pipe(decoder);
      process.stdin.on("end", () => {
        detachStdin();
        stdinEnded = true;
        scheduleCloseWhenIdle();
      });
      process.stdin.on("error", (err) => {
        detachStdin();
        stdinEnded = true;
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
      if (!started) {
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
          failed = true;
          ready = true;
          replyAllPending(
            -32000,
            "MCP HTTP backend failed to start (SAP logon timeout)",
          );
          scheduleCloseWhenIdle();
          return;
        }
        ready = true;
        drainPending();
        scheduleCloseWhenIdle();
      });
      return closePromise!;
    },
    failPending(code: number, message: string) {
      failed = true;
      ready = true;
      replyAllPending(code, message);
      scheduleCloseWhenIdle();
    },
    flush(): Promise<void> {
      return forwardChain;
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
