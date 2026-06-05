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
  // Number.parseInt is permissive ("2257abc" → 2257); require the whole
  // string to be a base-10 integer before accepting it.
  if (!PORT_PATTERN.test(value)) {
    return undefined;
  }
  const port = Number.parseInt(value, 10);
  if (
    !Number.isFinite(port) ||
    !Number.isInteger(port) ||
    port < 1 ||
    port > 65535
  ) {
    return undefined;
  }
  return port;
}

function parseRequestedPort(argv: string[]): number | undefined {
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (!arg) continue;
    if (arg === "--port") {
      const value = argv[i + 1];
      if (value === undefined) {
        console.error("Error: --port requires a value");
        process.exit(1);
      }
      const port = parsePort(value);
      if (port === undefined) {
        console.error(`Invalid --port: ${value} (must be an integer 1-65535)`);
        process.exit(1);
      }
      return port;
    }
    if (arg.startsWith("--port=")) {
      const port = parsePort(arg.slice("--port=".length));
      if (port === undefined) {
        console.error(
          `Invalid --port: ${arg.slice("--port=".length)} (must be an integer 1-65535)`,
        );
        process.exit(1);
      }
      return port;
    }
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
