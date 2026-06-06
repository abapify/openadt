#!/usr/bin/env bun
/**
 * Manual stdio MCP smoke test — initialize + tools/list via stream framing.
 * Usage: bun run test:mcp:stdio
 */
import { spawn } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { pipeline } from "node:stream";
import { frameMcpMessage, McpFrameDecoder } from "./mcp-framing.ts";

const repoRoot = join(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "..",
  "..",
);
const launcher = join(
  repoRoot,
  "tools",
  "sap-adt-mcp-launcher",
  "src",
  "main.ts",
);
const port = 2238;

type JsonRpcMessage = {
  id?: number;
  result?: {
    serverInfo?: { name: string; version: string };
    tools?: unknown[];
  };
};

console.log("=== MCP Stdio Test ===");
console.log(
  "Starting: bun",
  launcher,
  "serve",
  "--stdio",
  "--port",
  String(port),
);

const child = spawn(
  "bun",
  [launcher, "serve", "--stdio", "--port", String(port), "--import-from=adtls"],
  { stdio: ["pipe", "pipe", "inherit"], cwd: repoRoot, windowsHide: true },
);

const decoder = new McpFrameDecoder();
pipeline(child.stdout, decoder, (err) => {
  if (err) {
    console.error("[MCP pipeline error]", err.message);
    if (!child.killed) {
      child.kill();
    }
  }
});

let messageCount = 0;

function send(obj: object): void {
  child.stdin.write(frameMcpMessage(obj));
}

decoder.on("data", (body: string) => {
  messageCount++;
  console.log(`\n=== MCP Message #${messageCount} ===`);

  let parsed: JsonRpcMessage;
  try {
    parsed = JSON.parse(body) as JsonRpcMessage;
    console.log(JSON.stringify(parsed, null, 2));
  } catch (err) {
    console.error("Failed to parse response:", err);
    return;
  }

  if (parsed.result?.serverInfo) {
    console.log("\n✓ serverInfo present:", parsed.result.serverInfo);
  }

  if (messageCount === 1) {
    send({ jsonrpc: "2.0", method: "notifications/initialized" });
    console.log("\n→ Sent notifications/initialized");
    setTimeout(() => {
      send({ jsonrpc: "2.0", id: 2, method: "tools/list" });
      console.log("→ Sent tools/list request");
    }, 500);
  }

  if (parsed.id === 2 && parsed.result?.tools) {
    console.log(`\n✅ SUCCESS: Received ${parsed.result.tools.length} tools`);
    setTimeout(() => {
      child.kill();
      process.exit(0);
    }, 500);
  }
});

decoder.on("error", (err) => {
  console.error("[MCP decode error]", err.message);
});

child.on("error", (err) => {
  console.error("[ERROR] Failed to spawn:", err.message);
  process.exit(1);
});

child.on("exit", (code) => {
  console.log("\n=== Process exited with code:", code, "===");
  process.exit(code ?? 1);
});

setTimeout(() => {
  send({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {
      protocolVersion: "2024-11-05",
      capabilities: {},
      clientInfo: { name: "test-client", version: "0.1.0" },
    },
  });
  console.log("Sent initialize request\n");
}, 2000);

setTimeout(() => {
  console.log("\n=== Timeout: killing process ===");
  console.log(
    "(SAP operations may have timed out - check for SSO/Secure Login windows)",
  );
  child.kill();
  process.exit(1);
}, 300_000);
