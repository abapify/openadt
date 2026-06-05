import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  listEndpoints,
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
});
