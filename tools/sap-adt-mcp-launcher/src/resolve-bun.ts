import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

/** Bun install location (https://bun.sh). Override with OPENADT_BUN. */
export function resolveBunExecutable(): string {
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

/** Prepend ~/.bun/bin so `bun` resolves under Cursor agent CLI minimal PATH. */
export function bunBinDir(): string {
  return join(homedir(), ".bun", "bin");
}

export function prependBunToPath(env: NodeJS.ProcessEnv = process.env): void {
  const dir = bunBinDir();
  if (!existsSync(dir)) {
    return;
  }
  const sep = process.platform === "win32" ? ";" : ":";
  const pathKey =
    Object.keys(env).find((k) => k.toUpperCase() === "PATH") ?? "PATH";
  const current = env[pathKey] ?? "";
  if (
    current
      .split(sep)
      .some((entry) => entry.toLowerCase() === dir.toLowerCase())
  ) {
    return;
  }
  env[pathKey] = current ? `${dir}${sep}${current}` : dir;
}
