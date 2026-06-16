# openadt-mcp

The standalone OpenADT MCP server. A single, zero-dependency Bun-compiled
binary that turns any MCP-compatible agent — Cursor, Claude Desktop, Devin,
Windsurf, a custom HTTP client — into a SAP ADT client.

This document is the **user-facing reference** for the `openadt-mcp`
product. It complements:

- [`packages/adt-mcp/README.md`](../packages/adt-mcp/README.md) — product overview
- [`specs/mcp.md`](../specs/mcp.md) — authoritative contract (commands, exit codes, lifecycle)
- [`specs/packaging.md`](../specs/packaging.md#openadt-mcp-archives) — release artifact matrix
- [`docs/usage.md`](usage.md#mcp) — full OpenADT user manual

## Contents

- [What is `openadt-mcp`?](#what-is-openadt-mcp)
- [Installation](#installation)
- [Prerequisites](#prerequisites)
- [First-run checklist](#first-run-checklist)
- [CLI reference](#cli-reference)
  - [`serve`](#serve)
  - [`status`, `list`, `stop`, `print-config`](#status-list-stop-print-config)
  - [`check`](#check)
  - [Exit codes](#exit-codes)
- [Transports and source modes](#transports-and-source-modes)
- [MCP tools](#mcp-tools)
  - [SAP `abap_*` tools](#sap-abap_-tools)
  - [OpenADT `adt_*` tools](#openadt-adt_-tools)
  - [Workflow prompts](#workflow-prompts)
- [Configuring your agent](#configuring-your-agent)
- [HTTP mode for native MCP clients](#http-mode-for-native-mcp-clients)
- [Endpoint store](#endpoint-store)
- [Troubleshooting](#troubleshooting)
- [Security](#security)

---

## What is `openadt-mcp`?

`openadt-mcp` is a **unified MCP mesh** that merges two tool groups behind one
endpoint, plus a set of OpenADT-authored workflow prompts:

| Group          | Source                                                                | Count    | Backend                                |
| -------------- | --------------------------------------------------------------------- | -------- | -------------------------------------- |
| `abap_*`       | Official SAP ADT MCP (HTTP `/mcp` inside `adt-lsc`)                    | Grows    | Proxied transparently                  |
| `adt_*`        | OpenADT-owned LSP tools (lock, format, refs, ATC, transport, …)       | 26       | In-process over the same `adt-lsc` LSP |
| `prompts/*`    | OpenADT-authored workflow prompts (RAP, OData, tests, …)              | 4        | Local markdown                         |

A single owned `adt-lsc` child backs both tool groups. The mesh speaks
**stdio** (default — what IDEs and CLI agents want) or **HTTP** (`--http` —
for web MCP clients).

It is the **same launcher** the Java `openadt mcp` command wraps, but
packaged and versioned as a standalone binary. You do not need the `openadt`
Java package; if you have both, they share `~/.openadt/` state and
`openadt mcp …` resolves and spawns `openadt-mcp` directly.

---

## Installation

`openadt-mcp` is published as four platform archives per release
(`openadt-mcp-X.Y.Z-{platform}.{zip|tar.gz}`):

| Platform      | Archive                                                |
| ------------- | ------------------------------------------------------ |
| `win-x64`     | `openadt-mcp-X.Y.Z-win-x64.zip`                        |
| `linux-x64`   | `openadt-mcp-X.Y.Z-linux-x64.tar.gz`                   |
| `darwin-arm64`| `openadt-mcp-X.Y.Z-darwin-arm64.tar.gz`                |
| `darwin-x64`  | `openadt-mcp-X.Y.Z-darwin-x64.tar.gz`                  |

Scoop and Homebrew pin the matching version of `openadt-mcp` to the
corresponding `openadt` release. Bun is **bundled** — no separate runtime
install.

### Windows — Scoop

```powershell
scoop bucket add abapify https://github.com/abapify/scoop-bucket
scoop install openadt-mcp
```

### macOS / Linux — Homebrew

```bash
brew tap abapify/openadt
brew install openadt-mcp
```

### Manual download

Grab the archive for your platform from the
[GitHub release](https://github.com/abapify/openadt/releases), extract it
somewhere on `PATH`, and verify:

```bash
openadt-mcp --help
# openadt-mcp <command>
# Commands:
#   serve         Start the unified mesh MCP server (stdio; --http for web)
#   status        Probe active MCP endpoint health
#   stop          Stop tracked MCP backend(s)
#   list          List active MCP endpoints
#   print-config  Emit HTTP MCP client JSON (url + headers)
#   check         Detect ADT LS extension version
```

### Verify

```bash
openadt-mcp check
# ADT LS Extension Detection
# ==========================
#
# Found 1 extension(s):
#   - /home/you/.vscode/extensions/sapse.adt-vscode-3.20.0
#     Version: 3.20.0
#
# Selected (newest):
#   Path: /home/you/.vscode/extensions/sapse.adt-vscode-3.20.0
#   Version: 3.20.0
#
# adt-lsc: /home/you/.vscode/extensions/sapse.adt-vscode-3.20.0/bin/adt-lsc
#
# Destinations
# ------------
#   Store: /home/you/.adtls
#   1 destination(s): ABC_100_USER_EN
```

If `sapse.adt-vscode` is not found, install it and re-run. Override the
binary path with `ADT_LS_PATH=/custom/path/adt-lsc`.

---

## Prerequisites

`openadt-mcp` has one runtime dependency: the SAP ADT for VS Code extension
(`sapse.adt-vscode`), which ships the `adt-lsc` binary and the licensed
ADT packages. OpenADT cannot redistribute them due to the SAP Developer
License.

1. **Install Visual Studio Code** — <https://code.visualstudio.com/download>.
   VS Code does not need to stay open during normal operation; the install
   makes the `adt-lsc` binary available on disk.
2. **Install ABAP Development Tools for VS Code** —
   <https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode>.
   From VS Code, press `Ctrl+P` and run `ext install SAPSE.adt-vscode`.
3. **Configure a destination in VS Code** (first time only):
   1. Open the Command Palette (`Ctrl+Shift+P`).
   2. Run **ABAP: New Destination**.
   3. Choose **RFC** (on-premise / private cloud) or **HTTP** (public cloud).
   4. Enter your connection details (host, client, language).

   The destination is stored in `~/.adtls/destinations.json` and is picked
   up automatically by `openadt-mcp serve`.

Override the destination directory with `ADTLS_HOME=/custom/dir`.

`openadt-mcp` does **not** require Java, Bun, Node, or Python.

---

## First-run checklist

```bash
# 1. Confirm the SAP extension is discoverable and destinations are present
openadt-mcp check

# 2. Start the stdio mesh (run by your agent — see "Configuring your agent")
openadt-mcp serve --stdio

# 3. Or, for HTTP-native clients:
openadt-mcp serve --http --port 2236 --show-token
#   prints: Bearer <token>

# 4. Inspect / manage running backends
openadt-mcp list
openadt-mcp status
openadt-mcp stop
```

Cold start performs `adt-lsc` spawn → LSP handshake → destinations →
`startMCPServer` → poll until HTTP accepts. SSO / browser-based logon may
prompt on first call; subsequent calls use the cached session.

---

## CLI reference

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

### `serve`

Start the unified mesh. Default transport is **stdio**; pass `--http` for
Streamable HTTP `/mcp` + Bearer on localhost.

```bash
# stdio mesh (default) — what IDEs / CLI agents want
openadt-mcp serve --stdio

# HTTP mesh for web MCP clients
openadt-mcp serve --http --port 2236 --show-token

# SAP abap_* tools only (no OpenADT adt_* group)
openadt-mcp serve --no-lsp

# OpenADT adt_* tools only (no SAP abap_* proxy)
openadt-mcp serve --no-proxy

# Attach to an external SAP MCP (SAP tools only, no adt_*)
openadt-mcp serve --sap-port 2236 --sap-token <tok>

# Reuse a healthy shared daemon from the endpoint store (SAP tools only)
openadt-mcp serve --shared

# Pre-select a destination at startup
openadt-mcp serve --destination ABC_100_USER_EN
```

| Flag                       | Default                          | Meaning                                                                          |
| -------------------------- | -------------------------------- | -------------------------------------------------------------------------------- |
| `--http`                   | off (stdio)                      | Serve the mesh over Streamable HTTP `/mcp` instead of stdio                      |
| `--port N`                 | `2236`                           | Mesh HTTP port (`--http`); else the SAP backend port                            |
| `--workspace DIR`          | `~/.openadt/adt-ls-workspace`    | `adt-lsc` `-data` directory                                                      |
| `--lsp` / `--no-lsp`       | `--lsp`                          | Include / exclude the OpenADT `adt_*` group (own mode)                          |
| `--proxy` / `--no-proxy`   | `--proxy`                        | Include / exclude the SAP `abap_*` group                                         |
| `--sap-port N`             | —                                | Attach to an external SAP MCP on this port                                       |
| `--sap-token T`            | endpoint store                   | Bearer token for `--sap-port`                                                    |
| `--shared`                 | off                              | Reuse a healthy shared daemon from the endpoint store                            |
| `--destination ID`         | —                                | Pre-logon + `setDestination` for one destination                                 |
| `--logon-timeout`          | `300`                            | Seconds for `ensureLoggedOn`                                                     |
| `--show-token`             | off                              | Print the Bearer token to stdout (with `--http`)                                |
| `--verbose` / `-v`         | off                              | LSP trace → `~/.openadt/logs/mcp-serve.log`                                      |

Destinations are **auto-derived** from `~/.adtls/destinations.json`; there
is no `--import` flag — point `--workspace` at the data directory and
destinations are pulled in.

### `status`, `list`, `stop`, `print-config`

```bash
openadt-mcp list
# port 2236 · http://localhost:2236/mcp · own · pid 4711

openadt-mcp status
# OK   port 2236 · http://localhost:2236/mcp · own · 1 dest

openadt-mcp status --port 2236
# OK   port 2236 · http://localhost:2236/mcp · own · 1 dest

openadt-mcp status --json
# [
#   {
#     "port": 2236,
#     "url": "http://localhost:2236/mcp",
#     "healthy": true,
#     "destinations": ["ABC_100_USER_EN"],
#     "mode": "own"
#   }
# ]

openadt-mcp stop                 # stop every tracked backend
openadt-mcp stop --port 2236     # stop one

openadt-mcp print-config --port 2236
# {"url":"http://localhost:2236/mcp","headers":{"Authorization":"Bearer <tok>"}}
```

`stop` is **safe for shared mode**: it only kills endpoints whose owning
`pid` is still alive, and prunes the corresponding
`~/.openadt/mcp/endpoints/<port>.json`.

### `check`

```bash
openadt-mcp check
```

Detects the `sapse.adt-vscode` extension (newest by version), reports the
resolved `adt-lsc` path (override with `ADT_LS_PATH`), and prints the
destination store location and registered destination ids. Use this when a
`serve` fails with "extension not found" or when destinations are missing.

### Exit codes

| Code | Meaning                                                            |
| ---- | ------------------------------------------------------------------ |
| 0    | Success                                                            |
| 1    | General error (extension missing, logon timeout/failure, etc.)     |
| 3    | HTTP MCP never became ready within the timeout                     |
| 4    | Port already in use                                                |
| 5    | Multiple active endpoints and no `--port` given                    |
| 6    | Ensure lock timeout (shared-mode spawn deadlock)                   |
| 7    | Daemon spawn failed (shared mode)                                  |

---

## Transports and source modes

### Transports

| Transport                | Who connects                   | Wire format                                            |
| ------------------------ | ------------------------------ | ------------------------------------------------------ |
| `serve` (stdio, default) | Agent / IDE with stdio MCP     | Content-Length / NDJSON on stdin/stdout                |
| `serve --http`           | Agent / IDE with HTTP MCP      | `POST http://localhost:<port>/mcp` + Bearer            |

The stdio bridge is a transparent proxy: it forwards **every** client
JSON-RPC request to HTTP MCP (including `initialize`, `tools/list`,
notifications) and writes the HTTP response back to stdout. **No** stub or
synthetic `initialize` result from OpenADT. The session id is preserved
across requests.

### SAP source modes

The SAP `abap_*` group can come from three sources. The OpenADT `adt_*`
group needs the owned LSP connection, so it is available in **own** mode
only:

| Mode              | Trigger                              | `adt-lsc`      | `adt_*` | `abap_*` |
| ----------------- | ------------------------------------ | -------------- | ------- | -------- |
| **own** (default) | `serve`                              | spawned, owned | yes     | yes      |
| **attach**        | `serve --sap-port N [--sap-token]`   | external       | no      | yes      |
| **shared**        | `serve --shared`                     | shared daemon  | no      | yes      |

In **shared** mode, `serve --stdio` finds or spawns a detached daemon
(one `adt-lsc` per workspace) and attaches a lightweight stdio bridge.
The bridge does **not** kill the backend on exit — multiple agents share
the same daemon. Force owned lifecycle in CI / scripts with
`openadt-mcp serve --http --port 2236`.

---

## MCP tools

### SAP `abap_*` tools

The official SAP ADT MCP server is **SAP-owned** and its surface grows with
each `sapse.adt-vscode` release. OpenADT orchestrates it but does not
reimplement the layer.

Authoritative list and parameter shapes:
[SAP ADT MCP tools reference](https://help.sap.com/docs/abap-cloud/abap-development-tools-for-visual-studio-code/mcp-tools).

**Example: list tools at runtime.** From any MCP client:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

The mesh returns the union of `abap_*` (proxied from SAP) and `adt_*`
(OpenADT-owned) tools. Long SAP tool names may be shortened for clients
with a 64-char limit — see
[Troubleshooting: Bedrock tool name cap](#troubleshooting).

### OpenADT `adt_*` tools

The 26 OpenADT-owned tools run **in-process** over the same `adt-lsc` LSP
connection. Each tool requires a `destination` argument matching an entry
in `~/.adtls/destinations.json` (e.g. `ABC_100_USER_EN`). Object URIs use
the standard ADT format (e.g. `/sap/bc/adt/oo/classes/zcl_hello`).

#### High priority

| Tool                       | Description                                       | LSP method                                    |
| -------------------------- | ------------------------------------------------- | --------------------------------------------- |
| `adt_get_check_variants`   | List ATC check variants on the destination        | `adtLs/atc/getCheckVariants`                  |
| `adt_run_check`            | Run ATC check on one or more ADT URIs             | `adtLs/atc/runCheck`                          |
| `adt_lock_file`            | Lock an ADT object for editing                    | `adtLs/fileSystem/lockFile`                   |
| `adt_unlock_file`          | Release the lock on an ADT object                 | `adtLs/fileSystem/unlockFile`                 |
| `adt_get_file_lock_status` | Get the current lock status of an object          | `adtLs/fileSystem/getFileLockStatus`          |
| `adt_format`               | Pretty-print ABAP / DDL / DDLA / SRVD / BSCE      | `textDocument/formatting` (open-doc lifecycle) |
| `adt_diagnostic`           | Syntax + check errors for an ADT object           | `textDocument/diagnostic` (open-doc lifecycle) |
| `adt_find_references`      | Find all usages of a symbol                       | `textDocument/references` (open-doc lifecycle) |
| `adt_toggle_version`       | Toggle between active and inactive version        | `adtLs/fileSystem/toggleVersion`              |
| `adt_check_transport_lock` | Check the transport associated with an object lock | `adtLs/cts/transport/checkTransportForObjectLock` |
| `adt_create_transport`     | Create a workbench/customizing transport          | `adtLs/cts/transport/createTransportForObjectLock`|
| `adt_assign_transport`     | Assign a transport to an ADT object               | `adtLs/cts/transport/assignTransportToObject`     |
| `adt_quick_search`         | RIS quick search in the repository                | `adtLs/repository/quickSearch`                |

#### Medium priority

| Tool                          | Description                                | LSP method                              |
| ----------------------------- | ------------------------------------------ | --------------------------------------- |
| `adt_get_inactive_objects`    | List inactive objects in the request       | `adtLs/activation/getInactiveObjects`   |
| `adt_run_application`         | Run a class or program in console mode     | `adtLs/applicationRun/runApplication`   |
| `adt_get_hover`               | Markdown documentation for a code element | `textDocument/hover` (open-doc lifecycle)|
| `adt_document_symbols`        | Hierarchical document outline              | `textDocument/documentSymbol` (open-doc)|
| `adt_search_transports_simple`| Simple transport search                    | `adtLs/cts/transport/searchTransportsSimple`|
| `adt_search_transports`       | Advanced transport search with all filters | `adtLs/cts/transport/searchTransports`      |
| `adt_get_coverage`            | Get code coverage data for a run           | `adtLs/coverage/getCoverage`            |
| `adt_load_statement_results`  | Load statement-level coverage             | `adtLs/coverage/loadStatementResults`   |

#### Low priority

| Tool                     | Description                                       | LSP method                            |
| ------------------------ | ------------------------------------------------- | ------------------------------------- |
| `adt_force_refresh`      | Force refresh of an object from the server        | `adtLs/fileSystem/forceRefresh`       |
| `adt_get_object_name`    | Extract the object name from an ADT URI           | `adtLs/fileSystem/getObjectName`      |
| `adt_get_package_name`   | Extract the package name from an ADT URI          | `adtLs/fileSystem/getPackageName`     |
| `adt_get_folder_uri`     | Compute the folder URI for navigation             | `adtLs/fileSystem/getFolderUri`       |
| `adt_get_external_links` | Return external links (e.g. ADT for Eclipse)      | `adtLs/fileSystem/getExternalLinks`   |
| `adt_get_ls_uri`         | ADT path → repotree/AFF URI (required by others)  | `adtLs/repository/getLsUri`           |

#### Open-document lifecycle

Headless MCP calls standard LSP `textDocument/*` methods (`references`,
`documentSymbol`, `hover`, …) that VS Code reaches only **after** opening
a file. Each `adt_*` text-document call wraps:

1. `adtLs/repository/getLsUri` — ADT path → repotree/AFF URI (required).
2. `adtLs/fileSystem/forceRefresh` — `{ uri, refreshRelatedFiles: true }` (best-effort).
3. `adtLs/fileSystem/readFile` — load source into SFS.
4. `textDocument/didOpen` — notification with file content (standalone LSP transport only).
5. Primary LSP request (`textDocument/*`).
6. `textDocument/didClose` — always in `finally` (standalone LSP transport only).

Per-URI serialization prevents concurrent calls on the same object from
closing each other's documents mid-flight.

`adt_find_references` accepts `symbol` (preferred) or `position` (0-based),
resolves position via outline/source search, and times out after 20 s with
guidance when the symbol is too heavily referenced. Hover tools prime
`textDocument/semanticTokens/full` before `textDocument/hover` (ABAP token
cache gate).

#### Result envelope

All `adt_*` tools return a standardised JSON envelope wrapped in a
standard MCP `CallToolResult`:

```json
{
  "success": true,
  "data": { /* tool-specific payload */ }
}
```

On error, `success: false` and a structured `error` object is returned
with `isError: true` on the MCP response:

```json
{
  "success": false,
  "error": {
    "code": "LOCKED_BY_OTHER",
    "message": "Object is locked by another user",
    "destination": "ABC_100_USER_EN"
  }
}
```

Error codes: `LOCKED_BY_OTHER`, `NO_TRANSPORT`, `NOT_FOUND`, `INVALID_URI`,
`THROTTLED`, `INTERNAL`, `LSP_ERROR`, `TIMEOUT`.

### Workflow prompts

OpenADT ships four workflow prompts over `prompts/list` / `prompts/get`:

| Prompt                  | Purpose                                                    |
| ----------------------- | ---------------------------------------------------------- |
| `create-abap-object`    | Create a new ABAP class / interface / program / table      |
| `generate-rap-service`  | Generate a RAP service binding for a target package       |
| `expose-odata-service`  | Inspect or expose an OData service binding                |
| `activate-and-test`     | Activate the request and run ABAP Unit tests               |

Disable the prompts (and the appended SAP cheat-sheet on
`initialize.instructions`) with `OPENADT_MCP_NO_GUIDANCE=1`.

---

## Configuring your agent

Use **short server keys** (e.g. `sap-adt`, `adt`) — long keys eat into the
tool-name budget on clients with a 64-char cap (Claude on AWS Bedrock;
see [Troubleshooting](#troubleshooting)).

### Cursor

`.cursor/mcp.json` in your project root (or `~/.cursor/mcp.json` globally).
Do **not** set `"cwd": "${workspaceFolder}"` — some agent builds break
MCP spawn with it. Run the agent from the project root.

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

### Claude Desktop

`claude_desktop_config.json` (Claude Desktop settings for the exact path):

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

### Claude Code

`.mcp.json` at the repo root (not `.cursor/mcp.json`). Same `sap-adt` key,
same command:

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

### Devin CLI

`.devin/config.json` in your project root:

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

### Windsurf

`~/.codeium/windsurf/mcp_config.json`:

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

### Pre-selecting a destination

If you only work with one destination, pre-select it at startup to skip
per-call destination arg:

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "openadt-mcp",
      "args": ["serve", "--stdio", "--destination", "ABC_100_USER_EN"]
    }
  }
}
```

---

## HTTP mode for native MCP clients

For agents that support Streamable HTTP MCP (URL + headers), start the
server once and keep it running:

```bash
openadt-mcp serve --http --port 2236 --show-token
# prints the Bearer token to stdout
```

Print the agent-ready client config:

```bash
openadt-mcp print-config --port 2236
```

```json
{
  "url": "http://localhost:2236/mcp",
  "headers": {
    "Authorization": "Bearer <token>",
    "User-Agent": "openadt-mcp-client"
  }
}
```

| Item           | Value                                                                                                          |
| -------------- | -------------------------------------------------------------------------------------------------------------- |
| URL            | `http://localhost:<port>/mcp`                                                                                  |
| Method         | `POST`                                                                                                         |
| Auth           | `Authorization: Bearer <token>` (required; missing/invalid → **401**)                                          |
| `Host`         | Must be `localhost` or `127.0.0.1` (DNS rebinding filter)                                                      |
| `Content-Type` | `application/json`                                                                                             |
| `Accept`       | `application/json, text/event-stream`                                                                          |
| Session        | `Mcp-Session-Id` request/response header (streamable HTTP MCP); preserve across requests in one client session |

Token is generated at startup unless you pass `--show-token`; it is stored
in `~/.openadt/mcp/endpoints/<port>.json` (mode `0600`).

---

## Endpoint store

Each `serve` / `serve --http` writes `~/.openadt/mcp/endpoints/<port>.json`:

```json
{
  "port": 2236,
  "url": "http://localhost:2236/mcp",
  "token": "...",
  "pid": 4711,
  "adtLscPid": 4712,
  "destinations": ["ABC_100_USER_EN"],
  "mode": "own"
}
```

- Removed on **clean shutdown** of the owning process.
- Stale entries pruned when `pid` is dead (`stop` / `list` will skip them).
- Used by `list`, `status`, `print-config`, and `--shared` discovery — not
  used by `attach` mode (`--sap-port`).
- File mode is `0600`; the directory is `~/.openadt/mcp/endpoints/`.

---

## Troubleshooting

### `openadt-mcp check` reports "No ADT VS Code extensions found"

Install `sapse.adt-vscode` from the VS Code marketplace
(<https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode>) and
re-run. If the extension is installed in a non-standard location, point at
the `adt-lsc` binary directly with `ADT_LS_PATH=/custom/path/adt-lsc`.

### "No destinations found"

Configure a destination in VS Code (Command Palette → **ABAP: New
Destination**). The destination is stored in `~/.adtls/destinations.json`
(override the directory with `ADTLS_HOME`). Verify with `openadt-mcp check`.

### Cold-start logon hangs

SSO / Secure Login / browser-based logon may prompt on the first call to a
destination. This is expected and can take up to `--logon-timeout` seconds
(default 300). Use `--verbose` to trace:

```bash
openadt-mcp serve --stdio --verbose
# trace → ~/.openadt/logs/mcp-serve.log
```

If a previous run crashed, stale lock or endpoint files can block startup:

```bash
# Stale lock files
rm -f ~/.openadt/mcp/ensure-*.lock

# Stale endpoint files
rm -f ~/.openadt/mcp/endpoints/*.json
```

### MCP Inspector / Claude reconnect loop on `openadt-mcp serve --stdio`

Upgrade to **`openadt-mcp ≥ 1.3.17`**. Earlier 1.3.16 cold-start shared
backend was unable to spawn; the fix re-execs the binary via
`process.execPath` instead of looking for `bun main.ts` on disk.
Workarounds for the broken release: add `--standalone` (monolithic path,
no shared backend):

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "openadt-mcp",
      "args": ["serve", "--stdio", "--standalone"]
    }
  }
}
```

### Port 2236 already in use

VS Code's own ADT extension defaults to port 2236. Either stop the VS Code
MCP server or pick a different port:

```bash
openadt-mcp serve --http --port 2237
```

### Multiple active endpoints, no `--port` given

`status`, `stop`, and `print-config` exit 5 if more than one endpoint is
active and you did not specify `--port`. Pass `--port <port>` to pick one.

### Amazon Q can't spawn the binary

Amazon Q often can't resolve `openadt-mcp` from its GUI's `PATH`. Point
the config at an absolute path and add `--standalone` (Amazon Q has its
own per-session model and is usually fine with the monolithic path):

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "C:\\Users\\you\\scoop\\apps\\openadt-mcp\\current\\openadt-mcp.exe",
      "args": ["serve", "--stdio", "--standalone"]
    }
  }
}
```

### AWS Bedrock / Claude tool-name cap (64 chars)

Claude on AWS Bedrock prefixes every MCP tool as
`mcp__<serverKey>__<toolName>`, and Bedrock Converse rejects
`toolSpec.name` values longer than 64 characters. SAP ADT MCP tool names
can be long (e.g. `abap_business_services-fetch_service_information` is
49 chars), so a long server key pushes the combined name over the limit.

**Budget formula:**

```text
len(serverKey) + len(toolName) ≤ 57   // 5 (mcp__) + 2 (__) = 7 overhead
```

| Server key        | Max safe SAP tool name | Notes                                  |
| ----------------- | ---------------------- | -------------------------------------- |
| `sap-adt`         | 50                     | Recommended — fits the longest SAP tool|
| `sap-adt-dev`     | 46                     | `abap_business_services-…` rejected    |
| `my-sap-adt-mcp`  | 43                     | Most business-service tools rejected   |

**Mitigations (in order):**

1. **Keep the MCP server key short** — use `sap-adt` or `adt` in
   `.mcp.json` / `.cursor/mcp.json`. Do **not** use descriptive suffixes
   like `-dev` unless you also shorten tool exposure.
2. **Stdio proxy shortening** — `serve --stdio` shortens SAP tool names
   longer than 45 characters in `tools/list` and maps aliases back on
   `tools/call`. Override the max with `OPENADT_MCP_MAX_TOOL_NAME` (range
   16–57).
3. **Tighter limit for long server keys** — if you must keep a long
   server key, set `OPENADT_MCP_MAX_TOOL_NAME` to `57 - len(serverKey)`.

**Symptom:** project fails to start in Claude with HTTP 400 /
`ValidationException`: `toolSpec.name` … `must have length less than or
equal to 64`.

---

## Security

- Bearer tokens live only in `~/.openadt/mcp/endpoints/<port>.json` (mode
  `0600`); debug logs redact `Authorization`.
- The mesh binds to **localhost** only. Remote clients are not supported.
- Only fictional fixtures (`DEV`, `dev-ms.example.com`, fake UUIDs) belong
  in git. Never commit real destinations, JCo natives, or landscape
  identifiers.
- The `openadt-mcp` archives **do not** bundle `adt-ls/` from
  `sapse.adt-vscode` — the SAP Developer License applies to that binary
  and its dependencies.

---

## See also

- [`packages/adt-mcp/README.md`](../packages/adt-mcp/README.md) — product overview
- [`specs/mcp.md`](../specs/mcp.md) — authoritative contract
- [`specs/adt-agent-typescript.md`](../specs/adt-agent-typescript.md) — `adt_*` tool contract (lifecycle, throttling, error codes)
- [`specs/packaging.md`](../specs/packaging.md#openadt-mcp-archives) — release artifact matrix and package manager integration
- [`docs/usage.md`](usage.md) — full OpenADT user manual (fetch, proxy, MCP)
- [SAP ADT MCP tools reference](https://help.sap.com/docs/abap-cloud/abap-development-tools-for-visual-studio-code/mcp-tools) — official SAP tool catalog
