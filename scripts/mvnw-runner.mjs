#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const wrapper = process.platform === "win32" ? "mvnw.cmd" : "mvnw";
const command = join(repoRoot, wrapper);

const result = spawnSync(command, process.argv.slice(2), {
  cwd: repoRoot,
  stdio: "inherit",
  shell: process.platform === "win32",
  env: process.env,
});

process.exit(result.status ?? 1);
