/**
 * Ensure a shared MCP HTTP backend is running.
 *
 * Multiple stdio agents share one `adt-lsc` + one HTTP MCP endpoint.
 * See specs/mcp-shared-backend.md.
 */
import { createServer, type Server } from "node:net";
import {
  closeSync,
  existsSync,
  mkdirSync,
  openSync,
  rmSync,
  statSync,
} from "node:fs";
import { spawn, type ChildProcess } from "node:child_process";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { sleep } from "./process.ts";
import {
  endpointFilePath,
  findHealthyEndpoint,
  type McpEndpointRecord,
} from "./endpoint-store.ts";
import { DEFAULT_MCP_PORT } from "./types.ts";
import { buildAdtLscSpawnRuntime } from "./runtime-env.ts";
import { resolveBunExecutable } from "./resolve-bun.ts";

const PORT_MIN = 1024;
const PORT_MAX = 65535;
const DEFAULT_LOCK_TIMEOUT_MS = 360_000;
const LOCK_POLL_INTERVAL_MS = 500;
const HEALTHY_PROBE_INTERVAL_MS = 250;

const here = dirname(fileURLToPath(import.meta.url));

export type EnsureSharedBackendOptions = {
  preferredPort?: number;
  /** Forwarded to the spawned daemon. */
  serveArgs?: string[];
  /** Max time to wait for the lock and for a healthy endpoint. */
  timeoutMs?: number;
  /** Custom mcp root dir (for tests). */
  mcpRootDir?: string;
  /** Custom launcher path (for tests). */
  launcherPath?: string;
};

export type EnsureSharedBackendResult = {
  port: number;
  token: string;
  url: string;
  record: McpEndpointRecord;
};

/** Result of attaching to an existing healthy endpoint (no spawn). */
export type AttachResolution =
  | { kind: "none" }
  | { kind: "ambiguous"; records: McpEndpointRecord[] }
  | { kind: "healthy"; record: McpEndpointRecord };

export function mcpRootDir(): string {
  return process.env.OPENADT_MCP_DIR ?? join(homedir(), ".openadt", "mcp");
}

export function ensureLockPath(port: number): string {
  return join(mcpRootDir(), `ensure-${port}.lock`);
}

export function isValidPort(port: number): boolean {
  return Number.isInteger(port) && port >= PORT_MIN && port <= PORT_MAX;
}

/**
 * Find an available TCP port by trying sequential binds from `start`.
 * Returns the first port that binds successfully.
 */
export function findAvailablePort(start: number): Promise<number> {
  return new Promise((resolvePort, reject) => {
    let port = Math.max(start, PORT_MIN);
    const tryNext = (): void => {
      if (port > PORT_MAX) {
        reject(new Error(`No available port starting from ${start}`));
        return;
      }
      const server: Server = createServer();
      server.unref();
      server.on("error", () => {
        port++;
        tryNext();
      });
      server.listen(port, "127.0.0.1", () => {
        const address = server.address();
        const boundPort =
          address && typeof address !== "string" ? address.port : port;
        server.close((err) => {
          if (err) {
            port = boundPort + 1;
            tryNext();
            return;
          }
          resolvePort(boundPort);
        });
      });
    };
    tryNext();
  });
}

async function withEnsureLock<T>(
  port: number,
  fn: () => Promise<T>,
  options: { timeoutMs?: number; mcpRootDir?: string } = {},
): Promise<T> {
  const root = options.mcpRootDir ?? mcpRootDir();
  const lockPath = ensureLockPathForRoot(root, port);
  mkdirSync(root, { recursive: true, mode: 0o700 });

  const timeoutMs = options.timeoutMs ?? DEFAULT_LOCK_TIMEOUT_MS;
  const deadline = Date.now() + timeoutMs;
  await acquireEnsureLock(lockPath, deadline);

  try {
    return await fn();
  } finally {
    try {
      rmSync(lockPath);
    } catch {
      /* already removed */
    }
  }
}

async function acquireEnsureLock(
  lockPath: string,
  deadline: number,
): Promise<void> {
  while (Date.now() < deadline) {
    if (tryClaimEnsureLock(lockPath)) {
      return;
    }
    await waitForLockOrStale(lockPath, deadline);
  }
  throw new Error(`Timed out waiting for ensure lock (lock: ${lockPath})`);
}

