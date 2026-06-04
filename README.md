<div align="center">

![OpenADT banner](docs/assets/openadt-banner.svg)

# OpenADT

**Run SAP ABAP Development Tools (ADT) from the terminal — with the same SDK and logon stack as Eclipse.**

[![Latest release](https://img.shields.io/github/v/release/abapify/openadt?label=release&sort=semver)](https://github.com/abapify/openadt/releases)
[![License](https://img.shields.io/github/license/abapify/openadt)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/abapify/openadt/ci.yml?branch=main&label=CI)](https://github.com/abapify/openadt/actions/workflows/ci.yml)
[![Platforms](https://img.shields.io/badge/platforms-Windows%20%7C%20Linux%20%7C%20macOS-blue)](#install)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](apps/ARCHITECTURE.md)

[Quick start](#quick-start) · [Install](#install) · [ABAP FS + OpenADT](#abap-fs-and-other-adt-clients--openadt) · [Usage guide](docs/usage.md) · [Specs](specs/) · [Report issue](https://github.com/abapify/openadt/issues)

</div>

---

## Why OpenADT exists

SAP ships ADT as **Eclipse plugins** on top of **JCo destinations** and corporate SSO (SNC, Secure Login, tickets). That stack works in the IDE, but it is awkward for everything else:

| You want to…                                        | Without OpenADT                                                     | With OpenADT                                                                |
| --------------------------------------------------- | ------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Hit `/sap/bc/adt/...` from a script or CI job       | Reimplement auth, cookies, CSRF, SNC — or copy fragile curl recipes | `openadt fetch DEV /sap/bc/adt/discovery`                                   |
| Point a local tool at SAP (extension, test harness) | Teach it SAP logon protocols                                        | `openadt proxy DEV --listen 127.0.0.1:8080` and use plain HTTP to localhost |
| Let an AI agent read ADT APIs safely                | Give the model raw landscape credentials or custom HTTP hacks       | MCP bridge over `fetch` ([preview](#mcp-preview))                           |

**OpenADT is not another ADT HTTP client.** It is a thin **Java wrapper around the official `com.sap.adt.*` SDK** so `fetch` and `proxy` behave like Eclipse ADT when `runtime.adt_plugins_dir` is configured — including destination selection and SNC SSO where your machine already has JCo and crypto libraries.

OpenADT does **not** bundle SAP software. You install licensed JCo, ADT plugins, and landscape config from SAP or your organization; OpenADT detects and wires them once via `openadt config bootstrap`.

---

## How it fits in your stack

```mermaid
flowchart LR
  subgraph local["Your machine"]
    CLI["Scripts / curl / IDE plugin"]
    OA["openadt fetch / proxy"]
    CFG["~/.openadt/config.toml"]
  end
  subgraph sapstack["Same stack as Eclipse ADT"]
    SDK["SAP ADT SDK com.sap.adt.*"]
    JCO["SAP JCo + SNC / SSO"]
  end
  SAP["SAP ABAP system /sap/bc/adt/..."]

  CLI --> OA
  OA --> CFG
  OA --> SDK
  SDK --> JCO
  JCO --> SAP
```

- **`openadt fetch`** — one ADT request from the terminal (JSON-friendly output for automation).
- **`openadt proxy`** — localhost HTTP reverse bridge; callers speak HTTP to `127.0.0.1`, OpenADT speaks ADT to SAP.
- **`openadt config bootstrap`** — detect systems and runtime paths, write config (no rescan on every request).

---

## Quick start

After [install](#install), on a machine that already has SAP ADT/Eclipse or staged JCo (see [usage guide](docs/usage.md)):

```bash
openadt config bootstrap    # detect landscape → ~/.openadt/config.toml
openadt proxy DEV --listen 127.0.0.1:8080
openadt fetch DEV /sap/bc/adt/discovery --pretty
```

Use fictional aliases in docs and tests (`DEV`, `DEVELOPER`, `dev-ms.example.com`). Your real `~/.openadt/config.toml` stays local and is not part of this repo.

---

## Install

<table>
<tr>
<th>Windows (Scoop)</th>
<th>Linux / macOS (Homebrew)</th>
</tr>
<tr>
<td>

```powershell
scoop bucket add openadt https://github.com/abapify/scoop-bucket
scoop install openadt
```

One-shot (no bucket):

```powershell
scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json
```

</td>
<td>

```bash
brew tap abapify/openadt
brew install openadt
brew update && brew upgrade openadt   # later
```

</td>
</tr>
</table>

Build from source or corporate mirrors: [docs/usage.md#install-openadt-today](docs/usage.md#install-openadt-today) · [specs/packaging.md](specs/packaging.md).

---

## Commands at a glance

| Command                               | Purpose                                                                      |
| ------------------------------------- | ---------------------------------------------------------------------------- |
| `openadt fetch`                       | Single ADT HTTP call through the SDK (or explicit fallback transport)        |
| `openadt proxy`                       | Localhost bridge for tools that only speak HTTP                              |
| `openadt config` / `config bootstrap` | Show or generate merged TOML config                                          |
| `openadt setup`                       | Legacy entry point; prefer `config bootstrap` + [setup spec](specs/setup.md) |

Full CLI contract: [specs/cli.md](specs/cli.md).

---

## Transport modes

Default is **SDK** (Eclipse-parity). Fallbacks are opt-in:

| `adt.transport`     | When to use                                                             |
| ------------------- | ----------------------------------------------------------------------- |
| `sdk` (**default**) | `runtime.adt_plugins_dir` set — preferred                               |
| `http`              | Explicit opt-in; browser SSO / `MYSAPSSO2` ticket HTTP without full SDK |
| `rest-rfc`          | JCo present but no ADT plugin pool                                      |

Details: [specs/cli.md](specs/cli.md) · [specs/config.md](specs/config.md).

---

## MCP preview

Experimental **stdio MCP** so agents (Cursor, Claude, Copilot, etc.) can call ADT via `adt_fetch` / `adt_discover` without reimplementing SAP logon.

- Spec: [specs/mcp.md](specs/mcp.md)
- Bridge: [tools/mcp-bridge/](tools/mcp-bridge/)

---

## ABAP FS and other ADT clients + OpenADT

[**ABAP FS**](https://marcellourbani.github.io/vscode_abap_remote_fs/) (ABAP remote filesystem for VS Code) and similar tools talk to SAP over **ADT HTTP**. They normally expect a **base URL**, **SAP client**, and often **HTTP Basic auth** in the connection profile. On corporate landscapes with **SNC / Secure Login / SSO**, storing a SAP password in the editor is the wrong model — OpenADT handles SAP logon once; the tool only speaks to **localhost**.

```mermaid
flowchart LR
  Client["ABAP FS or ADT HTTP client"]
  Proxy["openadt proxy\nlocal Basic auth"]
  SAP["SAP /sap/bc/adt/..."]

  Client -->|"http://127.0.0.1:8080"| Proxy
  Proxy -->|"SDK + JCo / SNC"| SAP
```

### 1. Start OpenADT with local Basic auth

After `openadt config bootstrap` (and `openadt config build` when using SDK transport), run the proxy on loopback. The username/password here **only protect the local listener** — they are **not** your SAP user (see [specs/proxy.md](specs/proxy.md)).

```bash
export OPENADT_PROXY_PASSWORD="choose-a-local-secret"
openadt proxy DEV --listen 127.0.0.1:8080 --local-auth basic --local-username openadt
```

Keep this terminal open while VS Code or MCP clients are connected.

### 2. Point ABAP FS at the proxy

In VS Code, use [**ABAP FS: Connection Manager**](https://marcellourbani.github.io/vscode_abap_remote_fs/) (or `abapfs.remote` in settings) and set the **ADT base URL** to the proxy root — **no** `/sap/bc/adt` suffix; the extension adds ADT paths under that host.

Example `settings.json` fragment (use fictional hostnames in samples; your real config stays local):

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

Use the same **local** username/password as `--local-username` / `OPENADT_PROXY_PASSWORD`. OpenADT strips incoming `Authorization` and SAP session headers before forwarding, then logs on to SAP with your configured SDK/SNC profile.

Verify from another terminal:

```bash
curl -u openadt:choose-a-local-secret http://127.0.0.1:8080/sap/bc/adt/discovery
```

### 3. ABAP FS MCP vs OpenADT

ABAP FS also ships an [**in-editor MCP server**](https://marcellourbani.github.io/vscode_abap_remote_fs/) (`abapfs.mcpServer`, default port `4847`) with optional **Bearer `apiKey`** — that secures access **to the MCP server inside VS Code**, not SAP itself. VS Code must stay open and connected to SAP.

| Integration                                                              | Role of OpenADT                                                                           |
| ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| **ABAP FS ADT connection** (browse/activate/debug in VS Code)            | Point `url` at `openadt proxy`; SAP auth via OpenADT                                      |
| **ABAP FS MCP** (`http://localhost:4847/mcp`)                            | No OpenADT in the path; use when you want ABAP FS tools inside VS Code                    |
| **Other MCP or HTTP clients** that require Basic auth to an ADT base URL | Same pattern: ADT URL → `http://127.0.0.1:<port>` + local Basic → running `openadt proxy` |

For proxy flags, profiles (`--profile snc`), and troubleshooting, see [docs/usage.md#start-the-local-proxy](docs/usage.md#start-the-local-proxy).

---

## What OpenADT is not

- A replacement for Eclipse ADT or SAP GUI
- A landscape scanner on every request (bootstrap writes config once)
- A redistribution of SAP JCo, ADT plugins, or Secure Login — **you** supply licensed installs

Product vision and package map: [specs/vision.md](specs/vision.md) · [apps/ARCHITECTURE.md](apps/ARCHITECTURE.md).

---

## Documentation

| Topic                                       | Link                           |
| ------------------------------------------- | ------------------------------ |
| Install, WSL, devcontainer, troubleshooting | [docs/usage.md](docs/usage.md) |
| Behavior specs                              | [specs/](specs/)               |
| Contributing / agents                       | [AGENTS.md](AGENTS.md)         |
| Security                                    | [SECURITY.md](SECURITY.md)     |

---

## License

[Apache License 2.0](LICENSE) — Copyright contributors. SAP, ABAP, and ADT are trademarks of their respective owners; this project is not affiliated with SAP SE.
