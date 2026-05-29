#!/usr/bin/env bun
/**
 * Minimal stdio MCP stub: tools delegate to `openadt fetch` and `openadt adt`.
 * See specs/mcp.md.
 */
import { spawnSync } from "node:child_process";

function write(msg: object) {
  const body = JSON.stringify(msg);
  process.stdout.write(`Content-Length: ${Buffer.byteLength(body, "utf8")}\r\n\r\n${body}`);
}

function openadtBin(): string {
  if (process.env.OPENADT_BIN) {
    return process.env.OPENADT_BIN;
  }
  return process.platform === "win32" ? "openadt.cmd" : "openadt";
}

function handleFetch(args: { system?: string; path?: string }) {
  const system = args.system ?? "DEV";
  const path = args.path ?? "/sap/bc/adt/discovery";
  const result = spawnSync(openadtBin(), ["fetch", system, path, "--pretty"], {
    encoding: "utf8",
  });
  const text = (result.stdout ?? "") + (result.stderr ?? "");
  return {
    content: [{ type: "text", text: text || `exit ${result.status}` }],
    isError: (result.status ?? 1) !== 0,
  };
}

function handleDiscover(args: { system?: string; format?: string }) {
  const system = args.system ?? "DEV";
  const format = args.format ?? "text";
  const result = spawnSync(
    openadtBin(),
    ["adt", "discover", system, "--format", format],
    {
      encoding: "utf8",
    },
  );
  const text = (result.stdout ?? "") + (result.stderr ?? "");
  return {
    content: [{ type: "text", text: text || `exit ${result.status}` }],
    isError: (result.status ?? 1) !== 0,
  };
}

function handleLogon(args: { system?: string; format?: string }) {
  const system = args.system ?? "DEV";
  const format = args.format ?? "text";
  const result = spawnSync(
    openadtBin(),
    ["adt", "logon", system, "--format", format],
    {
      encoding: "utf8",
    },
  );
  const text = (result.stdout ?? "") + (result.stderr ?? "");
  return {
    content: [{ type: "text", text: text || `exit ${result.status}` }],
    isError: (result.status ?? 1) !== 0,
  };
}

const handlers: Record<string, (args: Record<string, string>) => object> = {
  adt_fetch: (a) => handleFetch(a),
  adt_discover: (a) => handleDiscover(a),
  adt_logon: (a) => handleLogon(a),
};

const tools = [
  {
    name: "adt_fetch",
    description: "Fetch one ADT resource via openadt fetch",
    inputSchema: {
      type: "object",
      properties: {
        system: { type: "string", description: "Config alias (e.g. DEV)" },
        path: {
          type: "string",
          description: "ADT path e.g. /sap/bc/adt/discovery",
        },
      },
      required: ["system", "path"],
    },
  },
  {
    name: "adt_discover",
    description: "Run openadt adt discover (SDK IAdtDiscovery)",
    inputSchema: {
      type: "object",
      properties: {
        system: { type: "string", description: "Config alias (e.g. DEV)" },
        format: {
          type: "string",
          description: "text or json",
          enum: ["text", "json"],
        },
      },
      required: ["system"],
    },
  },
  {
    name: "adt_logon",
    description: "Run openadt adt logon (SDK IAdtLogonService)",
    inputSchema: {
      type: "object",
      properties: {
        system: { type: "string", description: "Config alias (e.g. DEV)" },
        format: {
          type: "string",
          description: "text or json",
          enum: ["text", "json"],
        },
      },
      required: ["system"],
    },
  },
];

let buffer = Buffer.alloc(0);
process.stdin.on("data", (chunk) => {
  buffer = Buffer.concat([buffer, chunk as Buffer]);
  while (true) {
    const headerEnd = buffer.indexOf("\r\n\r\n");
    if (headerEnd < 0) break;
    const headers = buffer.subarray(0, headerEnd).toString("utf8");
    const match = /Content-Length:\s*(\d+)/i.exec(headers);
    if (!match) break;
    const len = Number(match[1]);
    const bodyStart = headerEnd + 4;
    if (buffer.length < bodyStart + len) break;
    const body = buffer.subarray(bodyStart, bodyStart + len).toString("utf8");
    buffer = buffer.subarray(bodyStart + len);
    const msg = JSON.parse(body) as {
      jsonrpc?: string;
      id?: number | string;
      method?: string;
      params?: { name?: string; arguments?: Record<string, string> };
    };
    if (msg.method === "initialize") {
      write({
        jsonrpc: "2.0",
        id: msg.id,
        result: {
          protocolVersion: "2024-11-05",
          capabilities: { tools: {} },
          serverInfo: { name: "openadt-mcp-bridge", version: "0.1.0" },
        },
      });
      continue;
    }
    if (msg.method === "tools/list") {
      write({
        jsonrpc: "2.0",
        id: msg.id,
        result: { tools },
      });
      continue;
    }
    if (msg.method === "tools/call") {
      const name = msg.params?.name ?? "";
      const fn = handlers[name];
      const result = fn
        ? fn(msg.params?.arguments ?? {})
        : {
          content: [{ type: "text", text: `Unknown tool: ${name}` }],
          isError: true,
        };
      write({ jsonrpc: "2.0", id: msg.id, result });
      continue;
    }
    if (msg.id !== undefined) {
      write({ jsonrpc: "2.0", id: msg.id, result: {} });
    }
  }
});
