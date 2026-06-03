# OpenADT

**OpenADT is a local bridge to SAP ADT through the official SAP ADT SDK** — the same destination, session, and logon stack as Eclipse ADT (JCo + `com.sap.adt.*`), not a reimplemented ADT HTTP client.

## Why Java

SAP ships ADT as Eclipse plugins and JCo destinations. OpenADT runs that stack headlessly so `fetch` and `proxy` behave like the IDE when `runtime.adt_plugins_dir` is configured.

## Install

**Windows:** Scoop's `bucket add` does not parse `#branch` in the URL, so clone the `scoop-bucket` branch first and add the local checkout as a bucket:

```powershell
git clone -b scoop-bucket --depth 1 https://github.com/abapify/openadt openadt-bucket
scoop bucket add openadt .\openadt-bucket\packaging\scoop
scoop install openadt
```

Or skip the bucket and install directly from the manifest URL: `scoop install https://raw.githubusercontent.com/abapify/openadt/scoop-bucket/openadt.json`

**Linux / macOS:**

```bash
brew tap abapify/openadt https://github.com/abapify/openadt.git
brew install openadt
```

Later: `brew update && brew upgrade openadt`

Build from source: [docs/usage.md](docs/usage.md#install-openadt-today).

## Start in three commands

```bash
openadt config bootstrap    # detect landscape + write ~/.openadt/config.toml
openadt proxy DEV --listen 127.0.0.1:8080
openadt fetch DEV /sap/bc/adt/discovery --pretty
```

Use fictional system aliases in docs and tests (`DEV`, `DEVELOPER`, `dev-ms.example.com`). Your real `~/.openadt/config.toml` stays on your machine.

## MCP (preview)

Experimental stdio MCP for agents: see [specs/mcp.md](specs/mcp.md) and [tools/mcp-bridge/](tools/mcp-bridge/).

## Fallback transports

| `adt.transport` | When                                       |
| --------------- | ------------------------------------------ |
| `sdk` (default) | `runtime.adt_plugins_dir` set — preferred  |
| `http`          | Explicit opt-in; browser SSO / ticket HTTP |
| `rest-rfc`      | JCo present, no ADT plugins                |

## Advanced

WSL, devcontainer, Secure Login, and troubleshooting: [docs/usage.md](docs/usage.md).

Specs: [specs/](specs/) · Architecture: [apps/ARCHITECTURE.md](apps/ARCHITECTURE.md) · Agents: [AGENTS.md](AGENTS.md)
