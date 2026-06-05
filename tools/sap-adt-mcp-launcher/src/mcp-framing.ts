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

/**
 * Decode MCP stdio transport: bytes → JSON-RPC body strings.
 * Content-Length values are byte counts (UTF-8), not UTF-16 length.
 */
export class McpFrameDecoder extends Transform {
  private buffer = Buffer.alloc(0);

  constructor() {
    super({ readableObjectMode: true });
  }

  override _transform(
    chunk: Buffer,
    _encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    try {
      this.buffer = Buffer.concat([this.buffer, chunk]);
      this.emitReadyFrames();
      callback();
    } catch (err) {
      callback(err instanceof Error ? err : new Error(String(err)));
    }
  }

  override _flush(callback: TransformCallback): void {
    try {
      this.emitReadyFrames();
      if (this.buffer.length > 0) {
        callback(
          new Error(
            `Incomplete MCP frame (${this.buffer.length} trailing bytes after stdin end)`,
          ),
        );
        return;
      }
      callback();
    } catch (err) {
      callback(err instanceof Error ? err : new Error(String(err)));
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
export class McpNdjsonDecoder extends Transform {
  private buffer = "";

  constructor() {
    super({ readableObjectMode: true });
  }

  override _transform(
    chunk: Buffer,
    _encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    try {
      this.buffer += chunk.toString("utf8");
      this.drain(false);
      callback();
    } catch (err) {
      callback(err instanceof Error ? err : new Error(String(err)));
    }
  }

  override _flush(callback: TransformCallback): void {
    try {
      this.drain(true);
      callback();
    } catch (err) {
      callback(err instanceof Error ? err : new Error(String(err)));
    }
  }

  private drain(flush: boolean): void {
    while (true) {
      const newline = this.buffer.indexOf("\n");
      if (newline >= 0) {
        const line = this.buffer.slice(0, newline).trim();
        this.buffer = this.buffer.slice(newline + 1);
        if (line) {
          this.push(line);
        }
        continue;
      }
      const trimmed = this.buffer.trim();
      if (!trimmed) {
        return;
      }
      if (trimmed.startsWith("{")) {
        try {
          JSON.parse(trimmed);
          this.push(trimmed);
          this.buffer = "";
          continue;
        } catch {
          if (!flush) {
            return;
          }
        }
      }
      if (flush && trimmed) {
        this.push(trimmed);
        this.buffer = "";
      }
      return;
    }
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

  override _transform(
    chunk: Buffer,
    encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    if (this.mode === "pending") {
      this.mode = detectMcpStdioTransport(chunk);
      this.emit("transport", this.mode);
    }
    const sink = this.mode === "ndjson" ? this.ndjson : this.framed;
    sink.write(chunk, encoding, callback);
  }

  override _flush(callback: TransformCallback): void {
    const sink = this.mode === "ndjson" ? this.ndjson : this.framed;
    sink.end(callback);
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

  override _transform(
    body: string,
    _encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    try {
      this.push(
        this.mode === "ndjson" ? encodeNdjsonLine(body) : frameMcpMessage(body),
      );
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
