/**
 * Tiny localhost HTTP endpoint the shared daemon exposes so the (separate) stdio
 * bridge can run LSP-backed read tools. The daemon owns the `adt-lsc` LSP pipe;
 * the bridge does not — this is the one channel between them (the SAP MCP HTTP
 * server cannot be extended with our tools). Bearer-protected, 127.0.0.1 only.
 *
 * Routes:
 *   POST /read-object { destination, objectName, objectType? } → ReadObjectResult
 *   POST /search      { destination, pattern, types?, maxResults? } → { references }
 *
 * See docs/plans/2026-06-07-mcp-read-abap-object.md (Option A).
 */
import { findAvailablePort } from "./ensure-backend.ts";
import { generateMcpToken } from "./mcp.ts";
import { ReadTimeoutError, type ReadObjectBackend } from "./read-object.ts";

const READ_AUX_PORT_BASE = 39_000;

export type ReadAuxServer = {
  url: string;
  token: string;
  stop(): void;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function readObjectArgs(body: Record<string, unknown>) {
  return {
    destination: String(body.destination ?? ""),
    objectName:
      typeof body.objectName === "string" ? body.objectName : undefined,
    objectType:
      typeof body.objectType === "string" ? body.objectType : undefined,
    uri: typeof body.uri === "string" ? body.uri : undefined,
  };
}

function searchArgs(body: Record<string, unknown>) {
  return {
    destination: String(body.destination ?? ""),
    pattern: String(body.pattern ?? ""),
    types: Array.isArray(body.types)
      ? body.types.filter((t): t is string => typeof t === "string")
      : undefined,
    maxResults:
      typeof body.maxResults === "number" ? body.maxResults : undefined,
  };
}

/** Start the daemon read endpoint backed by an LSP-backed `ReadObjectBackend`. */
export async function startReadAuxServer(
  backend: ReadObjectBackend,
  options: { token?: string; startPort?: number } = {},
): Promise<ReadAuxServer> {
  const token = options.token ?? generateMcpToken();
  const port = await findAvailablePort(options.startPort ?? READ_AUX_PORT_BASE);

  const server = Bun.serve({
    port,
    hostname: "127.0.0.1",
    async fetch(req): Promise<Response> {
      if ((req.headers.get("authorization") ?? "") !== `Bearer ${token}`) {
        return json({ error: "unauthorized" }, 401);
      }
      if (req.method !== "POST") {
        return json({ error: "method not allowed" }, 405);
      }
      let body: Record<string, unknown>;
      try {
        body = (await req.json()) as Record<string, unknown>;
      } catch {
        return json({ error: "invalid json body" }, 400);
      }
      const path = new URL(req.url).pathname;
      try {
        if (path === "/read-object") {
          return json(await backend.readObject(readObjectArgs(body)));
        }
        if (path === "/search") {
          const references = await backend.search(searchArgs(body));
          return json({ references });
        }
        return json({ error: "not found" }, 404);
      } catch (err) {
        // Log the full error (incl. stack) for the operator; surface a
        // useful message to the client only for known/recoverable failures
        // like `ReadTimeoutError` (object not found / cold backend / session
        // expired) — never echo raw stack traces out over HTTP.
        console.error(`[openadt-mcp] read aux error: ${formatError(err)}`);
        if (err instanceof ReadTimeoutError) {
          return json({ error: err.message }, 504);
        }
        return json({ error: "internal error" }, 500);
      }
    },
  });

  return {
    url: `http://127.0.0.1:${server.port}`,
    token,
    stop() {
      try {
        server.stop(true);
      } catch {
        /* already stopped */
      }
    },
  };
}

function formatError(err: unknown): string {
  if (err instanceof Error) {
    return err.stack ?? `${err.name}: ${err.message}`;
  }
  return String(err);
}
