#!/usr/bin/env bun
/**
 * Stdio MCP entry for agents with a minimal PATH (Cursor agent CLI, IDE MCP).
 * Resolves Bun from ~/.bun/bin without absolute paths in .cursor/mcp.json.
 * Proxies stdin/stdout explicitly (inherit breaks some MCP clients on Windows).
 */
import { existsSync } from "node:fs";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { createServer } from "node:net";
import { dirname, join } from "node:path";
import { homedir } from "node:os";
import { fileURLToPath } from "node:url";

import { buildAdtLscSpawnRuntime } from "./runtime-env.ts";
import { DEFAULT_MCP_PORT } from "./types.ts";

const repoRoot = join(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "..",
  "..",
);
const launcher = join(
  repoRoot,
  "tools",
  "sap-adt-mcp-launcher",
  "src",
  "main.ts",
);

function resolveBun(): string {
  if (process.env.OPENADT_BUN?.trim()) {
    return process.env.OPENADT_BUN.trim();
  }
  const bunName = process.platform === "win32" ? "bun.exe" : "bun";
  const installed = join(homedir(), ".bun", "bin", bunName);
  if (existsSync(installed)) {
    return installed;
  }
  return bunName;
}

const PORT_MIN = 1024;
const PORT_MAX = 65535;

function isValidPort(port: number): boolean {
  return Number.isInteger(port) && port >= PORT_MIN && port <= PORT_MAX;
}

function validatePort(port: number, raw: string): number {
  if (!isValidPort(port)) {
    throw new Error(
      `Invalid OPENADT_MCP_PORT=${raw} (expected integer ${PORT_MIN}-${PORT_MAX}); falling back to ephemeral.`,
    );
  }
  return port;
}

async function pickMcpPort(): Promise<number> {
  const explicit = process.env.OPENADT_MCP_PORT?.trim();
  if (!explicit) {
    return bindEphemeralPort();
  }
  return validatePort(Number(explicit), explicit);
}

function bindEphemeralPort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.unref();
    server.on("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (!address || typeof address === "string") {
        server.close();
        reject(new Error("Could not bind ephemeral port"));
        return;
      }
      const port = address.port;
      server.close((err) => {
        if (err) {
          reject(err);
          return;
        }
        resolve(port >= PORT_MIN ? port : DEFAULT_MCP_PORT);
      });
    });
  });
}

function pipeStdio(child: ChildProcessWithoutNullStreams): void {
  process.stdin.pipe(child.stdin);
  child.stdout.pipe(process.stdout);
  child.stderr.pipe(process.stderr);

  process.stdin.on("end", () => {
    child.stdin.end();
  });
  process.stdin.on("error", () => {
    child.stdin.destroy();
  });
  child.stdout.on("error", () => {
    process.stdout.destroy();
  });
  child.stderr.on("error", () => {
    process.stderr.destroy();
  });
}

/** A readable whose downstream has caught up; if we never see `drain` we still
 * resolve after the 250ms safety timer so the parent never wedges. */
function drainStream(stream: NodeJS.ReadableStream | null): Promise<void> {
  if (!stream) {
    return Promise.resolve();
  }
  const readable = stream as NodeJS.ReadableStream & {
    writableEnded?: boolean;
    writableLength?: number;
    end?: () => void;
  };
  if (readable.writableEnded && readable.writableLength === 0) {
    return Promise.resolve();
  }
  return new Promise<void>((resolve) => {
    readable.once("drain", resolve);
    readable.end?.();
    setTimeout(resolve, 250).unref();
  });
}

async function drainChildStreams(
  child: ChildProcessWithoutNullStreams,
): Promise<void> {
  await Promise.all([drainStream(child.stdout), drainStream(child.stderr)]);
}

const port = await pickMcpPort();
const serveArgs = [
  "serve",
  "--stdio",
  "--port",
  String(port),
  ...process.argv.slice(2),
];

const bun = resolveBun();
const runtime = buildAdtLscSpawnRuntime();
const child: ChildProcessWithoutNullStreams = spawn(
  bun,
  [launcher, ...serveArgs],
  {
    cwd: repoRoot,
    stdio: ["pipe", "pipe", "pipe"],
    env: runtime.env,
    windowsHide: true,
  },
);

child.on("error", (err) => {
  console.error(
    `[openadt-mcp] failed to spawn ${bun}: ${err.message}\n` +
      "Install Bun (https://bun.sh) or set OPENADT_BUN to your bun executable.",
  );
  process.exit(1);
});

pipeStdio(child);

child.on("exit", async (code, signal) => {
  // Give the stdout/stderr pipes a tick to flush any final bytes the child
  // wrote just before exiting, then exit with the child's status. process.exit
  // terminates the event loop immediately, which can otherwise truncate the
  // tail of a streamable HTTP response in the MCP client.
  await drainChildStreams(child);
  if (signal) {
    process.exit(1);
  }
  process.exit(code ?? 1);
});
