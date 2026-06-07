#!/usr/bin/env bun
/**
 * Global dev CLI binary (Windows MCP Inspector + minimal PATH).
 * Same as dev-openadt / openadt-dev.cmd — local clone, not Scoop openadt.
 *
 *   openadt-dev.exe mcp serve --stdio --import-from adtls
 *   openadt-dev.exe fetch DEV /sap/bc/adt/...
 */
import { resolveOpenadtDevRoot } from "./resolve-openadt-dev-root.ts";

resolveOpenadtDevRoot();
await import("./nx-openadt.ts");
