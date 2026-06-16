# openadt-mcp

The standalone OpenADT MCP server — a single binary that turns any MCP-compatible
agent (Cursor, Claude Desktop, Devin, …) into a SAP ADT client. It merges the
**official SAP ADT MCP** (`abap_*` tools, served by `adt-lsc` from the
[SAPSE.adt-vscode](https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode)
extension) with the **OpenADT-owned** LSP tools (`adt_*`) and a set of workflow
**prompts**, behind one stdio or HTTP endpoint.

`openadt-mcp` is a **separate product** from the `openadt` Java CLI. It is a
Bun-compiled binary with no Java runtime dependency, installed and versioned
independently (`scoop install openadt-mcp` / `brew install openadt-mcp`).
Either product can drive the same MCP surface; install both if you also use
`openadt fetch` / `openadt proxy`.

Full contract: [`specs/mcp.md`](../../specs/mcp.md). Troubleshooting:
[`docs/openadt-mcp.md`](../../docs/openadt-mcp.md). User-facing install & usage:
[`docs/usage.md`](../../docs/usage.md#mcp).

## What you get

| Group          | Source                                                            | Count    | Backend                                  |
| -------------- | ----------------------------------------------------------------- | -------- | ---------------------------------------- |
| `abap_*`       | Official SAP ADT MCP (HTTP `/mcp` inside `adt-lsc`)                | Grows    | Proxied transparently                    |
| `adt_*`        | OpenADT-owned LSP tools (lock/unlock, format, refs, ATC, …)       | 26       | In-process over the same `adt-lsc` LSP   |
| `prompts/*`    | OpenADT-authored workflow prompts (RAP, OData, tests, …)          | 4        | Local markdown                           |

One owned `adt-lsc` child backs both tool groups. The server speaks **stdio**
by default (IDE/agent configs) or **HTTP** (`--http`) for web MCP clients.

## Install

The binary is published as `openadt-mcp-X.Y.Z-{platform}.{zip|tar.gz}` for
`win-x64`, `linux-x64`, `darwin-arm64`, and `darwin-x64`. Use the package
manager for your platform — both pin the version to the matching `openadt`
release.

```bash
# Windows (Scoop)
scoop install openadt-mcp

# macOS / Linux (Homebrew)
brew install openadt-mcp
```

No JDK, no Bun, no Node — the runtime is embedded. SAP ADT for VS Code
(provides `adt-lsc`) is the **only** runtime dependency. Override the
binary path with `ADT_LS_PATH`.

## Configure your agent

Cursor (project or `~/.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "openadt-mcp",
      "args": ["serve", "--stdio"]
    }
  }
}
```

Claude Desktop (`claude_desktop_config.json`), Devin (`.devin/config.json`),
and Windsurf all use the same `mcpServers` schema with the same `command` /
`args`. See [`docs/openadt-mcp.md`](../../docs/openadt-mcp.md#configuring-your-agent)
for per-client snippets and the `OPENADT_MCP_MAX_TOOL_NAME` workaround for
Claude on AWS Bedrock (64-char tool-name cap).

## Quick start

```bash
# 1. Confirm the SAP extension is discoverable
openadt-mcp check

# 2. Start the stdio mesh (run by your agent)
openadt-mcp serve --stdio

# 3. Or start the HTTP mesh and let HTTP-native clients connect
openadt-mcp serve --http --port 2236 --show-token

# 4. Inspect / manage running backends
openadt-mcp list
openadt-mcp status
openadt-mcp stop
```

## CLI

```text
openadt-mcp serve [--http] [--port N] [--workspace DIR]
                  [--no-lsp] [--no-proxy]
                  [--sap-port N [--sap-token T]] [--shared]
                  [--destination ID] [--show-token] [--verbose]
openadt-mcp status   [--port N] [--json]
openadt-mcp stop     [--port N] [--json]
openadt-mcp list     [--json]
openadt-mcp print-config [--port N] [--json]
openadt-mcp check
```

| Command          | Purpose                                                                |
| ---------------- | ---------------------------------------------------------------------- |
| `serve`          | Start the mesh (stdio by default; `--http` for Streamable HTTP `/mcp`) |
| `status`         | Probe endpoint health (`OK` / `DOWN` per port)                        |
| `stop`           | Stop tracked backends (owning pid is gone; endpoint file is pruned)    |
| `list`           | List active endpoints in `~/.openadt/mcp/endpoints/`                   |
| `print-config`   | Emit `{ url, headers }` JSON for HTTP-native MCP clients               |
| `check`          | Detect `sapse.adt-vscode` version and the destination store            |

Full flag table, exit codes, and SAP source modes (`own` / `attach` / `shared`)
live in [`docs/openadt-mcp.md`](../../docs/openadt-mcp.md#cli-reference) and
[`specs/mcp.md`](../../specs/mcp.md#command-reference).

## MCP tools

The `abap_*` group is owned by SAP; see the
[SAP ADT MCP tools reference](https://help.sap.com/docs/abap-cloud/abap-development-tools-for-visual-studio-code/mcp-tools)
for the authoritative list. The 26 OpenADT-owned `adt_*` tools are documented
in [`docs/openadt-mcp.md`](../../docs/openadt-mcp.md#mcp-tools) and the
spec contract in [`specs/adt-agent-typescript.md`](../../specs/adt-agent-typescript.md).

Common tools at a glance:

| Tool                       | Purpose                                       |
| -------------------------- | --------------------------------------------- |
| `adt_lock_file`            | Lock an ABAP object for editing               |
| `adt_unlock_file`          | Release the lock                              |
| `adt_get_file_lock_status` | Inspect current lock state                    |
| `adt_format`               | Pretty-print source (ABAP / DDL / …)          |
| `adt_diagnostic`           | Syntax + check errors for an object           |
| `adt_find_references`      | Find all usages of a symbol                   |
| `adt_quick_search`         | RIS quick search in the repository            |
| `adt_run_check`            | Run ATC on one or more objects                |
| `adt_get_check_variants`   | List ATC check variants on the destination    |
| `adt_toggle_version`       | Switch between active and inactive version   |
| `adt_check_transport_lock` | Check whether a transport is required         |
| `adt_create_transport`     | Create a transport for an object lock         |
| `adt_assign_transport`     | Assign a transport to an object               |

`adt_*` tools need the owned LSP connection, so they are only available in
**own** mode (default `serve` without `--sap-port` / `--shared`).

## Security

- Bearer tokens live in `~/.openadt/mcp/endpoints/<port>.json` (mode `0600`)
  and never appear in agent configs. The token is generated at startup
  unless `--show-token` is passed.
- Secrets (Bearer, JCo, `sapcrypto`) are redacted in debug logs.
- The mesh binds to `localhost` only; remote clients are not supported.
- Only fictional fixtures (`DEV`, `dev-ms.example.com`) belong in git. Never
  commit real destinations, JCo natives, or landscape identifiers.

## License

OpenADT source is Apache-2.0. The `openadt-mcp` archives **do not** bundle
`adt-ls/` from `sapse.adt-vscode` — the SAP Developer License applies to
that binary and its dependencies.

## Related

- [`specs/mcp.md`](../../specs/mcp.md) — full contract (commands, exit codes, transport, lifecycle)
- [`specs/adt-agent-typescript.md`](../../specs/adt-agent-typescript.md) — `adt_*` tool contract
- [`specs/packaging.md`](../../specs/packaging.md#openadt-mcp-archives) — release artifact matrix
- [`docs/openadt-mcp.md`](../../docs/openadt-mcp.md) — installation, CLI reference, MCP tools, troubleshooting
- [`docs/usage.md`](../../docs/usage.md) — full OpenADT user manual (fetch / proxy / MCP)
