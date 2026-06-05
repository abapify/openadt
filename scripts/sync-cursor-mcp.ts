#!/usr/bin/env bun
/**
 * Write project .cursor/mcp.json from ~/.openadt/mcp/endpoints/<port>.json
 * Usage: bun scripts/sync-cursor-mcp.ts [--port 2257]
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { resolveEndpointPort } from "../tools/sap-adt-mcp-launcher/src/endpoint-store.ts";
import { cursorMcpSnippet } from "../tools/sap-adt-mcp-launcher/src/mcp.ts";

const PORT_PATTERN = /^[0-9]+$/;

function parsePort(value: string): number | undefined {
  if (!PORT_PATTERN.test(value)) {
    return undefined;
  }
  const port = Number.parseInt(value, 10);
  if (port < 1 || port > 65535) {
    return undefined;
  }
  return port;
}

function fail(message: string): never {
  console.error(message);
  process.exit(1);
}

function requirePort(value: string): number {
  const port = parsePort(value);
  if (port === undefined) {
    fail(`Invalid --port: ${value} (must be an integer 1-65535)`);
  }
  return port;
}

type PortArgResult = { value: string } | { missing: true } | null;

function readPortArg(argv: string[], i: number): PortArgResult {
  const arg = argv[i];
  if (arg === "--port") {
    const next = argv[i + 1];
    if (next === undefined) return { missing: true };
    return { value: next };
  }
  if (arg !== undefined && arg.startsWith("--port=")) {
    return { value: arg.slice("--port=".length) };
  }
  return null;
}

function parseRequestedPort(argv: string[]): number | undefined {
  for (let i = 0; i < argv.length; i++) {
    const r = readPortArg(argv, i);
    if (r === null) continue;
    if ("missing" in r) fail("Error: --port requires a value");
    return requirePort(r.value);
  }
  return undefined;
}

const requestedPort = parseRequestedPort(process.argv.slice(2));

const resolved = resolveEndpointPort(requestedPort);
if (!resolved.ok) {
  console.error(resolved.message);
  console.error(
    "Start a server first, e.g. bun run openadt -- mcp serve --port 2257 --destination DEV_100_developer_en",
  );
  process.exit(1);
}

const { port, record } = resolved;
const snippet = cursorMcpSnippet(port, record.token) as {
  mcpServers: Record<string, unknown>;
};

const cursorDir = join(process.cwd(), ".cursor");
const outPath = join(cursorDir, "mcp.json");
mkdirSync(cursorDir, { recursive: true });

let existing: { mcpServers?: Record<string, unknown> } = { mcpServers: {} };
if (existsSync(outPath)) {
  try {
    const parsed: unknown = JSON.parse(readFileSync(outPath, "utf8"));
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      existing = parsed as { mcpServers?: Record<string, unknown> };
    }
  } catch {
    /* replace invalid file */
  }
}

const merged = {
  ...existing,
  mcpServers: {
    ...(existing.mcpServers ?? {}),
    ...snippet.mcpServers,
  },
};

writeFileSync(outPath, `${JSON.stringify(merged, null, 2)}\n`, {
  encoding: "utf8",
  mode: 0o600,
});

console.log(`Wrote ${outPath}`);
console.log(`sap-adt → ${record.url} (port ${port})`);
