#!/usr/bin/env bun
/**
 * Stdio MCP entry for agents with a minimal PATH (Cursor agent CLI, IDE MCP).
 * Resolves Bun from ~/.bun/bin without absolute paths in .cursor/mcp.json.
 * Proxies stdin/stdout explicitly (inherit breaks some MCP clients on Windows).
 *
 * Uses shared mode (default) so multiple agents share one adt-lsc. Set
 * OPENADT_MCP_PORT to pin a specific port. See specs/mcp-shared-backend.md.
 */
import { existsSync } from "node:fs";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { dirname, join } from "node:path";
import { homedir } from "node:os";
import { fileURLToPath } from "node:url";

import { buildAdtLscSpawnRuntime } from "./runtime-env.ts";

const here = dirname(fileURLToPath(import.meta.url));

function resolveRepoRoot(): string {
  for (const key of ["OPENADT_DEV_ROOT", "OPENADT_REPO"] as const) {
    const raw = process.env[key]?.trim();
    if (raw && isRepoRoot(raw)) {
      return raw;
    }
  }
  return join(here, "..", "..", "..");
}

function isRepoRoot(candidate: string): boolean {
  if (!existsSync(candidate)) return false;
  // The value must point at an actual openadt checkout (or the launcher dir),
  // not just any existing path. Treat both layouts as valid.
  return (
    existsSync(join(candidate, "tools", "sap-adt-mcp-launcher")) ||
    existsSync(join(candidate, "package.json"))
  );
}

function resolveLauncher(): { runtime: string; launcher: string } {
  const built = findExistingBuiltLauncher();
  if (built) return { runtime: resolveBun(), launcher: built };
  const fromRepo = findExistingRepoLauncher();
  if (fromRepo) return { runtime: resolveBun(), launcher: fromRepo };
  return { runtime: resolveBun(), launcher: defaultRepoLauncher() };
}

function findExistingBuiltLauncher(): string | undefined {
  for (const ext of [".mjs", ".js"]) {
    const built = join(here, `main${ext}`);
    if (existsSync(built)) return built;
  }
  return undefined;
}

function findExistingRepoLauncher(): string | undefined {
  const repoRoot = resolveRepoRoot();
  for (const rel of [
    join("tools", "sap-adt-mcp-launcher", "dist", "main.mjs"),
    join("tools", "sap-adt-mcp-launcher", "src", "main.ts"),
  ]) {
    const candidate = join(repoRoot, rel);
    if (existsSync(candidate)) return candidate;
  }
  return undefined;
}

function defaultRepoLauncher(): string {
  const repoRoot = resolveRepoRoot();
  return join(repoRoot, "tools", "sap-adt-mcp-launcher", "src", "main.ts");
}

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
      `[openadt-mcp] Invalid OPENADT_MCP_PORT=${raw} (expected integer ${PORT_MIN}-${PORT_MAX}); using shared default.`,
    );
    return undefined;
  }
  return port;
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

// Shared mode: pass --port only when OPENADT_MCP_PORT is set. The launcher
// will auto-ensure (or attach to) a healthy shared backend.
// OPENADT_MCP_RESTART=1 forces a fresh daemon on launch (dev: pick up new code).
import { isTruthyEnv } from "./process.ts";
const explicitPort = parseExplicitPort(process.env.OPENADT_MCP_PORT?.trim());
const restartArgs = isTruthyEnv(process.env.OPENADT_MCP_RESTART)
  ? ["--restart"]
  : [];
const serveArgs = [
  "serve",
  "--stdio",
  ...(explicitPort !== undefined ? ["--port", String(explicitPort)] : []),
  ...restartArgs,
  ...process.argv.slice(2),
];

const { runtime, launcher } = resolveLauncher();
const runtimeEnv = buildAdtLscSpawnRuntime();
const child: ChildProcessWithoutNullStreams = spawn(
  runtime,
  [launcher, ...serveArgs],
  {
    cwd: process.cwd(),
    stdio: ["pipe", "pipe", "pipe"],
    env: runtimeEnv.env,
    windowsHide: true,
  },
);

child.on("error", (err) => {
  console.error(
    `[openadt-mcp] failed to spawn ${runtime}: ${err.message}\n` +
      "Install Bun (https://bun.sh) or set OPENADT_BUN to your bun executable path.",
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
