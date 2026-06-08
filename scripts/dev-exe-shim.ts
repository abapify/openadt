#!/usr/bin/env bun
/**
 * Thin re-exec shim compiled to ~/.local/bin/openadt-dev.exe and
 * openadt-mcp-dev.exe (a real .exe is required because Node / MCP Inspector
 * cannot spawn a .cmd on Windows).
 *
 * Crucially this shim contains NO product logic: it resolves the clone root and
 * `bun`, then spawns `bun <root>/<source-entry>` so edits to the launcher /
 * MCP tools are picked up live — without recompiling the .exe. (The previous
 * approach compiled the logic itself, freezing it into the binary.)
 *
 * Which source entry to run is decided by the .exe's own name:
 *   openadt-dev(.exe)     → scripts/openadt-dev-bin.ts            (the dev CLI)
 *   openadt-mcp-dev(.exe) → tools/sap-adt-mcp-launcher/src/mcp-dev-stdio-bin.ts
 */
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { basename, join } from "node:path";
import { resolveOpenadtDevRoot } from "./resolve-openadt-dev-root.ts";

function resolveBun(): string {
  if (process.env.OPENADT_BUN?.trim()) {
    return process.env.OPENADT_BUN.trim();
  }
  const exe = process.platform === "win32" ? "bun.exe" : "bun";
  const installed = join(homedir(), ".bun", "bin", exe);
  return existsSync(installed) ? installed : exe;
}

const root = resolveOpenadtDevRoot();
const isMcp = basename(process.execPath).toLowerCase().includes("mcp");
const target = isMcp
  ? join(root, "tools", "sap-adt-mcp-launcher", "src", "mcp-dev-stdio-bin.ts")
  : join(root, "scripts", "openadt-dev-bin.ts");

// MCP transport needs explicit stdio pipes (inherit breaks some MCP clients on
// Windows — see mcp-stdio-entry.ts); the interactive CLI wants inherit so logon
// prompts / TTY work.
const child = spawn(resolveBun(), [target, ...process.argv.slice(2)], {
  cwd: root,
  stdio: isMcp ? ["pipe", "pipe", "pipe"] : "inherit",
  env: process.env,
  windowsHide: true,
});

if (isMcp) {
  process.stdin.pipe(child.stdin!);
  child.stdout!.pipe(process.stdout);
  child.stderr!.pipe(process.stderr);
  process.stdin.on("end", () => child.stdin!.end());
  process.stdin.on("error", () => child.stdin!.destroy());
}

child.on("error", (err) => {
  console.error(`openadt-dev shim: failed to spawn bun: ${err.message}`);
  process.exit(1);
});
child.on("exit", (code, signal) => process.exit(signal ? 1 : (code ?? 0)));
