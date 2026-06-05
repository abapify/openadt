import { mkdirSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { type ChildProcess, spawn } from "node:child_process";
import { dirname } from "node:path";
import { PID_FILE } from "./config.ts";
import type { AdtLsInstall } from "./types.ts";
import {
  buildAdtLscSpawnRuntime,
  type AdtLscSpawnRuntime,
} from "./runtime-env.ts";

export type SpawnAdtLscOptions = {
  onStderrLine?: (line: string) => void;
  runtime?: AdtLscSpawnRuntime;
};

export function ensureWorkspaceDir(workspace: string): void {
  mkdirSync(workspace, { recursive: true });
}

export function spawnAdtLsc(
  install: AdtLsInstall,
  workspace: string,
  pipeName: string,
  options: SpawnAdtLscOptions = {},
): ChildProcess {
  ensureWorkspaceDir(workspace);
  const runtime = options.runtime ?? buildAdtLscSpawnRuntime();
  const args = [
    `--pipe=${pipeName}`,
    "-consoleLog",
    ...runtime.jvmArgs,
    `-Djco.trace_path=${workspace}`,
    "-data",
    workspace,
  ];
  const child = spawn(install.adtLscPath, args, {
    cwd: dirname(install.adtLscPath),
    env: runtime.env,
    stdio: options.onStderrLine ? ["ignore", "ignore", "pipe"] : "ignore",
    /** Must be false on Windows so Secure Login / SSO dialogs can appear. */
    windowsHide: false,
  });
  if (child.stderr && options.onStderrLine) {
    let pending = "";
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => {
      pending += chunk;
      const parts = pending.split(/\r?\n/);
      pending = parts.pop() ?? "";
      for (const line of parts) {
        if (line.trim()) {
          options.onStderrLine!(line);
        }
      }
    });
  }
  return child;
}

export function writePidFile(pid: number): void {
  mkdirSync(dirname(PID_FILE), { recursive: true });
  writeFileSync(PID_FILE, `${pid}\n`, "utf8");
}

export function readPidFile(): number | undefined {
  try {
    const raw = readFileSync(PID_FILE, "utf8").trim();
    const pid = Number.parseInt(raw, 10);
    return Number.isFinite(pid) ? pid : undefined;
  } catch {
    return undefined;
  }
}

export function clearPidFile(): void {
  try {
    unlinkSync(PID_FILE);
  } catch {
    /* absent */
  }
}

export function killProcessTree(child: ChildProcess | undefined): void {
  if (!child?.pid) {
    return;
  }
  try {
    if (process.platform === "win32") {
      spawn("taskkill", ["/T", "/F", "/PID", String(child.pid)], {
        stdio: "ignore",
        windowsHide: true,
      });
    } else {
      child.kill("SIGTERM");
    }
  } catch {
    /* already exited */
  }
}
