import {
  setMcpDestination,
  startMcpServer,
  stopMcpServer,
} from "@marianfoo/adt-ls";
import { v4 as uuidv4 } from "uuid";
import { sleep } from "./process.ts";

/**
 * The adt-lsc MCP control-plane calls (`adtLs/mcp/{startMCPServer,stopMCPServer,setDestination}`)
 * are delegated to @marianfoo/adt-ls — the shared SDK over headless adt-ls — and re-exported so
 * the launcher's call sites stay stable. OpenADT keeps the HTTP-side helpers below: it proxies
 * the resulting HTTP MCP endpoint to agents itself (the SDK only drives the control plane).
 */
export { setMcpDestination, startMcpServer, stopMcpServer };

export function generateMcpToken(): string {
  return uuidv4();
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
