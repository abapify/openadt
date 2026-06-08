#!/usr/bin/env bun
/**
 * Standalone stdio MCP binary for MCP Inspector on Windows.
 * Inspector spawns .exe directly; .cmd shims fail (Node spawn EINVAL).
 * Compile: bun build --compile src/mcp-dev-stdio-bin.ts --outfile openadt-mcp-dev.exe
 *
 * Usage: openadt-mcp-dev.exe [--port 2238] [--import-from none] …
 */
const userArgs = process.argv.slice(2);
process.argv = [
  process.argv[0]!,
  process.argv[1] ?? "openadt-mcp-dev",
  "serve",
  "--stdio",
  ...userArgs,
];
await import("./main.ts");

export {};
