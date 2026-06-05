#!/usr/bin/env bun
/**
 * Write project .cursor/mcp.json from ~/.openadt/mcp/endpoints/<port>.json
 * Usage: bun scripts/sync-cursor-mcp.ts [--port 2257]
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { resolveEndpointPort } from "../tools/sap-adt-mcp-launcher/src/endpoint-store.ts";
import { cursorMcpSnippet } from "../tools/sap-adt-mcp-launcher/src/mcp.ts";

const argv = process.argv.slice(2);
let requestedPort: number | undefined;
for (let i = 0; i < argv.length; i++) {
  const arg = argv[i]!;
  if (arg === "--port" && i + 1 < argv.length) {
    requestedPort = Number.parseInt(argv[++i]!, 10);
  } else if (arg.startsWith("--port=")) {
    requestedPort = Number.parseInt(arg.slice("--port=".length), 10);
  }
}

const resolved = resolveEndpointPort(requestedPort);
if (!resolved.ok) {
  console.error(resolved.message);
  console.error(
    "Start a server first, e.g. bun run openadt -- mcp serve --port 2257 --destination S0D_200_PPLENKOV_EN",
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
    existing = JSON.parse(readFileSync(outPath, "utf8")) as {
      mcpServers?: Record<string, unknown>;
    };
  } catch {
    /* replace invalid file */
  }
}

const merged = {
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
