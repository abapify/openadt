#!/usr/bin/env bun
/**
 * openadt-mcp-agent — stdio MCP bridge for the OpenADT agent foundation
 * (`specs/adt-agent.md`).
 *
 * Protocol: Content-Length-framed MCP JSON-RPC on stdin/stdout (the same
 * convention as the existing `sap-adt-mcp-launcher` stdio bridge;
 * `specs/mcp.md` §3.3). Every `tools/call adt_<verb>` is forwarded to the
 * local `openadt` Java CLI:
 *
 *     openadt adt agent <verbId> \
 *       --config <config> --profile <profile> --json \
 *       [--param k=v ...]
 *
 * The CLI returns the AgentResult JSON envelope which we surface verbatim
 * as the MCP `tools/call` `content` (one text block).
 *
 * `tools/list` is static: the 26 catalog verbs from the umbrella
 * branch (specs/adt-agent.md §5). Each verb is forwarded with the
 * same args. This is the minimum-viable MCP surface so an agent can
 * drive any of the 26 verbs over stdio tonight; HTTP / Bearer / endpoint
 * store land in a follow-up (T22).
 *
 * Run with:
 *   bun tools/openadt-mcp-agent/src/main.ts
 */
import { spawn } from "node:child_process";
import { resolve } from "node:path";

const OPENADT_JAR =
  process.env.OPENADT_JAR ??
  resolve(__dirname, "../../apps/openadt-cli/target/openadt-1.3.17.jar");

// Catalog verbs (specs/adt-agent.md §5, including the 8 low-priority
// metadata verbs from §8.3 of the LSP catalog). Each entry is
// (verbId, jsonSchema) so we can emit a real `tools/list` for an
// MCP client without re-reading the registry from the CLI.
const VERBS: { id: string; title: string; description: string }[] = [
  // High priority (catalog §3)
  {
    id: "adt_atc_get_variants",
    title: "ATC: list check variants",
    description: "List ATC check variants available on the destination.",
  },
  {
    id: "adt_atc_run_check",
    title: "ATC: run check",
    description: "Run an ATC check on one or more ADT URIs.",
  },
  {
    id: "adt_lock_object",
    title: "Lock object",
    description: "Lock an ADT object for editing.",
  },
  {
    id: "adt_unlock_object",
    title: "Unlock object",
    description: "Release the lock on an ADT object.",
  },
  {
    id: "adt_format_code",
    title: "Format code",
    description: "Pretty-print ABAP / DDL / DDLA / SRVD / BSCE source.",
  },
  {
    id: "adt_get_diagnostics",
    title: "Get diagnostics",
    description: "Syntax + check errors for an ADT object.",
  },
  {
    id: "adt_find_references",
    title: "Find references",
    description: "Find all usages of an ADT object.",
  },
  {
    id: "adt_toggle_version",
    title: "Toggle version",
    description: "Toggle between active and inactive version.",
  },
  {
    id: "adt_check_transport_lock",
    title: "Check transport lock",
    description: "Check the transport associated with an object lock.",
  },
  {
    id: "adt_create_transport",
    title: "Create transport",
    description: "Create a workbench/customizing transport for an object lock.",
  },
  {
    id: "adt_assign_transport",
    title: "Assign transport",
    description: "Assign a transport to an ADT object.",
  },
  {
    id: "adt_quick_search",
    title: "Quick search",
    description: "RIS quick search in the repository.",
  },
  // Medium priority (catalog §2)
  {
    id: "adt_get_inactive_objects",
    title: "List inactive objects",
    description: "List inactive objects in the request.",
  },
  {
    id: "adt_run_application",
    title: "Run ABAP application",
    description: "Run a class or program in ABAP console mode.",
  },
  {
    id: "adt_get_lock_status",
    title: "Lock status",
    description: "Return the current lock status of an object.",
  },
  {
    id: "adt_refresh_object",
    title: "Force refresh",
    description: "Force refresh of an object from the server.",
  },
  {
    id: "adt_get_hover",
    title: "Hover",
    description: "Get markdown documentation for a code element.",
  },
  {
    id: "adt_document_symbols",
    title: "Document symbols",
    description: "Hierarchical document outline.",
  },
  {
    id: "adt_search_transports",
    title: "Search transports",
    description: "Simple transport search.",
  },
  {
    id: "adt_search_transports_advanced",
    title: "Advanced transport search",
    description: "Advanced transport search with all filters.",
  },
  {
    id: "adt_get_coverage",
    title: "Code coverage",
    description: "Get coverage data for a run.",
  },
  {
    id: "adt_load_statement_coverage",
    title: "Statement coverage",
    description: "Load statement-level coverage.",
  },
  // Low priority (catalog §3.2)
  {
    id: "adt_get_object_name",
    title: "Get object name",
    description: "Extract the object name from an ADT URI.",
  },
  {
    id: "adt_get_package_name",
    title: "Get package name",
    description: "Extract the package name from an ADT URI.",
  },
  {
    id: "adt_get_folder_uri",
    title: "Get folder URI",
    description: "Compute the folder URI for navigation.",
  },
  {
    id: "adt_get_external_links",
    title: "Get external links",
    description: "Return external links (e.g. ADT for Eclipse).",
  },
];

