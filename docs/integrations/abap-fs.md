# ABAP FS and OpenADT

[ABAP FS](https://marcellourbani.github.io/vscode_abap_remote_fs/) (VS Code) and other ADT HTTP clients expect a **base URL**, **client**, and often **HTTP Basic auth**. On **SNC / Secure Login / SSO** landscapes you should not put SAP passwords in the editor — run **`openadt proxy`** on the host OS that owns JCo, then point the client at loopback with **local-only** Basic credentials.

Works on **Windows, Linux, and macOS** (same VS Code extension; install OpenADT via [Scoop](../../specs/packaging.md) or [Homebrew](../../specs/packaging.md)).

## 1. Install and bootstrap OpenADT

| OS            | Install                                                                                         |
| ------------- | ----------------------------------------------------------------------------------------------- |
| Windows       | `scoop bucket add openadt https://github.com/abapify/scoop-bucket` then `scoop install openadt` |
| Linux / macOS | `brew tap abapify/openadt` then `brew install openadt`                                          |

Then on the **host** (not WSL if JCo natives are on Windows):

```bash
openadt config bootstrap
openadt config build    # when using SDK transport (adt_plugins_dir)
```

See [Supported platforms](../usage.md#supported-platforms) and [WSL notes](../usage.md#wsl-and-devcontainers) if config (WSL) and runtime (Windows host) differ.

## 2. Start the proxy (local Basic auth)

Credentials here protect **localhost only** — not SAP. Details: [specs/proxy.md](../../specs/proxy.md).

**Linux / macOS (bash):**

```bash
export OPENADT_PROXY_PASSWORD="choose-a-local-secret"
openadt proxy DEV --listen 127.0.0.1:8080 --local-auth basic --local-username openadt
```

**Windows (PowerShell):**

```powershell
$env:OPENADT_PROXY_PASSWORD = "choose-a-local-secret"
openadt proxy DEV --listen 127.0.0.1:8080 --local-auth basic --local-username openadt
```

Keep this process running while VS Code is connected.

SNC / SSO profile example:

```bash
openadt proxy DEV --listen 127.0.0.1:8080 --local-auth basic --local-username openadt --profile snc
```

## 3. Configure ABAP FS

In VS Code: **ABAP FS: Connection Manager** or `abapfs.remote` in settings. Set **URL** to the proxy root (no `/sap/bc/adt` suffix).

```json
{
  "abapfs.remote": {
    "DEV": {
      "url": "http://127.0.0.1:8080",
      "username": "openadt",
      "password": "choose-a-local-secret",
      "client": "100",
      "language": "EN"
    }
  }
}
```

Use fictional values in samples only (`DEV`, client `100`). Store real passwords in the OS credential manager where ABAP FS supports it; the proxy password is still the **local** secret from step 2.

## 4. Verify

**Linux / macOS:**

```bash
curl -u openadt:choose-a-local-secret http://127.0.0.1:8080/sap/bc/adt/discovery
```

**Windows (PowerShell):**

```powershell
curl.exe -u openadt:choose-a-local-secret http://127.0.0.1:8080/sap/bc/adt/discovery
```

## ABAP FS MCP (separate path)

ABAP FS can expose an in-editor MCP server (`abapfs.mcpServer`, default `http://localhost:4847/mcp`) with optional Bearer `apiKey`. That secures the **MCP server in VS Code**, not SAP. VS Code must stay open and connected.

| Path                                       | OpenADT                                      |
| ------------------------------------------ | -------------------------------------------- |
| ABAP FS ADT connection (`abapfs.remote`)   | Use proxy URL + local Basic (this guide)     |
| ABAP FS MCP (`:4847/mcp`)                  | No OpenADT; ABAP FS tools run inside VS Code |
| Other MCP/HTTP clients needing ADT + Basic | Same proxy pattern as step 2–3               |

OpenADT’s own experimental MCP bridge: [specs/mcp.md](../../specs/mcp.md), [tools/mcp-bridge/](../../tools/mcp-bridge/).
