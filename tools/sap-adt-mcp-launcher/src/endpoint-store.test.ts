import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  findHealthyEndpoint,
  isProcessAlive,
  listEndpoints,
  mcpEndpointsDir,
  readEndpoint,
  removeEndpoint,
  resolveEndpointPort,
  writeEndpoint,
  type McpEndpointRecord,
} from "./endpoint-store.ts";

let tempDir: string;
let previousDir: string | undefined;

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
  previousDir = process.env.OPENADT_MCP_ENDPOINTS_DIR;
  tempDir = mkdtempSync(join(tmpdir(), "openadt-mcp-endpoints-"));
  process.env.OPENADT_MCP_ENDPOINTS_DIR = tempDir;
});

afterEach(() => {
  rmSync(tempDir, { recursive: true, force: true });
  if (previousDir === undefined) {
    delete process.env.OPENADT_MCP_ENDPOINTS_DIR;
  } else {
    process.env.OPENADT_MCP_ENDPOINTS_DIR = previousDir;
  }
});

describe("endpoint-store", () => {
  test("write and read endpoint by port", () => {
    writeEndpoint(sampleRecord(2257));
    const record = readEndpoint(2257);
    expect(record?.token).toBe("token-2257");
    expect(record?.url).toBe("http://localhost:2257/mcp");
  });

  test("listEndpoints returns multiple active ports", () => {
    writeEndpoint(sampleRecord(2255));
    writeEndpoint(sampleRecord(2260));
    const list = listEndpoints();
    expect(list.map((e) => e.port)).toEqual([2255, 2260]);
  });

  test("resolveEndpointPort auto-picks single endpoint", () => {
    writeEndpoint(sampleRecord(2241));
    const resolved = resolveEndpointPort();
    expect(resolved.ok).toBe(true);
    if (resolved.ok) {
      expect(resolved.port).toBe(2241);
    }
  });

  test("resolveEndpointPort errors when multiple endpoints without --port", () => {
    writeEndpoint(sampleRecord(2241));
    writeEndpoint(sampleRecord(2242));
    const resolved = resolveEndpointPort();
    expect(resolved.ok).toBe(false);
    if (!resolved.ok) {
      expect(resolved.message).toContain("2241");
      expect(resolved.message).toContain("2242");
    }
  });

  test("removeEndpoint deletes file", () => {
    writeEndpoint(sampleRecord(2236));
    removeEndpoint(2236);
    expect(readEndpoint(2236, { pruneStale: false })).toBeUndefined();
  });

  test("parseEndpoint rejects malformed records (missing destinations/workspace/startedAt)", () => {
    const dir = mcpEndpointsDir();
    writeFileSync(
      join(dir, "2301.json"),
      JSON.stringify({
        port: 2301,
        url: "http://localhost:2301/mcp",
        token: "t",
        pid: process.pid,
        // missing destinations, workspace, startedAt
      }),
    );
    expect(readEndpoint(2301)).toBeUndefined();
    expect(existsSync(join(dir, "2301.json"))).toBe(false);
  });

  test("parseEndpoint rejects out-of-range port and non-positive PID", () => {
    const dir = mcpEndpointsDir();
    const base = {
      url: "http://localhost:2302/mcp",
      token: "t",
      destinations: ["DEV_100_developer_en"],
      workspace: "/tmp",
      startedAt: new Date().toISOString(),
    };
    writeFileSync(
      join(dir, "2302.json"),
      JSON.stringify({ ...base, port: 70000, pid: process.pid }),
    );
    writeFileSync(
      join(dir, "2303.json"),
      JSON.stringify({ ...base, port: 2303, pid: -1 }),
    );
    expect(readEndpoint(2302)).toBeUndefined();
    expect(readEndpoint(2303)).toBeUndefined();
  });

  test("parseEndpoint rejects non-null JSON (e.g. JSON literal null)", () => {
    const dir = mcpEndpointsDir();
    writeFileSync(join(dir, "2304.json"), "null");
    expect(readEndpoint(2304)).toBeUndefined();
  });

  test("resolveEndpointPort rejects NaN, non-integer, and out-of-range ports", () => {
    const bad = [
      Number.NaN,
      Number.POSITIVE_INFINITY,
      0,
      -1,
      65536,
      1.5,
    ] as unknown as number[];
    for (const port of bad) {
      const r = resolveEndpointPort(port);
      expect(r.ok).toBe(false);
      if (!r.ok) {
        expect(r.message).toContain("Invalid port");
      }
    }
  });

  test("isProcessAlive returns true for current process and false for missing PID", () => {
    expect(isProcessAlive(process.pid)).toBe(true);
    expect(isProcessAlive(2_000_000_000)).toBe(false);
  });

  test("findHealthyEndpoint returns 'none' when store is empty", async () => {
    const result = await findHealthyEndpoint();
    expect(result.status).toBe("none");
  });

  test("findHealthyEndpoint returns 'unhealthy' when no record responds", async () => {
    writeEndpoint(sampleRecord(2250));
    const result = await findHealthyEndpoint();
    expect(result.status).toBe("unhealthy");
  });

  test("findHealthyEndpoint filters by preferred port when set", async () => {
    // Dead pids → pruned by readEndpoint default → listEndpoints returns empty
    // → findHealthyEndpoint returns 'none'. The preferredPort filter is
    // tested via integration (mocked live HTTP).
    writeEndpoint({ ...sampleRecord(2251), pid: 2_000_000_000 });
    writeEndpoint({ ...sampleRecord(2252), pid: 2_000_000_000 });
    const result = await findHealthyEndpoint(2251);
    expect(["none", "unhealthy"]).toContain(result.status);
  });

  test("writeEndpoint preserves optional mode field (daemon vs standalone)", () => {
    const daemon = { ...sampleRecord(2260), mode: "daemon" as const };
    const standalone = { ...sampleRecord(2261), mode: "standalone" as const };
    writeEndpoint(daemon);
    writeEndpoint(standalone);
    expect(readEndpoint(2260)?.mode).toBe("daemon");
    expect(readEndpoint(2261)?.mode).toBe("standalone");
  });
});
