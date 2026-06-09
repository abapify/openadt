# openadt-mcp-agent

Stdio MCP bridge for the [OpenADT agent foundation](../../specs/adt-agent.md).

Spawns the local `openadt` Java CLI for every MCP `tools/call adt_*` and
returns the CLI's `AgentResult` JSON envelope verbatim. `tools/list`
returns the 26 catalog verbs (18 high-priority + 8 low-priority from
[`specs/lsp-operations-catalog.md`](../../specs/lsp-operations-catalog.md)).

## Why this exists

The official SAP ADT MCP server (the `adt-lsc` HTTP backend; see
[`../sap-adt-mcp-launcher/`](../sap-adt-mcp-launcher/)) only exposes a
subset of the VS Code extension's LSP surface. The autonomous-agent
foundation needs the rest (lock/unlock, format, references, diagnostics,
ATC, transport, …). `openadt-mcp-agent` is the OpenADT-owned MCP
surface for those verbs.

## Status (T21)

- Stdio MCP bridge (Content-Length framed JSON-RPC).
- 26 catalog verbs registered as `tools/list`.
- `tools/call adt_<verb>` → spawn `java -jar <openadt>.jar adt agent
<verbId> --json --param k=v …` → return the CLI's JSON envelope as
  a text content block.
- **Not yet in this branch:** HTTP / Bearer / endpoint store. Those
  land in T22 as an additive second product (`openadt-mcp-agent serve
--port 2237`).

## Build / run

```bash
# Build the Java CLI (distribution profile is fine for the stub verbs):
bun run openadt:build
# Output: apps/openadt-cli/target/openadt-1.3.17.jar

# Run the stdio bridge (default — uses ./../../apps/openadt-cli/target/openadt-1.3.17.jar):
bun tools/openadt-mcp-agent/src/main.ts
```

The bridge reads MCP JSON-RPC from stdin and writes responses to
stdout. Configure your agent with:

```json
{
  "mcpServers": {
    "openadt-agent": {
      "command": "bun",
      "args": ["tools/openadt-mcp-agent/src/main.ts"]
    }
  }
}
```

## Per-verb SDK wiring

The umbrella branch
[`feat/agent-foundation-verb-stubs`](https://github.com/abapify/openadt/tree/feat/agent-foundation-verb-stubs)
registers all 26 verbs as stubs that return
`{ error: { code: "INTERNAL", message: "awaiting-sap-sdk-wiring" } }`.
Real SAP SDK wiring is one small PR per verb; the bridge is unchanged
by those follow-ups.

## Security

- No Bearer, no auth — the bridge is local-only by definition (spawns a
  child process on the same host). An agent that can talk to the bridge
  already has shell access to the JAR.
- Endpoint store (T22) will use `0600` and redacted logs per
  [`specs/mcp.md` §6](../../specs/mcp.md).

## License

Apache-2.0 — same as the rest of OpenADT.
