import { Transform, type TransformCallback } from "node:stream";

const HEADER_TERMINATOR = Buffer.from("\r\n\r\n");
const CONTENT_LENGTH = /Content-Length:\s*(\d+)/i;

export type McpStdioTransport = "content-length" | "ndjson";

/** Detect Cursor agent CLI (NDJSON) vs IDE MCP (Content-Length). */
export function detectMcpStdioTransport(chunk: Buffer): McpStdioTransport {
  const trimmed = chunk.toString("utf8").trimStart();
  if (trimmed.startsWith("{")) {
    return "ndjson";
  }
  if (/^Content-Length:/im.test(trimmed)) {
    return "content-length";
  }
  return "content-length";
}

/** One Content-Length MCP frame (bytes). */
export function frameMcpMessage(msg: object | string): Buffer {
  const body = typeof msg === "string" ? msg : JSON.stringify(msg);
  const bodyBuf = Buffer.from(body, "utf8");
  const header = Buffer.from(
    `Content-Length: ${bodyBuf.length}\r\n\r\n`,
    "utf8",
  );
  return Buffer.concat([header, bodyBuf]);
}

function encodeNdjsonLine(body: string): Buffer {
  return Buffer.from(`${body.trim()}\n`, "utf8");
}

/** Shared Transform lifecycle (try/catch + callback) for all MCP stdio decoders. */
abstract class McpDecoder extends Transform {
  constructor() {
    super({ readableObjectMode: true });
  }

  private safeCall(step: () => void, callback: TransformCallback): void {
    try {
      step();
      callback();
    } catch (err) {
      callback(err instanceof Error ? err : new Error(String(err)));
    }
  }

  override _transform(
    chunk: Buffer,
    _encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    this.safeCall(() => this.onChunk(chunk), callback);
  }

  override _flush(callback: TransformCallback): void {
    this.safeCall(() => this.onEnd(), callback);
  }

  protected abstract onChunk(chunk: Buffer): void;
  protected abstract onEnd(): void;
}

/**
 * Decode MCP stdio transport: bytes → JSON-RPC body strings.
 * Content-Length values are byte counts (UTF-8), not UTF-16 length.
 */
export class McpFrameDecoder extends McpDecoder {
  private buffer = Buffer.alloc(0);

  protected override onChunk(chunk: Buffer): void {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    this.processFrame(false);
  }

  protected override onEnd(): void {
    this.processFrame(true);
  }

  private processFrame(flush: boolean): void {
    this.emitReadyFrames();
    if (!flush) {
      return;
    }
    if (this.buffer.length > 0) {
      throw new Error(
        `Incomplete MCP frame (${this.buffer.length} trailing bytes after stdin end)`,
      );
    }
  }

  private emitReadyFrames(): void {
    while (true) {
      const headerEnd = this.buffer.indexOf(HEADER_TERMINATOR);
      if (headerEnd < 0) {
        return;
      }
      const headers = this.buffer.subarray(0, headerEnd).toString("utf8");
      const match = CONTENT_LENGTH.exec(headers);
      if (!match) {
        this.buffer = this.buffer.subarray(
          headerEnd + HEADER_TERMINATOR.length,
        );
        continue;
      }
      const len = Number(match[1]);
      const bodyStart = headerEnd + HEADER_TERMINATOR.length;
      if (this.buffer.length < bodyStart + len) {
        return;
      }
      const body = this.buffer
        .subarray(bodyStart, bodyStart + len)
        .toString("utf8");
      this.buffer = this.buffer.subarray(bodyStart + len);
      this.push(body);
    }
  }
}

/** NDJSON lines or single JSON objects (Cursor agent CLI). */
export class McpNdjsonDecoder extends McpDecoder {
  private readonly utf8 = new TextDecoder("utf-8");
  private buffer = "";

  protected override onChunk(chunk: Buffer): void {
    this.buffer += this.utf8.decode(chunk, { stream: true });
    this.processLines(false);
  }

  protected override onEnd(): void {
    this.buffer += this.utf8.decode(new Uint8Array(0), { stream: false });
    this.processLines(true);
  }

  private processLines(flush: boolean): void {
    this.drainLines();
    if (flush) {
      this.handleTrailing();
    }
  }

