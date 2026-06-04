# SAP ADT MCP (launcher)

OpenADT **does not** implement MCP tools. The `openadt mcp` commands start the **official SAP ADT MCP** from the [SAP ADT VS Code extension](https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode): `adt-lsc` over LSP pipe transport, then `adtLs/mcp/startMCPServer`.

## Prerequisites

- **SAP ADT for VS Code** installed (`sapse.adt-vscode` under `~/.vscode/extensions` or `~/.cursor/extensions`)
- **Bun** on `PATH` (launcher is TypeScript; Java CLI delegates to `tools/sap-adt-mcp-launcher/`)
- SAP destinations configured in the **ADT VS Code / Cursor GUI** (_Open Destinations_, _Add Destination as Folder to Workspace_). `openadt mcp serve` imports them by default (not from `~/.openadt/config.toml`).

Do **not** bundle `adt-ls/` from the extension in OpenADT releases (SAP Developer License).

## Commands

| Command                    | Role                                                         |
| -------------------------- | ------------------------------------------------------------ |
| `openadt mcp serve`        | Spawn `adt-lsc`, LSP init, start HTTP MCP, hold until Ctrl+C |
| `openadt mcp status`       | Probe `http://localhost:<port>/mcp`                          |
| `openadt mcp print-config` | Emit Cursor `mcpServers` JSON snippet                        |

### `openadt mcp serve`

```bash
openadt mcp serve
openadt mcp serve --port 2236 --json
openadt mcp serve --show-token
```

Options:

- `--port` — MCP HTTP port (default **2236**, same as extension `adt.mcpServer.port`)
- `--workspace` — adt-lsc `-data` directory (with default GUI import: VS Code/Cursor `adtWorkspace` under `workspaceStorage`)
- `--import-from=auto` — **default**: `~/.adtls/destinations.json` (ADT LS store from VS Code logon), else GUI `adtWorkspace` cache, else `~/.openadt/destinations/*.toml` materialized into the adt-lsc workspace; registers `abap:/<destinationId>`, passes `destinationsStorePath` = `~/.adtls` (directory, not the JSON file), and calls `adtLs/destinations/createProject` per id (same as _Add Destination as Folder to Workspace_)
- `--import-from=adtls` — `~/.adtls/destinations.json` only
- `--import-from=gui` — GUI semantic cache only
- `--import-from=openadt` — `~/.openadt` materialization only (not `openadt fetch`)
- `--no-gui` / `--import-from=none` — no import
- `--destination` — optional `adtLs/mcp/setDestination` after start
- `--json` — machine-readable status on stdout
- `--show-token` — print full Bearer token (default: redacted)
- `--verbose` / `-v` — LSP message trace + `adt-lsc -consoleLog` → `~/.openadt/logs/mcp-serve.log` (tokens redacted); also `MCP_DEBUG=1`
- `--log-file` — override debug log path (with `--verbose`)

### Debugging (inside vs outside MCP)

OpenADT does **not** implement MCP HTTP — SAP `adt-lsc` does after `adtLs/mcp/startMCPServer`. The launcher can observe:

| Layer                           | What you see                                                                                                               |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Launcher `--verbose`            | LSP pipe traffic, `createProject` / `ensureLoggedOn`, `destinations/list`, SAP → client notifications (e.g. logon prompts) |
| `~/.openadt/logs/mcp-serve.log` | Same, append-only; Bearer/password fields redacted                                                                         |
| `<workspace>/.metadata/.log`    | Eclipse/adt-lsc JVM log (same file VS Code uses when `-data` points at `adtWorkspace`)                                     |
| MCP Inspector / `tools/call`    | HTTP errors and `isError` tool results (SAP MCP protocol)                                                                  |

For headless logon, the launcher answers SAP `requestBrowserBasedLogon` (opens the default browser automatically) and `requestLogonInput` (terminal prompt). LSP `initialize` must include `initializationOptions.userAgentInfos` (same as VS Code) or adt-lsc JCo logon fails before any SSO UI. Complete SSO / Secure Login when prompted; default wait **5 minutes** (`--logon-timeout=300`). Silent SSO may skip the popup if credentials are already cached.

Exit codes: `0` OK · `1` extension missing · `2` `adt-lsc`/LSP failed · `3` `startMCPServer` failed · `4` port in use

### Cursor / agent HTTP config

```json
{
  "mcpServers": {
    "sap-adt": {
      "url": "http://localhost:2236/mcp",
      "headers": {
        "Authorization": "Bearer <from openadt mcp serve --show-token>",
        "User-Agent": "openadt-mcp-client"
      }
    }
  }
}
```

Tool catalog = **SAP** (`abap_list_destinations`, `fetch_services`, IDE actions, etc.) — not OpenADT duplicates.

## Relationship to `fetch` / `proxy`

- `openadt fetch` / `openadt proxy` — scripts and IDE bridge via OpenADT config (`~/.openadt/config.toml`)
- `openadt mcp serve` — agent path via SAP adt-ls destination store (GUI import by default; `config.toml` sync is optional follow-up for `fetch` only)

## Security

- No logging of Bearer tokens unless `--show-token`
- Tests and docs use fictional fixtures only (`DEV`, `dev-ms.example.com`)

## Implementation

- Launcher: `tools/sap-adt-mcp-launcher/`
- CLI wiring: Picocli `mcp` + dev routing in `scripts/nx-openadt.ts`

## Without extension

Exit **1** with Marketplace link. **No** fallback to a custom stdio MCP bridge.
