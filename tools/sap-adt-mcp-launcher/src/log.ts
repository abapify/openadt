import { appendFileSync, mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

export const DEFAULT_MCP_LOG = join(
  homedir(),
  ".openadt",
  "logs",
  "mcp-serve.log",
);

export type McpLog = {
  readonly logPath: string;
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
  /** Full LSP / adt-lsc trace (file only). */
  trace(message: string): void;
  adtLscStderr(chunk: string): void;
  dispose(): void;
};

export function isMcpDebugEnabled(): boolean {
  const v = process.env.MCP_DEBUG?.trim().toLowerCase();
  return v === "1" || v === "true" || v === "yes";
}

export function createMcpLog(options: {
  verbose?: boolean;
  logFile?: string;
}): McpLog | undefined {
  if (!options.verbose && !isMcpDebugEnabled()) {
    return undefined;
  }
  const logPath = options.logFile?.trim() || DEFAULT_MCP_LOG;
  mkdirSync(dirname(logPath), { recursive: true });
  return new FileMcpLog(logPath);
}

/** Redact Bearer tokens and common secret fields before writing logs. */
export function redactSecrets(text: string): string {
  return text
    .replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer [REDACTED]")
    .replace(/"token"\s*:\s*"[^"]*"/gi, '"token":"[REDACTED]"')
    .replace(/"password"\s*:\s*"[^"]*"/gi, '"password":"[REDACTED]"')
    .replace(/"clientSecret"\s*:\s*"[^"]*"/gi, '"clientSecret":"[REDACTED]"');
}

class FileMcpLog implements McpLog {
  readonly logPath: string;

  constructor(logPath: string) {
    this.logPath = logPath;
    this.write("info", "openadt mcp debug session started");
  }

  info(message: string): void {
    this.emit("info", message);
  }

  warn(message: string): void {
    this.emit("warn", message);
  }

  error(message: string): void {
    this.emit("error", message);
  }

  trace(message: string): void {
    this.write("trace", message);
  }

  adtLscStderr(chunk: string): void {
    for (const line of chunk.split(/\r?\n/)) {
      if (line.trim()) {
        this.write("adt-lsc", line);
      }
    }
  }

  dispose(): void {
    this.write("info", "openadt mcp debug session ended");
  }

  private emit(level: string, message: string): void {
    this.write(level, message);
    if (level === "error" || level === "warn" || level === "info") {
      console.error(`[openadt-mcp] ${level}: ${redactSecrets(message)}`);
    }
  }

  private write(level: string, message: string): void {
    const line = `${isoTimestamp()} [${level}] ${redactSecrets(message)}\n`;
    appendFileSync(this.logPath, line, "utf8");
  }
}

function isoTimestamp(): string {
  return new Date().toISOString();
}

export function eclipseWorkspaceLogPath(workspace: string): string {
  return join(workspace, ".metadata", ".log");
}
