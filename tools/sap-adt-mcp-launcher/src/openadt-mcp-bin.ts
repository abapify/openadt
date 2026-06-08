#!/usr/bin/env bun
/**
 * Compiled-Bun entry point for the published `openadt-mcp` CLI.
 * Compile: bun build --compile src/openadt-mcp-bin.ts --outfile openadt-mcp[.exe]
 *
 * Difference from mcp-dev-stdio-bin.ts: this is the full CLI (serve, list,
 * status, stop, bridge, print-config). The dev shim injects "serve --stdio"
 * for the MCP Inspector; the published binary does not.
 */
process.argv = [
  process.argv[0]!,
  process.argv[1] ?? "openadt-mcp",
  ...process.argv.slice(2),
];
await import("./main.ts");

export {};
