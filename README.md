<div align="center">

![OpenADT banner](docs/assets/openadt-banner.svg)

# OpenADT

**SAP ADT from the terminal — same SDK, JCo, and SNC logon stack as Eclipse.**

[![Latest release](https://img.shields.io/github/v/release/abapify/openadt?label=release&sort=semver)](https://github.com/abapify/openadt/releases)
[![License](https://img.shields.io/github/license/abapify/openadt)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/abapify/openadt/ci.yml?branch=main&label=CI)](https://github.com/abapify/openadt/actions/workflows/ci.yml)
[![Platforms](https://img.shields.io/badge/platforms-Windows%20%7C%20Linux%20%7C%20macOS-blue)](#-install)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](apps/ARCHITECTURE.md)

[**Install**](#-install) · [**Quick start**](#-quick-start) · [**MCP for AI agents**](#-mcp-for-ai-agents) · [**Docs**](docs/usage.md)

</div>

---

## What is OpenADT?

OpenADT is a **thin Java wrapper around the official SAP ADT SDK** (`com.sap.adt.*`). It reuses the same destination, session, and logon stack Eclipse ADT uses — so the same SAP authentication that works in your IDE works in your terminal, in scripts, in CI, and in AI agents.

| You want to…                                                  | OpenADT gives you…                                        |
| ------------------------------------------------------------- | --------------------------------------------------------- |
| Call `/sap/bc/adt/...` from a script or `curl`                | `openadt fetch DEV /sap/bc/adt/...`                      |
| Give VS Code / IntelliJ a local ADT endpoint                  | `openadt proxy DEV --listen 127.0.0.1:8080`               |
| Let Cursor / Claude / Devin call SAP tools over MCP           | `openadt mcp serve --stdio` (or `:2236/mcp` for HTTP)     |
| Skip hand-writing JCo / SNC / SSO config                      | `openadt config bootstrap` — auto-detect from SAP GUI     |
| Run many requests fast against a single warm SAP session      | `openadt proxy` once, then `openadt fetch` reuses it      |

> [!NOTE]
> **OpenADT does not implement MCP tools itself.** It launches the **official SAP ADT MCP server** (from the SAP ADT VS Code extension) and bridges it to any agent. Tool names and schemas come from SAP, not from OpenADT.

---

## Table of contents

| #   | Section                                                    |
| --- | ---------------------------------------------------------- |
| 1   | [Install](#-install)                                      |
| 2   | [Quick start](#-quick-start)                              |
| 3   | [Using `fetch` and `proxy`](#-using-fetch-and-proxy)       |
| 4   | [Configuration](#-configuration)                          |
| 5   | [Auto-detection from Eclipse &amp; SAP GUI](#-auto-detection) |
| 6   | [Transport modes](#-transport-modes)                      |
| 7   | [MCP for AI agents](#-mcp-for-ai-agents)                  |
| 8   | [Using OpenADT with ABAP FS](#-using-openadt-with-abap-fs) |
| 9   | [Troubleshooting](#-troubleshooting)                      |
| 10  | [Contributing & further reading](#-contributing--further-reading) |

---

## 📦 Install

OpenADT runs on **Windows**, **Linux**, and **macOS** on the **host OS that owns your JCo native library**. JCo and SAP ADT plugins are **not** bundled — see [SAP prerequisites](#sap-prerequisites) below.

> [!IMPORTANT]
> **JCo natives must match the OS that runs `openadt`.** A Linux JVM in WSL cannot load `sapjco3.dll` from a Windows install. If you develop in WSL but SAP tooling is on Windows, run `openadt` on Windows for `fetch` / `proxy` / `mcp serve` against a real landscape. See [docs/usage.md → WSL and devcontainers](docs/usage.md#wsl-and-devcontainers).

<details open>
<summary><strong>Windows — Scoop</strong></summary>

```powershell
scoop bucket add openadt https://github.com/abapify/scoop-bucket
scoop install openadt
```

Or without adding a bucket:

```powershell
scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json
```

Upgrade later: `scoop update openadt`.

</details>

<details>
<summary><strong>Linux &amp; macOS — Homebrew</strong></summary>

```bash
brew tap abapify/openadt
brew install openadt
```

Upgrade later: `brew update && brew upgrade openadt`.

</details>

<details>
<summary><strong>Build from source</strong></summary>

For a clone-based install (JDK 17+ and Maven):

```bash
git clone https://github.com/abapify/openadt.git
cd openadt
./mvnw -q verify -Pdistribution
java -jar apps/openadt-cli/target/openadt-*.jar --version
```

Full dev guide: [docs/contributing.md](docs/contributing.md). Release channels: [specs/packaging.md](specs/packaging.md).

</details>

### SAP prerequisites

OpenADT is a thin wrapper — it does **not** ship JCo, CryptoLib, or ADT plugins. Install what your auth path needs:

| Component                                   | Needed when                              |
| ------------------------------------------- | ---------------------------------------- |
| **SAP JCo 3.x** (jar + native for your OS)  | `sdk` or `rest-rfc` transport            |
| **SAP CryptoLib / `sapcrypto`** (native)    | SNC destinations (`authentication_kind = "sso"`) |
| **SAP Secure Login Client** (optional)      | Windows SNC SSO with smartcard / Kerberos |
| **VS Code + SAP ADT extension** *(optional)* | Only for `openadt mcp serve`             |
| **SAP GUI / NWBC / Eclipse ADT** *(optional)* | Improves `config bootstrap` auto-detection — see [Auto-detection](#-auto-detection) |

> [!TIP]
> If you already use SAP GUI or Eclipse ADT against the same systems, you already have most of the runtime files. `openadt config bootstrap` will find them.

---

## 🚀 Quick start

On the **host** that owns JCo and the licensed ADT plugins (Windows, Linux, or macOS):

```bash
# 1. Detect SAP systems + JCo from SAP GUI / Eclipse config
openadt config bootstrap

# 2. Build the SDK runtime jar (required for the default SDK transport)
openadt config build

# 3. Make a single ADT request
openadt fetch DEV /sap/bc/adt/discovery --pretty

# 4. Or start a local proxy and reuse a warm SAP session
openadt proxy DEV --listen 127.0.0.1:8080
# (in another shell)
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json
```

> [!NOTE]
> `DEV` is a **fictional system alias** used throughout this README. Replace it with your own system alias from `openadt config`. Real examples in this repo use only `DEV` and `dev-ms.example.com` — see [Security](#security--redaction).

For a deeper walkthrough including the ABAP FS integration and HTTP browser-SSO: [docs/usage.md](docs/usage.md).

---

## 🛠 Using `fetch` and `proxy`

### `openadt fetch` — one request

```bash
# Discovery document
openadt fetch DEV /sap/bc/adt/discovery --pretty

# System info as JSON
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail

# With explicit headers (e.g. for sources / syntax trees)
openadt fetch DEV /sap/bc/adt/programs/programs \
  --header "Accept: application/vnd.sap.adt.programs.v1+xml" --include

# POST a body from a file
openadt fetch DEV /sap/bc/adt/example --method POST \
  --body @request.xml --header "Content-Type: application/xml"
```

Useful flags: `--method`, `--header`, `--body @file`, `--output <file>`, `--include`, `--fail`, `--pretty`, `--raw`, `--direct` (bypass a running proxy), `--no-cache` (HTTP SSO only).

### `openadt proxy` — local ADT endpoint

```bash
# Open (no local auth) — fine for personal dev
openadt proxy DEV --listen 127.0.0.1:8080

# With local Basic auth — recommended for VS Code / shared boxes
export OPENADT_PROXY_PASSWORD="choose-a-local-secret"
openadt proxy DEV --listen 127.0.0.1:8080 --local-auth basic --local-username openadt
```

Now point any client at `http://127.0.0.1:8080/sap/bc/adt/...` — for example:

```bash
curl -u openadt:choose-a-local-secret http://127.0.0.1:8080/sap/bc/adt/discovery
```

| Client                                | Connect with                                 |
| ------------------------------------- | -------------------------------------------- |
| VS Code [ABAP FS](docs/integrations/abap-fs.md) | Proxy URL + local Basic creds         |
| IntelliJ + ADT plugin                 | Proxy URL + local Basic creds                |
| `curl` / scripts / CI                 | Proxy URL + local Basic creds                |
| MCP-supporting agents (HTTP)          | `openadt mcp serve` — see [below](#-mcp-for-ai-agents) |

> [!TIP]
> The local Basic credentials are **not** SAP users. They protect the loopback only. While the proxy is running, `openadt fetch` for the same system/profile reuses the warm session automatically — no cold JVM/SDK startup per call. Use `fetch --direct` to bypass the proxy and go straight to SAP.

Full command reference: [specs/cli.md](specs/cli.md). Proxy contract (header stripping, auth): [specs/proxy.md](specs/proxy.md).

---

## ⚙️ Configuration

OpenADT reads TOML config from (in order, **last-wins merge**):

1. `$OPENADT_CONFIG`
2. `./.openadt/config.toml`
3. `~/.openadt/config.toml` (or `%USERPROFILE%\.openadt\` on Windows)

After `openadt config bootstrap`, the typical layout is:

```text
~/.openadt/
├── config.toml                  # entrypoint, includes the fragments below
├── destinations/
│   └── detected.openadt.toml    # systems auto-detected from SAP GUI / Eclipse
└── local.openadt.toml           # local runtime paths (JCo, sapcrypto, adt_plugins_dir)
```

### Minimal entrypoint

```toml
version = 1

[merge]
strategy = "last-wins"
includes = [
  "destinations/*.openadt.toml",
  "local.openadt.toml"
]
```

### A destination (fictional `DEV`)

```toml
version = 1

[destinations.DEV]
alias         = "DEV"
system_id     = "DEV"
client        = "100"
language      = "EN"
user          = "DEVELOPER"

[destinations.DEV.adt]
transport           = "sdk"   # default — see Transport modes below
authentication_kind = "sso"   # uses SNC SSO
```

Inspect the merged result anytime:

```bash
openadt config           # paths, systems, runtime — secrets redacted
openadt config bootstrap --check   # detect without writing
```

Full schema: [specs/config.md](specs/config.md). Profiles (e.g. an extra `sso` HTTP profile alongside the detected SNC one): `openadt config destinations create --help`.

---

<a id="auto-detection"></a>

## 🪄 Auto-detection from Eclipse &amp; SAP GUI

`openadt config bootstrap` runs a set of **detectors** that read your existing SAP tooling config and write `destinations/detected.openadt.toml` + `local.openadt.toml`. You do not have to type JCo host, sysnr, or SNC names by hand.

| Detector                         | Reads                                                                              | What you get |
| -------------------------------- | ---------------------------------------------------------------------------------- | ------------ |
| `SapGuiLandscapeDetector`        | `SAPUILandscape.xml` (incl. `LogonServerConfigCache/*.xml`)                        | Systems with `ashost`/`sysnr` **or** message-server `mshost`/`msserv`/`group`/`r3name` for load-balanced entries; SNC name auto-enables SNC SSO |
| `NwbcSystemDetector`             | `%APPDATA%\SAP\NWBC\Recents\*.recents`                                             | Fills missing `client` and `system_id` from recent connections |
| `EclipseAdtDetector`             | Eclipse ADT workspace `.destination.properties` files                              | Eclipse ADT destinations surfaced as OpenADT systems |
| `SapRulesDetector`               | `%APPDATA%\SAP\Common\saprules.xml`                                                | Adds the **ADT frontend host** observed in real Eclipse sessions (`adt.ashost`, `adt.base_url`) |
| `RuntimeDetector`                | JCo jars, `sapjco3.dll` / `.so` / `.dylib`, `sapcrypto.*`, Eclipse p2 plugin pools | `[runtime]` paths: `jco_jar`, `jco_native_dir`, `sapcrypto`, `adt_plugins_dir` |
| `SecureLoginDetector`            | Probes `https://127.0.0.1:34443` (SAP Secure Login Client hub)                     | Records hub reachability (no system profiles) |

Lookup paths per OS:

| OS      | Common config root                                |
| ------- | ------------------------------------------------- |
| Windows | `%APPDATA%\SAP\Common\`, Eclipse p2 under `%USERPROFILE%` |
| Linux   | Same as Windows when in WSL: `/mnt/c/Users/<user>/AppData/Roaming/SAP/...` |
| macOS   | `~/Library/Application Support/SAP/Common/`       |

After detection, the analyzer applies conservative defaults:

- `alias = system_id` when missing
- `user = <current OS user, UPPERCASE>` when missing
- `language = "EN"` when missing
- `adt.transport = "sdk"` when `adt_plugins_dir` is present, else `"rest-rfc"` when JCo is present, else `"sdk"` (default for HTTP/manual config)
- `jco.sticky = 1`, `jco.deny_initial_password = 1` for SNC SSO profiles
- `authentication_kind = "sso"` when SNC SSO is enabled

> [!NOTE]
> Detected JCo / `sapcrypto` / Secure Login are **optional prerequisites** — bootstrap does not fail when they are missing. Destinations can still be written and used with HTTP ticket auth (`adt.transport = "http"`).

Spec: [specs/setup.md](specs/setup.md). Linux-in-WSL quirks: [docs/usage.md → WSL and devcontainers](docs/usage.md#wsl-and-devcontainers).

---

## 🚦 Transport modes

| `adt.transport`  | Stack                                                                                  | When to use                                                        |
| ---------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `sdk` (default)  | `com.sap.adt.*` over JCo + SNC (Eclipse-parity)                                       | You have SAP JCo + ADT plugins. **Best for SNC SSO and full session semantics.** |
| `http`           | Direct ICF/SAML frontend, `MYSAPSSO2` ticket (or browser SSO)                          | Public cloud / ABAP Environment, or when JCo is unavailable        |
| `rest-rfc`       | RFC bridge `SADT_REST_RFC_ENDPOINT` over JCo                                           | JCo available, ADT plugin pool not (legacy fallback)               |

Default selection: `sdk` when `[runtime].adt_plugins_dir` is configured, else `rest-rfc` when only JCo is detected, else `sdk` (so HTTP/manual config still gets the SDK path when plugins are added later).

> [!TIP]
> Run `openadt proxy` once for the heaviest system. Subsequent `openadt fetch` calls reuse its warm SAP session and are dramatically faster than cold SDK startup — even across separate `fetch` invocations.

---

## 🤖 MCP for AI agents

OpenADT exposes the **official SAP ADT MCP server** to any MCP-compatible client. There are two ways an agent can talk to it:

| Transport    | How the agent connects                          | When to use                                  |
| ------------ | ----------------------------------------------- | -------------------------------------------- |
| **HTTP MCP** | `http://localhost:2236/mcp` + `Authorization: Bearer <token>` | IDEs / agents that support HTTP MCP natively |
| **stdio**    | `command: "openadt"`, `args: ["mcp","serve","--stdio"]`      | CLI agents that only speak stdio JSON-RPC    |

> [!IMPORTANT]
> **SAP only ships an HTTP MCP server.** The stdio transport in OpenADT is a **bridge** — it forwards Content-Length / NDJSON frames on stdin/stdout to the same HTTP endpoint with a Bearer token. No MCP tools are defined by OpenADT; everything comes from SAP's `adt-lsc`.

### 1. Install the SAP ADT extension in VS Code (one-time)

> The extension provides the `adt-lsc` binary that OpenADT launches headlessly. **VS Code does not need to stay open** during normal operation — the install makes the binary available on disk.

1. Install [Visual Studio Code](https://code.visualstudio.com/download).
2. Install [ABAP Development Tools for VS Code](https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode) (id: `SAPSE.adt-vscode`).
3. Create a destination in VS Code: **Command Palette → ABAP: New Destination → RFC** (on-prem / private cloud) **or HTTP** (public cloud).

The destination is stored in `~/.adtls/destinations.json` and picked up automatically by `openadt mcp serve`.

### 2. Start the MCP server

```bash
# stdio — point an agent at this command
openadt mcp serve --stdio

# HTTP — agent connects to http://localhost:2236/mcp with a Bearer token
openadt mcp serve --port 2236 --show-token
```

Flags worth knowing: `--port` (default `2236`), `--destination` (pre-select a destination), `--verbose` (LSP trace → `~/.openadt/logs/mcp-serve.log`), `--show-token` (prints Bearer token to stdout).

### 3. Configure your agent (`mcp.json`)

<details open>
<summary><strong>Cursor</strong> — <code>.cursor/mcp.json</code> (project) or <code>~/.cursor/mcp.json</code> (global)</summary>

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "openadt",
      "args": ["mcp", "serve", "--stdio"]
    }
  }
}
```

</details>

<details>
<summary><strong>Claude Desktop</strong> — <code>claude_desktop_config.json</code></summary>

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "openadt",
      "args": ["mcp", "serve", "--stdio"]
    }
  }
}
```

</details>

<details>
<summary><strong>Devin CLI</strong> — <code>.devin/config.json</code></summary>

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "openadt",
      "args": ["mcp", "serve", "--stdio"]
    }
  }
}
```

</details>

<details>
<summary><strong>HTTP-native agents</strong> — start once, then point the agent at the URL</summary>

```bash
openadt mcp serve --port 2236 --show-token
# prints: Bearer <token>
```

```json
{
  "url": "http://localhost:2236/mcp",
  "headers": { "Authorization": "Bearer <token>" }
}
```

</details>

### 4. Status and introspection

```bash
openadt mcp status --port 2236           # is the server up?
openadt mcp list                         # active endpoints on this host
openadt mcp print-config --port 2236     # JSON { url, headers } for HTTP clients
```

Reference: [SAP ADT MCP tools (help.sap.com)](https://help.sap.com/docs/abap-cloud/abap-development-tools-for-visual-studio-code/mcp-tools). Spec: [specs/mcp.md](specs/mcp.md). Per-agent cookbook: [docs/usage.md → MCP](docs/usage.md#mcp).

---

## 🗂 Using OpenADT with ABAP FS

[ABAP FS](https://marcellourbani.github.io/vscode_abap_remote_fs/) is the most full-featured ADT filesystem integration for VS Code. It expects an ADT **URL + HTTP Basic auth**. On SNC/SSO landscapes, **do not store your SAP password in VS Code** — run `openadt proxy` with a **local-only** Basic credential and point ABAP FS at the loopback.

```bash
# 1. Start the proxy with local Basic auth
export OPENADT_PROXY_PASSWORD="choose-a-local-secret"
openadt proxy DEV --listen 127.0.0.1:8080 \
  --local-auth basic --local-username openadt

# 2. In VS Code settings.json
```
```json
{
  "abapfs.remote": {
    "DEV": {
      "url":      "http://127.0.0.1:8080",
      "username": "openadt",
      "password": "choose-a-local-secret",
      "client":   "100",
      "language": "EN"
    }
  }
}
```

> [!NOTE]
> **ABAP FS MCP (`localhost:4847`) is a separate, VS Code-internal MCP** — it does not go through OpenADT and requires VS Code to stay open. OpenADT's path is the **HTTP ADT endpoint** above. If an agent needs MCP, use `openadt mcp serve` instead of (or in addition to) the ABAP FS in-editor MCP.

Step-by-step for Windows / Linux / macOS, including the `curl` smoke test: [docs/integrations/abap-fs.md](docs/integrations/abap-fs.md).

---

## 🩺 Troubleshooting

> Looking for a quick check? Run `openadt config` to see effective paths and runtime status. Enable diagnostics with `OPENADT_VERBOSE=true`.

| Symptom                                              | Likely cause                                            | Fix                                                                                 |
| ---------------------------------------------------- | ------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `JCo jar not configured`                            | Missing runtime fragment                                | `openadt config bootstrap`; check `~/.openadt/local.openadt.toml`                   |
| `no sapjco3 in java.library.path`                    | Native lib for a different OS in `jco_native_dir`       | Run setup on the **host** OS; Windows needs `sapjco3.dll`, Linux `libsapjco3.so`    |
| `Illegal JCo archive`                                | Eclipse p2 jar name (not the canonical `sapjco3.jar`)   | Re-run `openadt config build` after `bootstrap`                                      |
| `GSS-API: No credentials`                            | SNC material missing on the host                        | Install Secure Login on Windows, or set up `SECUDIR` on Linux                       |
| `MCP / Bearer 401` from SAP                          | Wrong or rotated token                                  | `openadt mcp status --port <port>`; restart `openadt mcp serve` to rotate the token |
| `tools/list` empty or MCP start hangs                | Logon never completed in `adt-lsc`                      | Wait for Secure Login / browser SSO; check `~/.openadt/logs/mcp-serve.log`          |
| `Connection refused` on `127.0.0.1:8080`             | Proxy not running                                       | Start `openadt proxy` in another shell                                              |
| `fetch` and `proxy` disagree on auth                 | Different transport / profile between the two           | Compare `openadt config`, alias, `adt.transport`; pass `--profile` explicitly       |
| `ClassNotFoundException` for `com.sap.adt.…`         | SDK runtime not built                                   | `openadt config build`                                                              |
| Discovery empty                                      | Not logged on                                           | `openadt auth login DEV` first                                                       |
| Browser SSO loop                                     | Frontend needs manual session step before ADT ticket    | Set `browser_entry_url` (or `OPENADT_HTTP_BROWSER_ENTRY_URL`) to a working page      |

<details>
<summary><strong>Verbose diagnostics</strong></summary>

```bash
# Linux / macOS
export OPENADT_VERBOSE=true

# Windows PowerShell
$env:OPENADT_VERBOSE = "true"

openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

SDK/JCo/SNC, HTTP SSO, proxy, and destination diagnostics land on **stderr** — body stays on **stdout** (combine with `--pretty --raw` for clean scripts).

</details>

### Security & redaction

Before sharing logs, configs, or screenshots:

- Remove real **SIDs, hosts, users, SNC names, tickets, and tokens**.
- Use the **fictional** fixtures in this repo as templates: `DEV`, `DEVELOPER`, `dev-ms.example.com`.
- **Never commit** SAP binaries, `~/.openadt/`, or generated devcontainer paths.

---

## 🤝 Contributing & further reading

**Want to hack on OpenADT?** Clone, build, run tests, and ship a PR: [docs/contributing.md](docs/contributing.md). PR checklist: [CONTRIBUTING.md](CONTRIBUTING.md). Vulnerability reporting: [SECURITY.md](SECURITY.md).

| Topic                       | Where to read                                                                  |
| --------------------------- | ------------------------------------------------------------------------------ |
| Using the installed CLI     | [docs/usage.md](docs/usage.md)                                                 |
| ABAP FS step-by-step        | [docs/integrations/abap-fs.md](docs/integrations/abap-fs.md)                   |
| Developing from a clone     | [docs/contributing.md](docs/contributing.md)                                   |
| Proxy contract              | [specs/proxy.md](specs/proxy.md)                                               |
| CLI commands & flags        | [specs/cli.md](specs/cli.md)                                                   |
| Config schema               | [specs/config.md](specs/config.md)                                             |
| Auto-detect detectors       | [specs/setup.md](specs/setup.md)                                               |
| MCP launcher spec           | [specs/mcp.md](specs/mcp.md)                                                   |
| SAP ADT SDK services        | [specs/sdk-services.md](specs/sdk-services.md)                                 |
| Product scope / roadmap     | [specs/vision.md](specs/vision.md)                                             |
| Packaging & releases        | [specs/packaging.md](specs/packaging.md)                                       |
| Java module / package map   | [apps/ARCHITECTURE.md](apps/ARCHITECTURE.md)                                   |
| Spec-driven dev workflow    | [DESIGN.md](DESIGN.md) · [specs/](specs/)                                      |

### What OpenADT is not

- Not an Eclipse ADT or SAP GUI replacement.
- Not a per-request landscape scanner — bootstrap writes config once.
- Not a redistribution of SAP JCo, ADT plugins, or Secure Login.

---

## 📄 License

[Apache License 2.0](LICENSE). SAP trademarks belong to their respective owners; this project is not affiliated with SAP SE.
