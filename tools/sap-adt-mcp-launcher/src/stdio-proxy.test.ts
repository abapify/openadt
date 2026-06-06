import { describe, expect, test, mock, afterEach } from "bun:test";
import {
  createStdioMcpBridge,
  JsonRpcError,
  JsonRpcRequest,
  jsonRpcErrorResponse,
  McpHttpEndpoint,
  parseMcpHttpResponseBody,
} from "./stdio-proxy.ts";
import { frameMcpMessage } from "./mcp-framing.ts";

function mockStdoutCapture(): {
  chunks: string[];
  restore: () => void;
} {
  const chunks: string[] = [];
  const origWrite = process.stdout.write.bind(process.stdout);
  process.stdout.write = ((
    chunk: string | Uint8Array,
    cb?: (err?: Error | null) => void,
  ) => {
    chunks.push(String(chunk));
    cb?.(null);
    return true;
  }) as typeof process.stdout.write;
  return {
    chunks,
    restore: () => {
      process.stdout.write = origWrite;
    },
  };
}

describe("parseMcpHttpResponseBody", () => {
  test("parses application/json body", () => {
    const body = '{"jsonrpc":"2.0","id":1,"result":{}}';
    expect(
      parseMcpHttpResponseBody({ contentType: "application/json", body }),
    ).toEqual([body]);
  });

  test("parses SSE data lines", () => {
    const sse =
      'event: message\r\ndata: {"jsonrpc":"2.0","id":1,"result":{}}\r\n\r\n';
    expect(
      parseMcpHttpResponseBody({ contentType: "text/event-stream", body: sse }),
    ).toEqual(['{"jsonrpc":"2.0","id":1,"result":{}}']);
  });

  test("returns empty for blank body", () => {
    expect(
      parseMcpHttpResponseBody({ contentType: "application/json", body: "  " }),
    ).toEqual([]);
  });
});

describe("jsonRpcErrorResponse", () => {
  test("includes request id", () => {
    const req = JsonRpcRequest.parse(
      '{"jsonrpc":"2.0","id":42,"method":"tools/list"}',
    )!;
    const err = jsonRpcErrorResponse(req, new JsonRpcError(-32000, "fail"));
    expect(err).toEqual({
      jsonrpc: "2.0",
      id: 42,
      error: { code: -32000, message: "fail" },
    });
  });

  test("skips notifications without id", () => {
    expect(
      JsonRpcRequest.parse(
        '{"jsonrpc":"2.0","method":"notifications/initialized"}',
      ),
    ).toBeUndefined();
  });
});

