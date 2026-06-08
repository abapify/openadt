/**
 * Spawn the Bun MCP launcher for dev-openadt / nx-openadt.
 * Stdio MCP clients (MCP Inspector, Cursor agents) need explicit pipe forwarding on Windows;
 * `stdio: "inherit"` through cmd/PowerShell layers breaks the JSON-RPC stream.
 */
import {
  spawn,
  spawnSync,
  type ChildProcessWithoutNullStreams,
} from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { resolveBunExecutable } from "../tools/sap-adt-mcp-launcher/src/resolve-bun.ts";

export function mcpLauncherPath(repoRoot: string): string {
  // Packaged builds (tsdown) emit `tools/sap-adt-mcp-launcher/dist/main.{mjs,js}`.
  // Dev clones (`bun run ...`) run `src/main.ts` directly. Prefer dist when present.
  const distDir = join(repoRoot, "tools", "sap-adt-mcp-launcher", "dist");
  for (const name of ["main.mjs", "main.js"]) {
    const candidate = join(distDir, name);
    if (existsSync(candidate)) return candidate;
  }
  return join(repoRoot, "tools", "sap-adt-mcp-launcher", "src", "main.ts");
}

/** Subcommands that speak MCP over stdin/stdout (not just stderr logs). */
export function mcpNeedsStdioPipe(mcpArgs: string[]): boolean {
  if (mcpArgs.includes("--stdio")) {
    return true;
  }
  return mcpArgs[0] === "bridge";
}

function mcpSpawnEnv(repoRoot: string): NodeJS.ProcessEnv {
  return { ...process.env, OPENADT_REPO: repoRoot };
}

export function pipeStdioChild(child: ChildProcessWithoutNullStreams): void {
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

  if (process.stdin.isPaused()) {
    process.stdin.resume();
  }
}

export function runMcpLauncherInherited(
  repoRoot: string,
  mcpArgs: string[],
): number {
  const launcher = mcpLauncherPath(repoRoot);
  if (!existsSync(launcher)) {
    console.error(`Missing MCP launcher: ${launcher}`);
    return 1;
  }
  const bun = resolveBunExecutable();
  const result = spawnSync(bun, [launcher, ...mcpArgs], {
    stdio: "inherit",
    cwd: repoRoot,
    env: mcpSpawnEnv(repoRoot),
  });
  return result.status ?? 1;
}

export function runMcpLauncherPiped(
  repoRoot: string,
  mcpArgs: string[],
): Promise<number> {
  const launcher = mcpLauncherPath(repoRoot);
  if (!existsSync(launcher)) {
    console.error(`Missing MCP launcher: ${launcher}`);
    return Promise.resolve(1);
  }
  const bun = resolveBunExecutable();
  return new Promise((resolve) => {
    const child = spawn(bun, [launcher, ...mcpArgs], {
      stdio: ["pipe", "pipe", "pipe"],
      cwd: repoRoot,
      env: mcpSpawnEnv(repoRoot),
      windowsHide: true,
    });
    child.on("error", (err) => {
      console.error(`[openadt-dev] failed to spawn ${bun}: ${err.message}`);
      resolve(1);
    });
    child.on("exit", (code, signal) => {
      resolve(signal ? 1 : (code ?? 1));
    });
    pipeStdioChild(child);
  });
}