function tryClaimEnsureLock(lockPath: string): boolean {
  try {
    // O_EXCL: fail if file exists. Close immediately — the lock is the
    // file's presence (rmSync in finally, stale-check via mtime).
    closeSync(openSync(lockPath, "wx", 0o600));
    return true;
  } catch (err) {
    const code = (err as NodeJS.ErrnoException | undefined)?.code;
    if (code !== "EEXIST") {
      throw new Error(`Failed to create lock ${lockPath}: ${formatError(err)}`);
    }
    return false;
  }
}

async function waitForLockOrStale(
  lockPath: string,
  deadline: number,
): Promise<void> {
  if (isLockStale(lockPath)) {
    try {
      rmSync(lockPath);
    } catch {
      /* race: another process cleared it */
    }
    return;
  }
  await sleep(LOCK_POLL_INTERVAL_MS);
  void deadline; // deadline is enforced by the outer loop in acquireEnsureLock
}

function ensureLockPathForRoot(root: string, port: number): string {
  return join(root, `ensure-${port}.lock`);
}

function isLockStale(lockPath: string): boolean {
  // Lock is considered stale if the file is older than 2x the SAP logon timeout.
  try {
    const stat = statSync(lockPath);
    const ageMs = Date.now() - stat.mtimeMs;
    return ageMs > DEFAULT_LOCK_TIMEOUT_MS * 2;
  } catch {
    return false;
  }
}

function formatError(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

/**
 * Spawn the launcher in detached mode (HTTP-only daemon).
 * The child is not a child of the bridge process — it survives parent exit.
 */
export function spawnDetachedServe(
  port: number,
  serveArgs: string[],
  options: { launcherPath?: string; extraEnv?: NodeJS.ProcessEnv } = {},
): ChildProcess {
  const launcher = options.launcherPath ?? resolveDefaultLauncherPath();
  const runtime = buildAdtLscSpawnRuntime();
  const args = [
    launcher,
    "serve",
    "--port",
    String(port),
    "--foreground",
    ...serveArgs,
  ];
  const child = spawn(resolveBunExecutable(), args, {
    cwd: process.cwd(),
    stdio: "ignore",
    env: {
      ...runtime.env,
      ...(options.extraEnv ?? {}),
    },
    detached: true,
    windowsHide: true,
  });
  child.unref();
  return child;
}

function resolveDefaultLauncherPath(): string {
  // tools/sap-adt-mcp-launcher/src/ensure-backend.ts → src/main.ts
  return resolve(here, "main.ts");
}

/**
 * Poll the endpoint store for a healthy record on `port`.
 * Returns the record once it responds to HTTP probe, or undefined on timeout.
 */
async function waitForHealthyRecord(
  port: number,
  timeoutMs: number,
): Promise<McpEndpointRecord | undefined> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await findHealthyEndpoint(port);
    if (result.status === "one") {
      return result.record;
    }
    if (result.status === "ambiguous") {
      // Multiple records (different pids) — wait for one to die or for our
      // own record to appear. Prefer the matching port.
      const matching = result.records.find((r) => r.port === port);
      if (matching) {
        return matching;
      }
    }
    await sleep(HEALTHY_PROBE_INTERVAL_MS);
  }
  return undefined;
}

/**
 * Resolve the attach target: an existing healthy endpoint if one exists.
 * Used by `ensureSharedBackend` before deciding to spawn a new daemon.
 */
export async function resolveAttachTarget(
  preferredPort?: number,
): Promise<AttachResolution> {
  const result = await findHealthyEndpoint(preferredPort);
  if (result.status === "one") {
    return { kind: "healthy", record: result.record };
  }
  if (result.status === "ambiguous") {
    return { kind: "ambiguous", records: result.records };
  }
  if (result.status === "none") {
    return { kind: "none" };
  }
  // unhealthy: records exist but none respond → fall through to ensure.
  return { kind: "none" };
}

/**
 * Ensure a shared MCP backend is running. Returns the record to attach to.
 *
 * Algorithm (see specs/mcp-shared-backend.md §Ensure):
 * 1. Find healthy endpoint → attach.
 * 2. Else acquire lock, double-check, spawn detached daemon, wait for healthy.
 * 3. Return record.
 */
