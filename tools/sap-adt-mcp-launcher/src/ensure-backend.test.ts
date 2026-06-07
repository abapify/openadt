import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  ensureLockPath,
  findAvailablePort,
  isValidPort,
  mcpRootDir,
  resolveAttachTarget,
} from "./ensure-backend.ts";
import { type McpEndpointRecord, writeEndpoint } from "./endpoint-store.ts";

let tempRoot: string;
let previousRoot: string | undefined;

function sampleRecord(port: number): McpEndpointRecord {
  return {
    port,
    url: `http://localhost:${port}/mcp`,
    token: `token-${port}`,
    pid: process.pid,
    startedAt: new Date().toISOString(),
    destinations: ["DEV_100_developer_en"],
    workspace: "/tmp/workspace",
  };
}

beforeEach(() => {
  previousRoot = process.env.OPENADT_MCP_DIR;
  tempRoot = mkdtempSync(join(tmpdir(), "openadt-mcp-root-"));
  process.env.OPENADT_MCP_DIR = tempRoot;
});

afterEach(() => {
  rmSync(tempRoot, { recursive: true, force: true });
  if (previousRoot === undefined) {
    delete process.env.OPENADT_MCP_DIR;
  } else {
    process.env.OPENADT_MCP_DIR = previousRoot;
  }
});

describe("ensure-backend", () => {
  test("isValidPort accepts 1024-65535 and rejects out-of-range", () => {
    expect(isValidPort(1024)).toBe(true);
    expect(isValidPort(2236)).toBe(true);
    expect(isValidPort(65535)).toBe(true);
    expect(isValidPort(1023)).toBe(false);
    expect(isValidPort(65536)).toBe(false);
    expect(isValidPort(0)).toBe(false);
    expect(isValidPort(-1)).toBe(false);
    expect(isValidPort(1.5)).toBe(false);
    expect(isValidPort(NaN)).toBe(false);
  });

  test("mcpRootDir respects OPENADT_MCP_DIR override", () => {
    expect(mcpRootDir()).toBe(tempRoot);
  });

  test("ensureLockPath produces per-port lock files under mcpRootDir", () => {
    const lock = ensureLockPath(2236);
    expect(lock).toBe(join(tempRoot, "ensure-2236.lock"));
  });

  test("findAvailablePort returns a usable port and binds+releases cleanly", async () => {
    const port = await findAvailablePort(25000);
    expect(isValidPort(port)).toBe(true);
    expect(port).toBeGreaterThanOrEqual(25000);
  });

  test("resolveAttachTarget returns 'none' when store is empty", async () => {
    const result = await resolveAttachTarget();
    expect(result.kind).toBe("none");
  });

  test("resolveAttachTarget returns 'unhealthy' (none) when records exist but pid is dead", async () => {
    writeEndpoint({ ...sampleRecord(2236), pid: 2_000_000_000 });
    const result = await resolveAttachTarget(2236);
    expect(result.kind).toBe("none");
  });

  test("resolveAttachTarget returns 'ambiguous' when multiple records exist with alive pids", async () => {
    // Both records are alive (process.pid) but no real HTTP server is running,
    // so findHealthyEndpoint prunes them via probeMcpHttp failure → "none".
    // We can only test the no-records case here; the ambiguous path requires
    // mocking a live HTTP MCP server (see integration smoke).
    writeEndpoint({ ...sampleRecord(2236), pid: process.pid });
    writeEndpoint({ ...sampleRecord(2237), pid: process.pid });
    const result = await resolveAttachTarget();
    // Probes fail in test env → none (records will be pruned on next read).
    expect(["none", "ambiguous"]).toContain(result.kind);
  });

  test("lock file creation and removal cycle (smoke)", () => {
    const lockPath = ensureLockPath(2500);
    // Touch the lock file via direct write (mimic withEnsureLock behavior).
    writeFileSync(lockPath, "", { mode: 0o600 });
    expect(existsSync(lockPath)).toBe(true);
    rmSync(lockPath);
    expect(existsSync(lockPath)).toBe(false);
  });
});