/** Read a Content-Length-framed MCP message from a stream. */
async function readMessage(
  stream: NodeJS.ReadableStream,
): Promise<unknown | null> {
  let buffer = "";
  for await (const chunk of stream) {
    buffer += chunk.toString("utf8");
    const headerEnd = buffer.indexOf("\r\n\r\n");
    if (headerEnd < 0) continue;
    const header = buffer.slice(0, headerEnd);
    const m = /Content-Length:\s*(\d+)/i.exec(header);
    if (!m) {
      return null;
    }
    const len = Number(m[1]);
    const bodyStart = headerEnd + 4;
    if (buffer.length < bodyStart + len) continue;
    const body = buffer.slice(bodyStart, bodyStart + len);
    try {
      return JSON.parse(body);
    } catch {
      return null;
    }
  }
  return null;
}

/** Write a Content-Length-framed MCP message to a stream. */
function writeMessage(stream: NodeJS.WritableStream, msg: unknown): void {
  const body = JSON.stringify(msg);
  const header = `Content-Length: ${Buffer.byteLength(body, "utf8")}\r\n\r\n`;
  stream.write(header + body);
}

const JSON_RPC_VERSION = "2024-11-05";

function ok(id: unknown, result: unknown): unknown {
  return { jsonrpc: JSON_RPC_VERSION, id, result };
}

function err(
  id: unknown,
  code: number,
  message: string,
  data?: unknown,
): unknown {
  return { jsonrpc: JSON_RPC_VERSION, id, error: { code, message, data } };
}

/** Spawn the Java CLI and wait for one envelope. */
function callCli(
  verbId: string,
  args: Record<string, unknown>,
): Promise<{ ok: boolean; stdout: string; stderr: string; exit: number }> {
  return new Promise((resolveP) => {
    const argv = ["-jar", OPENADT_JAR, "adt", "agent", verbId, "--json"];
    for (const [k, v] of Object.entries(args)) {
      if (v == null) continue;
      argv.push("--param", `${k}=${String(v)}`);
    }
    const proc = spawn("java", argv, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    proc.stdout.on("data", (b) => (stdout += b.toString("utf8")));
    proc.stderr.on("data", (b) => (stderr += b.toString("utf8")));
    proc.on("close", (code) =>
      resolveP({ ok: code === 0, stdout, stderr, exit: code ?? 0 }),
    );
  });
}

async function main(): Promise<void> {
  // Read MCP messages from stdin in a loop.
  // Each message is handled synchronously w.r.t. stdin (no concurrent
  // dispatches); this is the minimum-viable stdio bridge — a follow-up
  // upgrade to a worker pool can land in T22.
  while (true) {
    const msg = (await readMessage(process.stdin)) as {
      id?: unknown;
      method?: string;
      params?: unknown;
    } | null;
    if (!msg) break;
    const { id, method, params } = msg;
    try {
      if (method === "initialize") {
        writeMessage(
          process.stdout,
          ok(id, {
            protocolVersion: JSON_RPC_VERSION,
            serverInfo: { name: "openadt-mcp-agent", version: "0.1.0" },
            capabilities: { tools: {} },
          }),
        );
      } else if (method === "notifications/initialized") {
        // No-op per spec.
      } else if (method === "tools/list") {
        writeMessage(
          process.stdout,
          ok(id, {
            tools: VERBS.map((v) => ({
              name: v.id,
              description: v.description,
              inputSchema: {
                type: "object",
                additionalProperties: true,
                properties: {
                  destination: {
                    type: "string",
                    description: "Destination alias (e.g. DEV)",
                  },
                  uri: { type: "string", description: "ADT URI" },
                  variant: { type: "string" },
                  searchTerm: { type: "string" },
                  content: { type: "string" },
                  maxResults: { type: "number" },
                },
              },
            })),
          }),
        );
      } else if (method === "tools/call") {
        const p = params as
          | { name?: string; arguments?: Record<string, unknown> }
          | undefined;
        const verbId = p?.name;
        const args = p?.arguments ?? {};
        if (!verbId) {
          writeMessage(
            process.stdout,
            err(id, -32602, "tools/call missing name"),
          );
          continue;
        }
        const result = await callCli(verbId, args);
        if (!result.ok) {
          writeMessage(
            process.stdout,
            ok(id, {
              isError: true,
              content: [
                {
                  type: "text",
                  text: `openadt CLI exited ${result.exit}: ${result.stderr}`,
                },
              ],
            }),
          );
        } else {
          writeMessage(
            process.stdout,
            ok(id, {
              content: [{ type: "text", text: result.stdout.trim() }],
            }),
          );
        }
      } else if (method === "ping") {
        writeMessage(process.stdout, ok(id, {}));
      } else {
        writeMessage(
          process.stdout,
          err(id, -32601, `Method not found: ${method ?? "<none>"}`),
        );
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      writeMessage(process.stdout, err(id, -32603, message));
    }
  }
}

main().catch((error) => {
  process.stderr.write(`openadt-mcp-agent fatal: ${error}\n`);
  process.exit(1);
});
