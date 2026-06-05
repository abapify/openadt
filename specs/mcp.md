# SAP ADT MCP (launcher)

OpenADT **does not** implement MCP tools. The Bun launcher in `tools/sap-adt-mcp-launcher/` starts the **official SAP ADT MCP** from the [SAP ADT VS Code extension](https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode): `adt-lsc` over LSP pipe transport, then `adtLs/mcp/startMCPServer`.

## Commands

| Command        | Role                                                         |
| -------------- | ------------------------------------------------------------ |
| `serve`        | Spawn `adt-lsc`, LSP init, start HTTP MCP, hold until Ctrl+C |
| `status`       | Probe `http://localhost:<port>/mcp`                          |
| `list`         | List active endpoints (one store file per port)              |
| `print-config` | Emit Cursor `mcpServers` JSON from endpoint store            |

Run via Bun or OpenADT CLI (requires [Bun](https://bun.sh) on PATH):

```bash
openadt mcp serve --port 2236
openadt mcp list
openadt mcp print-config --port 2236
```

## Endpoint store (multi-instance)

Each `serve` writes `~/.openadt/mcp/endpoints/<port>.json` (url, token, pid, destinations). Removed on clean shutdown; stale files pruned when pid is gone.

- **One** active server: `print-config` and `status` auto-select it.
- **Several** servers: `list`, then `print-config --port <port>` (and `status --port <port>`).

Bearer token is **not** printed on serve by default — use `print-config` or `scripts/sync-cursor-mcp.ts` for Cursor.

## Security

- Tokens in `~/.openadt/mcp/endpoints/` (mode `0600`); debug logs redact Bearer headers
- Tests and docs use fictional fixtures only

## Implementation

- Launcher: `tools/sap-adt-mcp-launcher/`
- Cursor sync helper: `scripts/sync-cursor-mcp.ts`
