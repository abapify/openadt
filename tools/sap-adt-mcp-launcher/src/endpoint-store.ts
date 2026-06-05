import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { killProcessByPid, sleep, waitForProcessExit } from "./process.ts";

export type McpEndpointRecord = {
  port: number;
  url: string;
  token: string;
  /** `openadt mcp serve` process pid (Bun launcher). */
  pid: number;
  adtLscPid?: number;
  startedAt: string;
  destination?: string;
  destinations: string[];
  workspace: string;
};

export function mcpEndpointsDir(): string {
  return (
    process.env.OPENADT_MCP_ENDPOINTS_DIR ??
    join(homedir(), ".openadt", "mcp", "endpoints")
  );
}

export function endpointFilePath(port: number): string {
  return join(mcpEndpointsDir(), `${port}.json`);
}

export function isProcessAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (err) {
    const code = (err as NodeJS.ErrnoException | undefined)?.code;
    // ESRCH: no such process. EPERM: process exists but we cannot signal it
    // (another user); treat as alive so we do not prematurely prune the
    // endpoint record.
    return code === "EPERM";
  }
}

export function writeEndpoint(record: McpEndpointRecord): void {
  const dir = mcpEndpointsDir();
  mkdirSync(dir, { recursive: true });
  writeFileSync(
    endpointFilePath(record.port),
    `${JSON.stringify(record, null, 2)}\n`,
    { encoding: "utf8", mode: 0o600 },
  );
}

export function removeEndpoint(port: number): void {
  try {
    unlinkSync(endpointFilePath(port));
  } catch {
    /* absent */
  }
}

function isPositiveInteger(value: unknown): value is number {
  return (
    typeof value === "number" &&
    Number.isFinite(value) &&
    Number.isInteger(value) &&
    value > 0
  );
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.length > 0;
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((d) => typeof d === "string");
}

function isValidPort(value: unknown): value is number {
  return isPositiveInteger(value) && value >= 1 && value <= 65535;
}

function isValidEndpoint(
  r: Partial<McpEndpointRecord>,
): r is McpEndpointRecord {
  return (
    isValidPort(r.port) &&
    isNonEmptyString(r.url) &&
    isNonEmptyString(r.token) &&
    isPositiveInteger(r.pid) &&
    isStringArray(r.destinations) &&
    isNonEmptyString(r.workspace) &&
    isNonEmptyString(r.startedAt)
  );
}

function isJsonObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function parseEndpoint(raw: string): McpEndpointRecord | undefined {
  let record: unknown;
  try {
    record = JSON.parse(raw);
  } catch {
    return undefined;
  }
  if (!isJsonObject(record)) {
    return undefined;
  }
  const r = record as Partial<McpEndpointRecord>;
  return isValidEndpoint(r) ? r : undefined;
}

export function readEndpoint(
  port: number,
  options?: { pruneStale?: boolean },
): McpEndpointRecord | undefined {
  const pruneStale = options?.pruneStale ?? true;
  const path = endpointFilePath(port);
  if (!existsSync(path)) {
    return undefined;
  }
  try {
    const record = parseEndpoint(readFileSync(path, "utf8"));
    if (!record) {
      if (pruneStale) {
        removeEndpoint(port);
      }
      return undefined;
    }
    if (pruneStale && !isProcessAlive(record.pid)) {
      removeEndpoint(port);
      return undefined;
    }
    return record;
  } catch {
    if (pruneStale) {
      removeEndpoint(port);
    }
    return undefined;
  }
}

export function listEndpoints(): McpEndpointRecord[] {
  const dir = mcpEndpointsDir();
  if (!existsSync(dir)) {
    return [];
  }
  const out: McpEndpointRecord[] = [];
  for (const name of readdirSync(dir)) {
    if (!name.endsWith(".json")) {
      continue;
    }
    const port = Number.parseInt(name.slice(0, -".json".length), 10);
    if (!Number.isFinite(port)) {
      continue;
    }
    const record = readEndpoint(port);
    if (record) {
      out.push(record);
    }
  }
  return out.sort((a, b) => a.port - b.port);
}

export type ResolveEndpointResult =
  | { ok: true; port: number; record: McpEndpointRecord }
  | { ok: false; message: string };

export function resolveEndpointPort(
  requestedPort?: number,
): ResolveEndpointResult {
  if (requestedPort !== undefined) {
    return resolveExplicitPort(requestedPort);
  }
  return resolveFromActive();
}

function resolveExplicitPort(port: number): ResolveEndpointResult {
  if (!isValidPort(port)) {
    return {
      ok: false,
      message: `Invalid port: ${port} (must be an integer 1-65535).`,
    };
  }
  const record = readEndpoint(port);
  if (!record) {
    return {
      ok: false,
      message: `No active MCP endpoint on port ${port}. Run: openadt mcp serve --port ${port}`,
    };
  }
  return { ok: true, port, record };
}

function resolveFromActive(): ResolveEndpointResult {
  const active = listEndpoints();
  if (active.length === 0) {
    return {
      ok: false,
      message: "No active MCP endpoints. Run: openadt mcp serve",
    };
  }
  if (active.length === 1) {
    const record = active[0]!;
    return { ok: true, port: record.port, record };
  }
  const ports = active.map((e) => String(e.port)).join(", ");
  return {
    ok: false,
    message: `Multiple MCP endpoints active (ports ${ports}). Use: openadt mcp list · openadt mcp print-config --port <port>`,
  };
}

/** Stop prior `openadt mcp serve` instances tracked in the endpoint store. */
export async function stopTrackedMcpServers(
  options: { onlyPort?: number } = {},
): Promise<number> {
  const endpoints = listEndpoints();
  const scoped = options.onlyPort
    ? endpoints.filter((ep) => ep.port === options.onlyPort)
    : endpoints;
  const pids = new Set<number>();
  for (const ep of scoped) {
    pids.add(ep.pid);
    if (ep.adtLscPid) {
      pids.add(ep.adtLscPid);
    }
  }
  let stopped = 0;
  for (const pid of pids) {
    if (!isProcessAlive(pid)) {
      continue;
    }
    killProcessByPid(pid);
    stopped++;
  }
  await Promise.all([...pids].map((pid) => waitForProcessExit(pid, 8_000)));
  if (stopped > 0) {
    await sleep(1_000);
  }
  // Remove endpoint records only after the targeted PIDs have actually
  // exited; otherwise a failed kill would leave a live MCP server with
  // no entry in the store and we'd leak both the process and its port.
  for (const ep of scoped) {
    removeEndpoint(ep.port);
  }
  return stopped;
}
