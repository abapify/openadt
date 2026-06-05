import { v4 as uuidv4 } from "uuid";
import { ParameterStructures, type MessageConnection } from "./rpc.ts";
import {
  LSP_METHOD_MCP_SET_DESTINATION,
  LSP_METHOD_MCP_START,
  LSP_METHOD_MCP_STOP,
  type McpStartParams,
  type McpStartResult,
} from "./types.ts";

export function generateMcpToken(): string {
  return uuidv4();
}

export async function startMcpServer(
  connection: MessageConnection,
  params: McpStartParams,
): Promise<McpStartResult> {
  const result = (await connection.sendRequest(
    LSP_METHOD_MCP_START,
    ParameterStructures.byName,
    params,
  )) as McpStartResult | undefined;
  if (!result?.port || !result?.token) {
    throw new Error(
      `adtLs/mcp/startMCPServer returned invalid payload: ${summarizeMcpStart(result)}`,
    );
  }
  return result;
}

function summarizeMcpStart(result: unknown): string {
  if (!result || typeof result !== "object") {
    return typeof result;
  }
  const obj = result as Record<string, unknown>;
  const keys = Object.keys(obj);
  return `{${keys.map((k) => `${k}:<${typeof obj[k]}>`).join(", ")}}`;
}

export async function stopMcpServer(
  connection: MessageConnection,
): Promise<void> {
  await connection.sendRequest(LSP_METHOD_MCP_STOP);
}

export async function setMcpDestination(
  connection: MessageConnection,
  destinationId: string,
): Promise<void> {
  await connection.sendRequest(
    LSP_METHOD_MCP_SET_DESTINATION,
    ParameterStructures.byName,
    { destinationId },
  );
}

export function mcpUrl(port: number): string {
  return `http://localhost:${port}/mcp`;
}

export function isPortInUseMessage(message: string): boolean {
  return /port.*already in use/i.test(message);
}

export async function probeMcpHttp(
  port: number,
  token?: string,
): Promise<boolean> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "User-Agent": "openadt-mcp-client",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const body = JSON.stringify({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {
      protocolVersion: "2024-11-05",
      capabilities: {},
      clientInfo: { name: "openadt-mcp-status", version: "0.1.0" },
    },
  });

  try {
    const res = await fetch(mcpUrl(port), {
      method: "POST",
      headers,
      body,
      signal: AbortSignal.timeout(10_000),
    });
    return res.ok || res.status === 401;
  } catch {
    return false;
  }
}

export function cursorMcpSnippet(port: number, token: string): object {
  return {
    mcpServers: {
      "sap-adt": {
        url: mcpUrl(port),
        headers: {
          Authorization: `Bearer ${token}`,
          "User-Agent": "openadt-mcp-client",
        },
      },
    },
  };
}

export function redactToken(token: string): string {
  if (token.length <= 8) {
    return "***";
  }
  return `${token.slice(0, 4)}…${token.slice(-4)}`;
}
