# Using OpenADT

This guide is for **using an installed OpenADT CLI** (Scoop, Homebrew, or a release zip on your `PATH`). It covers SAP setup, config, `fetch`, `proxy`, and troubleshooting.

**Working from a git clone** (build, tests, devcontainer): [contributing.md](contributing.md).

Overview: [README.md](../README.md). Specs: [specs/README.md](../specs/README.md).

## Contents

| Topic                        | Section                                            |
| ---------------------------- | -------------------------------------------------- |
| Platforms                    | [Supported platforms](#supported-platforms)        |
| Install CLI                  | [Install](#install)                                |
| SAP software on your machine | [SAP prerequisites](#sap-prerequisites)            |
| First config                 | [First setup](#first-setup)                        |
| Config files                 | [Config](#config)                                  |
| `fetch`                      | [Fetch](#fetch)                                    |
| `proxy` / IDE clients        | [Local proxy](#local-proxy)                        |
| ABAP FS                      | [integrations/abap-fs.md](integrations/abap-fs.md) |
| WSL / devcontainer           | [WSL and devcontainers](#wsl-and-devcontainers)    |
| Problems                     | [Troubleshooting](#troubleshooting)                |
| Sharing logs safely          | [Security](#security)                              |

<a id="supported-platforms"></a>

## Supported platforms

| OS      | Setup detection                                            | `fetch` / `proxy` natives                |
| ------- | ---------------------------------------------------------- | ---------------------------------------- |
| Windows | SAP GUI / NWBC / Eclipse paths                             | `sapjco3.dll`, `sapcrypto.dll`           |
| Linux   | Staged or user paths; WSL can read `/mnt/c/...` for config | `libsapjco3.so`, `libsapcrypto.so`       |
| macOS   | `~/Library/Application Support/SAP/...`                    | `libsapjco3.dylib`, `libsapcrypto.dylib` |

Run `fetch` and `proxy` on the OS that owns the JCo native library. Linux Java in WSL cannot load Windows DLLs.

<a id="install"></a>

## Install

Packaging details: [specs/packaging.md](../specs/packaging.md). Build from source: [contributing.md#build-from-source](contributing.md#build-from-source).

### Windows — Scoop

```powershell
scoop bucket add openadt https://github.com/abapify/scoop-bucket
scoop install openadt
```

Without a bucket:

```powershell
scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json
```

Upgrade: `scoop update openadt`

### Linux and macOS — Homebrew

```bash
brew tap abapify/openadt
brew install openadt
```

Upgrade: `brew update && brew upgrade openadt`

Scoop/Homebrew install the CLI and suggest a JDK. **SAP JCo, ADT plugins, Secure Login, and landscape data are not bundled.**

<a id="sap-prerequisites"></a>

## SAP prerequisites

Install from SAP or your organization for the auth path you use:

| Component                    | When                            |
| ---------------------------- | ------------------------------- |
| SAP GUI / NWBC / Eclipse ADT | Helps `openadt setup` detection |
| SAP JCo 3.x (jar + native)   | `sdk` or `rest-rfc` transport   |
| SAP CryptoLib / `sapcrypto`  | SNC destinations                |
| SAP Secure Login Client      | Common on Windows SNC SSO       |

**Linux:** `libsapjco3.so`, `libsapcrypto.so`, Linux-visible `SECUDIR` for SNC.

**macOS:** Native libs + Eclipse/ADT or manual config under `~/Library/Application Support/SAP/`.

### Runtime options

- **SNC SSO** — JCo, matching native lib, `sapcrypto` on the **host** OS.
- **`rest-rfc`** — JCo (+ SNC when destination requires it).
- **`http`** — configured `base_url` and `MYSAPSSO2` ticket source; no JCo required.
- **Proxy `--local-auth basic`** — protects localhost only; not SAP logon.

Prefer the full SDK stack when you need Eclipse ADT parity.

<a id="first-setup"></a>

## First setup

Typical flow after install:

```bash
openadt config bootstrap
openadt config build          # SDK transport: builds ~/.openadt/runtime jar
openadt proxy DEV             # optional: keep running for fast fetch
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json
```

`openadt setup` is a legacy alias that runs bootstrap + build. After upgrading OpenADT, run `openadt config build` again.

Check without writing:

```bash
openadt config bootstrap --check
openadt config
```

Config fragments are written under `~/.openadt/` (or `%USERPROFILE%\.openadt\` on Windows). Do not paste real detector output into issues or commits.

<a id="config"></a>

## Config

Load order:

1. `OPENADT_CONFIG`
2. `./.openadt/config.toml`
3. `~/.openadt/config.toml`

Minimal entrypoint:

```toml
version = 1

[merge]
strategy = "last-wins"
includes = [
  "destinations/*.openadt.toml",
  "local.openadt.toml"
]
```

Example destination (fictional values):

```toml
version = 1

[destinations.DEV]
alias = "DEV"
system_id = "DEV"
client = "100"
language = "EN"
user = "DEVELOPER"

[destinations.DEV.adt]
transport = "sdk"
authentication_kind = "sso"
```

Schema: [specs/config.md](../specs/config.md).

<a id="fetch"></a>

## Fetch

```bash
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
openadt fetch DEV /sap/bc/adt/discovery --header "Accept: application/atomsvc+xml" --include --fail
```

Verbose diagnostics:

```bash
export OPENADT_VERBOSE=true    # Linux / macOS
# $env:OPENADT_VERBOSE = "true"   # Windows PowerShell
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

Redact logs before sharing. With a running proxy for the same system, `fetch` reuses the warm session unless you pass `--direct`.

### SDK diagnostics (`openadt auth` / `openadt discovery`)

Requires `transport = "sdk"` and `openadt config build`:

```bash
openadt auth login DEV
openadt discovery DEV --format json
openadt auth status DEV --format json
```

See [specs/cli.md](../specs/cli.md) and [specs/sdk-capabilities.md](../specs/sdk-capabilities.md).

<a id="local-proxy"></a>

## Local proxy

Contract: [specs/proxy.md](../specs/proxy.md). **ABAP FS:** [integrations/abap-fs.md](integrations/abap-fs.md).

```bash
openadt proxy DEV --listen 127.0.0.1:8080
```

Local Basic auth (credentials are **not** SAP users):

```bash
export OPENADT_PROXY_PASSWORD="local-only-password"
openadt proxy DEV --listen 127.0.0.1:8080 --local-auth basic --local-username openadt
```

Windows PowerShell: `$env:OPENADT_PROXY_PASSWORD = "local-only-password"` then the same flags.

Point clients at `http://127.0.0.1:8080/sap/bc/adt/...`. OpenADT strips client `Authorization` and SAP session headers before forwarding.

### Transport modes

| `adt.transport` | When                                  |
| --------------- | ------------------------------------- |
| `sdk` (default) | `runtime.adt_plugins_dir` set         |
| `rest-rfc`      | JCo without ADT plugins               |
| `http`          | Explicit frontend URL + ticket source |

Example HTTP ticket (fictional host):

```toml
[destinations.DEV.adt]
transport = "http"
base_url = "https://dev.example.com:8001"
authentication_kind = "sso"
```

```bash
export OPENADT_MYSAPSSO2="<ticket-value>"
```

<a id="wsl-and-devcontainers"></a>

## WSL and devcontainers

- **Config** in WSL can reference Windows paths under `/mnt/c/...`.
- **SNC SSO `fetch` / `proxy`** with Windows Secure Login usually require running OpenADT on **Windows**, not Linux-in-WSL, unless you have Linux JCo and Linux `SECUDIR` credentials.

Contributors using devcontainers: [contributing.md#devcontainer](contributing.md#devcontainer).

<a id="troubleshooting"></a>

## Troubleshooting

| Symptom                           | Likely cause                         | Action                                                            |
| --------------------------------- | ------------------------------------ | ----------------------------------------------------------------- |
| `JCo jar not configured`          | Missing runtime fragment             | `openadt config bootstrap`; check `~/.openadt/local.openadt.toml` |
| `no sapjco3 in java.library.path` | Wrong OS natives in `jco_native_dir` | Run setup on **host** OS; Windows needs `sapjco3.dll` in that dir |
| `Illegal JCo archive`             | Eclipse p2 jar name                  | Re-run bootstrap/build for canonical jar name                     |
| `GSS-API: No credentials`         | SNC material missing on host         | Secure Login on Windows or Linux `SECUDIR`                        |
| Proxy vs fetch differ             | Different config/transport           | Compare `openadt config`, alias, `adt.transport`                  |
| `adt discover` ClassNotFoundError | SDK runtime not built                | `openadt config build`                                            |
| Discovery empty                   | Not logged on                        | `openadt auth login DEV` first                                    |

<a id="security"></a>

## Security

Before sharing logs, config, or screenshots:

- Remove real SIDs, hosts, users, SNC names, tickets, and tokens.
- Use fictional examples: `DEV`, `DEVELOPER`, `dev-ms.example.com`.
- Do not commit SAP binaries, `.openadt/`, or generated devcontainer paths.

MCP bridge: [tools/mcp-bridge](../tools/mcp-bridge/), [specs/mcp.md](../specs/mcp.md).