describe("createStdioMcpBridge", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  test("failPending writes JSON-RPC errors for queued requests", async () => {
    const { chunks, restore } = mockStdoutCapture();
    try {
      const bridge = createStdioMcpBridge();
      bridge.start();
      const req = '{"jsonrpc":"2.0","id":7,"method":"initialize","params":{}}';
      const framed = frameMcpMessage(req);
      process.stdin.emit("data", framed);
      bridge.failPending(-32000, "startup failed");
      await bridge.flush();
      process.stdin.emit("end");
      expect(chunks.join("")).toContain('"id":7');
      expect(chunks.join("")).toContain("startup failed");
    } finally {
      restore();
    }
  });

  test("run forwards queued initialize to HTTP", async () => {
    const { chunks, restore } = mockStdoutCapture();

    const initReq =
      '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}';
    const initRes =
      '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"ADT MCP Server","version":"1.0.0"}}}';

    globalThis.fetch = mock(async () =>
      Response.json(JSON.parse(initRes), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    ) as unknown as typeof fetch;

    try {
      const bridge = createStdioMcpBridge();
      bridge.start();
      process.stdin.emit("data", frameMcpMessage(initReq));

      const runPromise = bridge.run(
        McpHttpEndpoint.forConfig(2236, "test-token"),
      );
      process.stdin.emit("end");
      await runPromise;

      expect(chunks.join("")).toContain("ADT MCP Server");
    } finally {
      restore();
      globalThis.fetch = originalFetch;
    }
  });

  test("run forwards initialize then tools/list with session", async () => {
    const { chunks, restore } = mockStdoutCapture();

    const { fetchMock, sessionRef, getCallCount } = mockSessionFetch();
    globalThis.fetch = fetchMock;

    try {
      const bridge = createStdioMcpBridge();
      bridge.start();
      const writeFrame = (obj: object) => {
        process.stdin.emit("data", frameMcpMessage(obj));
      };
      writeFrame({
        jsonrpc: "2.0",
        id: 1,
        method: "initialize",
        params: {
          protocolVersion: "2024-11-05",
          capabilities: {},
          clientInfo: { name: "t", version: "1" },
        },
      });

      const runPromise = bridge.run(
        McpHttpEndpoint.forConfig(2236, "test-token"),
      );
      await new Promise((r) => setTimeout(r, 50));
      writeFrame({ jsonrpc: "2.0", method: "notifications/initialized" });
      writeFrame({ jsonrpc: "2.0", id: 2, method: "tools/list" });
      await new Promise((r) => setTimeout(r, 50));
      process.stdin.emit("end");
      await runPromise;

      const out = chunks.join("");
      expect(out).toContain("ADT MCP Server");
      expect(out).toContain('"id":2');
      expect(out).toContain('"name":"t1"');
      expect(getCallCount()).toBeGreaterThanOrEqual(3);
      expect(sessionRef.value).toBe("sess-1");
    } finally {
      restore();
      globalThis.fetch = originalFetch;
    }
  });

  test("ForwardChain continues after a rejected step", async () => {
    const { chunks, restore } = mockStdoutCapture();

    let call = 0;
    let mode: "options" | "post" = "options";
    globalThis.fetch = mock(async (_url, init) => {
      call++;
      if (mode === "options" && init?.method === "OPTIONS") {
        return new Response(null, { status: 204 });
      }
      mode = "post";
      if (call === 2) {
        throw new Error("backend down");
      }
      const body = JSON.parse(String(init?.body ?? "{}"));
      return Response.json(
        { jsonrpc: "2.0", id: body.id, result: { ok: true, n: call } },
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      );
    }) as unknown as typeof fetch;

    try {
      const bridge = createStdioMcpBridge();
      bridge.start();
      const writeFrame = (obj: object) => {
        process.stdin.emit("data", frameMcpMessage(obj));
      };
      writeFrame({ jsonrpc: "2.0", id: 10, method: "ping" });
      writeFrame({ jsonrpc: "2.0", id: 11, method: "ping" });

      const runPromise = bridge.run(
        McpHttpEndpoint.forConfig(2236, "test-token"),
      );
      await new Promise((r) => setTimeout(r, 150));
      process.stdin.emit("end");
      await runPromise;

      const out = chunks.join("");
      expect(out).toContain('"id":11');
      expect(out).toContain('"ok":true');
    } finally {
      restore();
      globalThis.fetch = originalFetch;
    }
  });

  test("stdin 'end' listener is removed after stdin closes", async () => {
    const { restore } = mockStdoutCapture();
    const baselineEnd = process.stdin.listenerCount("end");
    const baselineError = process.stdin.listenerCount("error");
    try {
      const bridge = createStdioMcpBridge();
      bridge.start();
      process.stdin.emit("end");
      await bridge.flush();
      expect(process.stdin.listenerCount("end")).toBe(baselineEnd);
      expect(process.stdin.listenerCount("error")).toBe(baselineError);
    } finally {
      restore();
    }
  });
});

/**
 * Build a fetch mock that handles the canonical MCP handshake (initialize →
 * notifications/initialized → tools/list) over a single session id, returning
 * the mock plus accessors for assertions.
 */
function mockSessionFetch(): {
  fetchMock: typeof fetch;
  sessionRef: { value: string };
  getCallCount: () => number;
} {
  const toolsPayload =
    '{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"t1"}]}}';
  const sessionRef: { value: string } = { value: "" };
  let call = 0;

  const fetchMock = mock(async (_url, init) => {
    call++;
    const body = String(init?.body ?? "");
    if (body.includes('"method":"initialize"')) {
      sessionRef.value = "sess-1";
      return Response.json(
        {
          jsonrpc: "2.0",
          id: 1,
          result: {
            protocolVersion: "2024-11-05",
            serverInfo: { name: "ADT MCP Server", version: "1.0.0" },
          },
        },
        {
          status: 200,
          headers: {
            "content-type": "application/json",
            "mcp-session-id": sessionRef.value,
          },
        },
      );
    }
    if (body.includes("notifications/initialized")) {
      return new Response(null, { status: 202 });
    }
    if (body.includes('"method":"tools/list"')) {
      expect(init?.headers?.["Mcp-Session-Id"] ?? "").toBe(sessionRef.value);
      return new Response(`event: message\r\ndata: ${toolsPayload}\r\n\r\n`, {
        status: 200,
        headers: { "content-type": "text/event-stream" },
      });
    }
    return new Response("unexpected", { status: 500 });
  }) as unknown as typeof fetch;
  return { fetchMock, sessionRef, getCallCount: () => call };
}