  /** Emit one message per `\n`-terminated line; preserve the rest of the buffer for the next chunk. */
  private drainLines(): void {
    let newline = this.buffer.indexOf("\n");
    while (newline >= 0) {
      const rawLine = this.buffer.slice(0, newline);
      this.buffer = this.buffer.slice(newline + 1);
      const line = rawLine.trim();
      if (line) {
        assertValidJson(line);
        this.push(line);
      }
      newline = this.buffer.indexOf("\n");
    }
  }

  /** After draining lines, decide what to do with whatever remains in the buffer. */
  private handleTrailing(): void {
    const trimmed = this.buffer.trim();
    this.buffer = "";
    if (!trimmed) {
      return;
    }
    if (trimmed.startsWith("{")) {
      assertValidJson(trimmed);
      this.push(trimmed);
      return;
    }
    throw new Error(
      `Non-JSON trailing data in NDJSON stream: ${trimmed.slice(0, 100)}`,
    );
  }
}

class InvalidNdjsonError extends Error {
  override readonly name = "InvalidNdjsonError";
  constructor(cause: unknown) {
    super(
      `Invalid JSON in NDJSON stream: ${
        cause instanceof Error ? cause.message : String(cause)
      }`,
    );
  }
}

function assertValidJson(line: string): void {
  try {
    JSON.parse(line);
  } catch (err) {
    throw new InvalidNdjsonError(err);
  }
}

/** Auto-detect Content-Length vs NDJSON on first stdin chunk. */
export class McpStdioDecoder extends Transform {
  private mode: McpStdioTransport | "pending" = "pending";
  private readonly framed = new McpFrameDecoder();
  private readonly ndjson = new McpNdjsonDecoder();

  constructor() {
    super({ readableObjectMode: true });
    this.framed.on("data", (body: string) => this.push(body));
    this.ndjson.on("data", (body: string) => this.push(body));
    this.framed.on("error", (err: Error) => this.emit("error", err));
    this.ndjson.on("error", (err: Error) => this.emit("error", err));
  }

  get transport(): McpStdioTransport | undefined {
    return this.mode === "pending" ? undefined : this.mode;
  }

  private get activeSink(): McpFrameDecoder | McpNdjsonDecoder {
    return this.mode === "ndjson" ? this.ndjson : this.framed;
  }

  override _transform(
    chunk: Buffer,
    encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    if (this.mode === "pending") {
      this.mode = detectMcpStdioTransport(chunk);
      this.emit("transport", this.mode);
    }
    this.activeSink.write(chunk, encoding, callback);
  }

  override _flush(callback: TransformCallback): void {
    this.activeSink.end(callback);
  }
}

/** Encode JSON-RPC bodies for Content-Length or NDJSON stdio transport. */
export class McpStdioEncoder extends Transform {
  private mode: McpStdioTransport;

  constructor(mode: McpStdioTransport = "content-length") {
    super({ writableObjectMode: true });
    this.mode = mode;
  }

  setTransport(mode: McpStdioTransport): void {
    this.mode = mode;
  }

  private encodeBody(body: string): Buffer {
    return this.mode === "ndjson"
      ? encodeNdjsonLine(body)
      : frameMcpMessage(body);
  }

  override _transform(
    body: string,
    _encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    try {
      this.push(this.encodeBody(body));
      callback();
    } catch (err) {
      callback(err instanceof Error ? err : new Error(String(err)));
    }
  }
}

/** @deprecated Use McpStdioEncoder */
export class McpFrameEncoder extends McpStdioEncoder {
  constructor() {
    super("content-length");
  }
}

/** Write one stdio message and wait until the encoder accepts it (backpressure-safe). */
export function writeMcpStdioMessage(
  encoder: McpStdioEncoder,
  msg: object | string,
): Promise<void> {
  const body = typeof msg === "string" ? msg : JSON.stringify(msg);
  return new Promise((resolve, reject) => {
    encoder.write(body, (err) => {
      if (err) {
        reject(err);
      } else {
        resolve();
      }
    });
  });
}

/** @deprecated Use writeMcpStdioMessage */
export const writeFramedMessage = writeMcpStdioMessage;

/** Attach encoder to stdout once; keeps stdout open when the encoder ends. */
export function attachMcpStdoutEncoder(encoder: McpStdioEncoder): void {
  encoder.pipe(process.stdout, { end: false });
  encoder.on("error", (err) => {
    console.error(`[openadt-mcp] stdio stdout error: ${err.message}`);
  });
}
