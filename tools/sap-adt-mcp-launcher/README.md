# SAP ADT MCP launcher

Headless orchestrator for the **official SAP ADT MCP** shipped in the [SAP ADT VS Code extension](https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode). OpenADT does not implement MCP tools — it spawns `adt-lsc`, completes the LSP pipe handshake, and calls `adtLs/mcp/startMCPServer`.

## Prerequisites

- [SAP ADT for VS Code](https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode) installed (detected under `~/.vscode/extensions` or `~/.cursor/extensions`)
- [Bun](https://bun.sh) on `PATH` (dev and `./dev-openadt mcp` / packaged `openadt mcp` delegation)

## Build

The release ships pre-built JavaScript via [tsdown](https://github.com/nicepkg/tsdown). Build with:

```bash
bun run mcp:build          # from repo root
# or:
bun run build              # from this directory
```

Output: `dist/` (bundled ESM, all dependencies included — no `node_modules` needed at runtime).

## Commands

### From source (dev)

```bash
bun tools/sap-adt-mcp-launcher/src/main.ts serve
bun tools/sap-adt-mcp-launcher/src/main.ts status --port 2236
bun tools/sap-adt-mcp-launcher/src/main.ts list
bun tools/sap-adt-mcp-launcher/src/main.ts print-config --port 2236
```

### From built output (release)

```bash
bun tools/sap-adt-mcp-launcher/dist/main.mjs serve
bun tools/sap-adt-mcp-launcher/dist/main.mjs status --port 2236
bun tools/sap-adt-mcp-launcher/dist/main.mjs list
bun tools/sap-adt-mcp-launcher/dist/main.mjs print-config --port 2236
```

Each running `serve` stores url+token in `~/.openadt/mcp/endpoints/<port>.json`. With multiple servers, use `list` then `print-config --port <port>` for agent-neutral HTTP client JSON.

## Flags (`serve`)

| Flag                  | Default                       | Description                                                             |
| --------------------- | ----------------------------- | ----------------------------------------------------------------------- |
| `--port`              | `2236`                        | MCP HTTP port                                                           |
| `--workspace`         | `~/.openadt/adt-ls-workspace` | adt-lsc `-data` directory                                               |
| `--destination`       | —                             | Optional `adtLs/mcp/setDestination` (follow-up)                         |
| `--json`              | off                           | Machine-readable status on stdout                                       |
| `--show-token`        | off                           | Print Bearer token on stdout (default: endpoint store only)             |
| `--stdio`             | off                           | Stdio MCP transport (proxy stdin/stdout; stdout is JSON-RPC only)       |
| `--import-from=auto`  | **on** (default)              | `~/.adtls/destinations.json` → GUI cache → `~/.openadt` materialization |
| `--import-from=adtls` | —                             | Only `~/.adtls/destinations.json` (ADT VS Code logon store)             |
| `--no-gui`            | —                             | Skip import (`--import-from=none`)                                      |
| `--verbose` / `-v`    | off                           | LSP trace + `adt-lsc -consoleLog` → `~/.openadt/logs/mcp-serve.log`     |
| `--log-file`          | see above                     | Custom debug log path                                                   |

```bash
./dev-openadt mcp serve --port 2241 --verbose --show-token
# or: bun tools/sap-adt-mcp-launcher/src/main.ts serve ...
# packaged CLI: openadt mcp serve ...
```

SAP MCP HTTP (`tools/call`) runs inside `adt-lsc`; use MCP Inspector for that layer. Launcher verbose mode shows the LSP control plane only.

## Exit codes

| Code | Meaning                          |
| ---- | -------------------------------- |
| 0    | Success                          |
| 1    | Extension not found              |
| 2    | `adt-lsc` / LSP handshake failed |
| 3    | `startMCPServer` failed          |
| 4    | Port in use                      |

## Development override

Set `ADT_LS_PATH` to the full path of `adt-lsc.exe` (or platform binary) for CI or custom installs.

## License

Do **not** bundle `adt-ls/` from the extension in OpenADT releases — SAP Developer License applies.