export async function ensureSharedBackend(
  options: EnsureSharedBackendOptions = {},
): Promise<EnsureSharedBackendResult> {
  // Attach scope: when undefined, resolve against the whole store (attach to
  // the single healthy endpoint regardless of port). Spawn port: the port a
  // brand-new daemon should bind when no healthy backend exists.
  const attachPort = options.preferredPort;
  const spawnPort = options.preferredPort ?? DEFAULT_MCP_PORT;
  const timeoutMs = options.timeoutMs ?? DEFAULT_LOCK_TIMEOUT_MS;

  if (attachPort !== undefined && !isValidPort(attachPort)) {
    throw new Error(
      `Invalid port: ${attachPort} (must be ${PORT_MIN}-${PORT_MAX})`,
    );
  }

  // Step 1: try to attach to an existing healthy endpoint.
  const attach = await resolveAttachTarget(attachPort);
  if (attach.kind === "healthy") {
    return attachToRecord(attach.record);
  }
  if (attach.kind === "ambiguous") {
    throwAmbiguousError(
      attach.records,
      "Run: openadt mcp list. Cannot auto-attach.",
    );
  }

  // Step 2: ensure via lock + spawn.
  return withEnsureLock(
    spawnPort,
    async () => {
      // Double-check: another process may have ensured while we waited.
      const recheck = await resolveAttachTarget(attachPort);
      if (recheck.kind === "healthy") {
        return attachToRecord(recheck.record);
      }
      if (recheck.kind === "ambiguous") {
        throwAmbiguousError(recheck.records, "Run: openadt mcp list.");
      }
      return spawnAndAwaitHealthy({
        spawnPort,
        timeoutMs,
        serveArgs: options.serveArgs ?? [],
        launcherPath: options.launcherPath,
      });
    },
    { timeoutMs, mcpRootDir: options.mcpRootDir },
  );
}

function attachToRecord(record: McpEndpointRecord): EnsureSharedBackendResult {
  return {
    port: record.port,
    token: record.token,
    url: record.url,
    record,
  };
}

function throwAmbiguousError(
  records: McpEndpointRecord[],
  suffix: string,
): never {
  const ports = records.map((r) => String(r.port)).join(", ");
  const err = new Error(
    `Multiple active MCP endpoints (ports ${ports}). ${suffix}`,
  );
  (err as NodeJS.ErrnoException).code = "OPENADT_MCP_AMBIGUOUS";
  throw err;
}

async function spawnAndAwaitHealthy(input: {
  spawnPort: number;
  timeoutMs: number;
  serveArgs: string[];
  launcherPath?: string;
}): Promise<EnsureSharedBackendResult> {
  const port = await pickPortForServe(input.spawnPort);
  const child = spawnDetachedServe(port, input.serveArgs, {
    launcherPath: input.launcherPath,
  });
  if (!child.pid) {
    const err = new Error(`Failed to spawn MCP daemon on port ${port}`);
    (err as NodeJS.ErrnoException).code = "OPENADT_MCP_SPAWN_FAILED";
    throw err;
  }
  const healthy = await waitForHealthyRecord(port, input.timeoutMs);
  if (!healthy) {
    const err = new Error(
      `MCP daemon on port ${port} did not become healthy within ${input.timeoutMs}ms`,
    );
    (err as NodeJS.ErrnoException).code = "OPENADT_MCP_TIMEOUT";
    throw err;
  }
  // Detached daemon may have been reaped; trust the store record.
  return attachToRecord(healthy);
}

/**
 * Pick the port to use for a new daemon. If preferredPort is free, use it.
 * Otherwise auto-increment up to MAX_PORT_INCREMENT_ATTEMPTS.
 */
async function pickPortForServe(preferredPort: number): Promise<number> {
  // Quick check: does the endpoint store already have a record for this port?
  if (existsSync(endpointFilePath(preferredPort))) {
    // Endpoint exists — but we already checked findHealthyEndpoint and it
    // was unhealthy. Try the next port to avoid racing the dead daemon.
    return findAvailablePort(preferredPort + 1).catch(() => preferredPort);
  }
  return findAvailablePort(preferredPort);
}
