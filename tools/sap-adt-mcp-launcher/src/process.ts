import {
  existsSync,
  mkdirSync,
  readFileSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { type ChildProcess, spawn } from "node:child_process";
import { dirname, join } from "node:path";
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
  killProcessByPid(child.pid);
}

/** Resolve taskkill without relying on PATH (agent CLI uses minimal PATH). */
export function windowsTaskkillPath(): string | undefined {
  const root = process.env.SystemRoot ?? process.env.WINDIR ?? "C:\\Windows";
  const candidate = join(root, "System32", "taskkill.exe");
  return existsSync(candidate) ? candidate : undefined;
}

export function killProcessByPid(pid: number): void {
  if (!Number.isFinite(pid) || pid <= 0) {
    return;
  }
  try {
    if (process.platform === "win32") {
      const taskkill = windowsTaskkillPath();
      if (!taskkill) {
        process.kill(pid, "SIGTERM");
        return;
      }
      const killer = spawn(taskkill, ["/T", "/F", "/PID", String(pid)], {
        stdio: "ignore",
        windowsHide: true,
      });
      killer.on("error", () => {
        /* taskkill unavailable / not on PATH; ignore — process.kill is the fallback */
      });
      killer.unref();
    } else {
      process.kill(pid, "SIGTERM");
    }
  } catch {
    /* already exited or taskkill unavailable */
  }
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function waitForProcessExit(
  pid: number,
  timeoutMs: number,
): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      process.kill(pid, 0);
    } catch (err) {
      const code = (err as NodeJS.ErrnoException | undefined)?.code;
      if (code === "ESRCH") {
        return true;
      }
      if (code === "EPERM") {
        return false;
      }
      return true;
    }
    await sleep(100);
  }
  try {
    process.kill(pid, 0);
    return false;
  } catch {
    return true;
  }
}
