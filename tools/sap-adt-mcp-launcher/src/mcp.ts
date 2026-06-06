import { v4 as uuidv4 } from "uuid";
import { sleep } from "./process.ts";
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

/** Consume response body so poll probes do not leak open HTTP/SSE connections. */
export async function drainHttpResponse(res: Response): Promise<void> {
  try {
    await res.arrayBuffer();
  } catch {
    /* ignore drain errors — status already available */
  }
}

export async function probeMcpHttp(
  port: number,
  token?: string,
): Promise<boolean> {
  const headers: Record<string, string> = {
    "User-Agent": "openadt-mcp-client",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  try {
    // OPTIONS (or any HTTP response) proves the listener is bound without
    // creating MCP sessions. Do not POST initialize here — unread/streaming
    // probe bodies and extra sessions stall later tools/list on stdio.
    const res = await fetch(mcpUrl(port), {
      method: "OPTIONS",
      headers,
      signal: AbortSignal.timeout(10_000),
    });
    await drainHttpResponse(res);
    return true;
  } catch {
    return false;
  }
}

/** Poll until MCP HTTP accepts requests (startMCPServer may return before bind). */
export async function waitForMcpHttp(
  port: number,
  token: string,
  options?: { timeoutMs?: number; intervalMs?: number },
): Promise<boolean> {
  const timeoutMs = options?.timeoutMs ?? 30_000;
  const intervalMs = options?.intervalMs ?? 250;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await probeMcpHttp(port, token)) {
      return true;
    }
    await sleep(intervalMs);
  }
  return false;
}

/** Agent-neutral HTTP MCP client connection (url + Authorization header). */
export function mcpHttpClientConfig(port: number, token: string): object {
  return {
    url: mcpUrl(port),
    headers: {
      Authorization: `Bearer ${token}`,
      "User-Agent": "openadt-mcp-client",
    },
  };
}

export function redactToken(token: string): string {
  if (token.length <= 8) {
    return "***";
  }
  return `${token.slice(0, 4)}…${token.slice(-4)}`;
}
