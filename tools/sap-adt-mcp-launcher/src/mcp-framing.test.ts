import { describe, expect, test } from "bun:test";
import { PassThrough } from "node:stream";
import { finished } from "node:stream/promises";
import {
  detectMcpStdioTransport,
  frameMcpMessage,
  McpFrameDecoder,
  McpFrameEncoder,
  McpNdjsonDecoder,
  McpStdioDecoder,
  McpStdioEncoder,
  writeMcpStdioMessage,
} from "./mcp-framing.ts";

describe("frameMcpMessage", () => {
  test("uses Content-Length byte count", () => {
    const body = '{"jsonrpc":"2.0","id":1,"result":{}}';
    const frame = frameMcpMessage(body);
    const headerEnd = frame.indexOf("\r\n\r\n");
    expect(headerEnd).toBeGreaterThan(0);
    const headers = frame.subarray(0, headerEnd).toString("utf8");
    const match = /Content-Length:\s*(\d+)/i.exec(headers);
    expect(match?.[1]).toBe(String(Buffer.from(body, "utf8").length));
    expect(frame.subarray(headerEnd + 4).toString("utf8")).toBe(body);
  });

  test("counts UTF-8 bytes not UTF-16 code units", () => {
    const body = '{"msg":"ü"}';
    const frame = frameMcpMessage(body);
    const headerEnd = frame.indexOf("\r\n\r\n");
    const match = /Content-Length:\s*(\d+)/i.exec(
      frame.subarray(0, headerEnd).toString("utf8"),
    );
    expect(Number(match?.[1])).toBe(Buffer.from(body, "utf8").length);
    expect(Buffer.from(body, "utf8").length).toBeGreaterThan(body.length);
  });
});

describe("McpFrameDecoder", () => {
  test("emits complete frames from chunked input", async () => {
    const payload = '{"jsonrpc":"2.0","id":1,"method":"tools/list"}';
    const frame = frameMcpMessage(payload);
    const decoder = new McpFrameDecoder();
    const out: string[] = [];
    decoder.on("data", (body: string) => out.push(body));

    const half = Math.floor(frame.length / 2);
    decoder.write(frame.subarray(0, half));
    decoder.write(frame.subarray(half));
    decoder.end();

    await finished(decoder);
    expect(out).toEqual([payload]);
  });

  test("emits multiple frames in one write", async () => {
    const a = '{"id":1}';
    const b = '{"id":2}';
    const combined = Buffer.concat([frameMcpMessage(a), frameMcpMessage(b)]);
    const decoder = new McpFrameDecoder();
    const out: string[] = [];
    decoder.on("data", (body: string) => out.push(body));
    decoder.end(combined);
    await finished(decoder);
    expect(out).toEqual([a, b]);
  });
});

describe("McpFrameEncoder", () => {
  test("writes framed bytes with backpressure", async () => {
    const sink = new PassThrough();
    const chunks: Buffer[] = [];
    sink.on("data", (c: Buffer) => chunks.push(c));

    const encoder = new McpFrameEncoder();
    encoder.pipe(sink);

    const body = "x".repeat(5000);
    await writeMcpStdioMessage(encoder, body);
    encoder.end();
    await finished(sink);

    const frame = Buffer.concat(chunks);
    const headerEnd = frame.indexOf("\r\n\r\n");
    expect(frame.subarray(headerEnd + 4).toString("utf8")).toBe(body);
  });
});

describe("detectMcpStdioTransport", () => {
  test("detects NDJSON from Cursor agent CLI", () => {
    const chunk = Buffer.from(
      '{"method":"initialize","params":{"protocolVersion":"2025-11-25"}}',
      "utf8",
    );
    expect(detectMcpStdioTransport(chunk)).toBe("ndjson");
  });

  test("detects Content-Length framing", () => {
    const chunk = frameMcpMessage('{"id":1}');
    expect(detectMcpStdioTransport(chunk)).toBe("content-length");
  });
});

describe("McpNdjsonDecoder", () => {
  test("parses single JSON object without trailing newline", async () => {
    const payload =
      '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}';
    const decoder = new McpNdjsonDecoder();
    const out: string[] = [];
    decoder.on("data", (body: string) => out.push(body));
    decoder.end(payload);
    await finished(decoder);
    expect(out).toEqual([payload]);
  });
});

describe("McpStdioDecoder", () => {
  test("auto-selects NDJSON for agent-style input", async () => {
    const payload = '{"jsonrpc":"2.0","id":1,"method":"tools/list"}';
    const decoder = new McpStdioDecoder();
    const out: string[] = [];
    let mode: string | undefined;
    decoder.on("transport", (m) => {
      mode = m;
    });
    decoder.on("data", (body: string) => out.push(body));
    decoder.end(payload);
    await finished(decoder);
    expect(mode).toBe("ndjson");
    expect(out).toEqual([payload]);
  });
});

describe("McpStdioEncoder", () => {
  test("writes NDJSON lines for agent clients", async () => {
    const sink = new PassThrough();
    const chunks: Buffer[] = [];
    sink.on("data", (c: Buffer) => chunks.push(c));

    const encoder = new McpStdioEncoder("ndjson");
    encoder.pipe(sink);

    await writeMcpStdioMessage(encoder, { jsonrpc: "2.0", id: 1, result: {} });
    encoder.end();
    await finished(sink);

    expect(Buffer.concat(chunks).toString("utf8")).toBe(
      '{"jsonrpc":"2.0","id":1,"result":{}}\n',
    );
  });
});
