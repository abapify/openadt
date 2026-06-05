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
  } catch {
    return false;
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

function parseEndpoint(raw: string): McpEndpointRecord | undefined {
  try {
    const record = JSON.parse(raw) as McpEndpointRecord;
    if (
      typeof record.port === "number" &&
      typeof record.token === "string" &&
      typeof record.url === "string" &&
      typeof record.pid === "number"
    ) {
      return record;
    }
  } catch {
    /* invalid */
  }
  return undefined;
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
    const record = readEndpoint(requestedPort);
    if (!record) {
      return {
        ok: false,
        message: `No active MCP endpoint on port ${requestedPort}. Run: openadt mcp serve --port ${requestedPort}`,
      };
    }
    return { ok: true, port: requestedPort, record };
  }

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
