#!/usr/bin/env bun
/**
 * Stdio MCP entry for agents with a minimal PATH (Cursor agent CLI, IDE MCP).
 * Resolves Bun from ~/.bun/bin without absolute paths in .cursor/mcp.json.
 * Proxies stdin/stdout explicitly (inherit breaks some MCP clients on Windows).
 */
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { createServer } from "node:net";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { buildAdtLscSpawnRuntime } from "./runtime-env.ts";
import { resolveBunExecutable } from "./resolve-bun.ts";
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
  return resolveBunExecutable();
}

const PORT_MIN = 1024;
const PORT_MAX = 65535;

async function pickMcpPort(): Promise<number> {
  const explicit = process.env.OPENADT_MCP_PORT?.trim();
  if (explicit) {
    const port = Number(explicit);
    if (!Number.isInteger(port) || port < PORT_MIN || port > PORT_MAX) {
      throw new Error(
        `Invalid OPENADT_MCP_PORT=${explicit} (expected integer ${PORT_MIN}-${PORT_MAX}); falling back to ephemeral.`,
      );
    }
    return port;
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

child.on("exit", (code, signal) => {
  // Give the stdout/stderr pipes a tick to flush any final bytes the child
  // wrote just before exiting, then exit with the child's status. process.exit
  // terminates the event loop immediately, which can otherwise truncate the
  // tail of a streamable HTTP response in the MCP client.
  const finalize = () => {
    if (signal) {
      process.exit(1);
    }
    process.exit(code ?? 1);
  };
  const streams: Array<NodeJS.ReadableStream | null> = [
    child.stdout,
    child.stderr,
  ];
  let pending = streams.length;
  const onDrained = () => {
    pending -= 1;
    if (pending === 0) {
      finalize();
    }
  };
  for (const stream of streams) {
    if (!stream) {
      onDrained();
      continue;
    }
    const readable = stream as NodeJS.ReadableStream & {
      writableEnded?: boolean;
      writableLength?: number;
      end?: () => void;
    };
    if (readable.writableEnded && readable.writableLength === 0) {
      onDrained();
    } else {
      readable.once("drain", onDrained);
      readable.end?.();
    }
  }
  setTimeout(finalize, 250).unref();
});
