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

/** Parse `OPENADT_MCP_PORT` if it is a valid port; log a warning and return undefined otherwise. */
function parseExplicitPort(raw: string | undefined): number | undefined {
  if (!raw) {
    return undefined;
  }
  const port = Number(raw);
  if (!isValidPort(port)) {
    console.error(
      `[openadt-mcp] Invalid OPENADT_MCP_PORT=${raw} (expected integer ${PORT_MIN}-${PORT_MAX}); falling back to ephemeral.`,
    );
    return undefined;
  }
  return port;
}

async function pickMcpPort(): Promise<number> {
  const explicit = parseExplicitPort(process.env.OPENADT_MCP_PORT?.trim());
  if (explicit !== undefined) {
    return explicit;
  }
  return bindEphemeralPort();
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

/**
 * Wait for a readable stream to reach EOF. The child writes a JSON-RPC
 * streamable-HTTP response to stdout/stderr; we want the parent to flush
 * those bytes to its own stdout before exiting, otherwise the IDE MCP client
 * can lose the tail of a response.
 *
 * Resolves on `end`/`close`, or after a 250ms safety timer so the parent
 * never wedges if the readable never finishes.
 */
function drainReadable(stream: NodeJS.ReadableStream | null): Promise<void> {
  if (!stream) {
    return Promise.resolve();
  }
  return new Promise<void>((resolve) => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      stream.off("end", finish);
      stream.off("close", finish);
      resolve();
    };
    stream.once("end", finish);
    stream.once("close", finish);
    setTimeout(finish, 250).unref();
  });
}

/** Wait for the parent's stdout writable to drain any pending bytes. */
function drainStdoutWritable(): Promise<void> {
  if (!process.stdout.writableNeedDrain) {
    return Promise.resolve();
  }
  return new Promise<void>((resolve) => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      process.stdout.off("drain", finish);
      process.stdout.off("close", finish);
      process.stdout.off("error", finish);
      resolve();
    };
    process.stdout.once("drain", finish);
    process.stdout.once("close", finish);
    process.stdout.once("error", finish);
    setTimeout(finish, 250).unref();
  });
}

async function drainChildStreams(
  child: ChildProcessWithoutNullStreams,
): Promise<void> {
  await Promise.all([drainReadable(child.stdout), drainReadable(child.stderr)]);
  await drainStdoutWritable();
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
