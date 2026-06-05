import { describe, expect, test, mock, afterEach } from "bun:test";
import { drainHttpResponse, probeMcpHttp } from "./mcp.ts";

describe("drainHttpResponse", () => {
  test("consumes response body", async () => {
    let consumed = false;
    const res = new Response(
      new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode("ok"));
          controller.close();
        },
      }),
    );
    const orig = res.arrayBuffer.bind(res);
    res.arrayBuffer = async () => {
      consumed = true;
      return orig();
    };
    await drainHttpResponse(res);
    expect(consumed).toBe(true);
  });
});

describe("probeMcpHttp", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  test("drains probe response body", async () => {
    let drained = false;
    globalThis.fetch = mock(async (_url, init) => {
      expect(init?.method).toBe("OPTIONS");
      const res = new Response(null, { status: 200 });
      const orig = res.arrayBuffer.bind(res);
      res.arrayBuffer = async () => {
        drained = true;
        return orig();
      };
      return res;
    }) as typeof fetch;

    expect(await probeMcpHttp(2236, "token")).toBe(true);
    expect(drained).toBe(true);
  });
});
