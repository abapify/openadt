# OpenADT

OpenADT is an open-source Java CLI that bridges SAP ABAP Development Tools (ADT) access on **Windows, Linux, and macOS**.

OpenADT is not a full ADT client. It is a **local credential bridge** with a minimal CLI that lets ADT-aware tools reach SAP ADT endpoints through a localhost proxy or `openadt fetch`.

For **SNC SSO** (Eclipse ADT / SAP GUI parity), use SAP JCo, CryptoLib (`sapcrypto`), and optionally SAP Secure Login Client on the OS where runtime commands execute. That stack is **supported and auto-detected**, but **not required** for every setup — for example, HTTP transport with a ticket, password-based JCo destinations, or proxy `--local-auth basic` for localhost protection are valid workflows too.

## Install

**Windows:** `winget install --id OpenADT.OpenADT` (or local manifest — see [packaging/README.md](packaging/README.md)).

**Linux / macOS:** `brew install --HEAD --formula packaging/homebrew/openadt.rb`

Build from source: [Usage guide — Install OpenADT Today](docs/usage.md#install-openadt-today).

## Quick Start

```bash
# Detect local SAP systems and write config
openadt setup

# Start a local HTTP proxy for a system
openadt proxy DEV --listen 127.0.0.1:8080

# Fetch a single ADT resource
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --pretty --raw
```

For installation (winget on Windows, Homebrew on Linux/macOS), optional SAP runtime, WSL/devcontainer limits, proxy configuration, and troubleshooting, see the full [OpenADT Usage Guide](docs/usage.md).

## Local Setup Doctor

For machine-local checks that should stay outside the OpenADT CLI surface, use:

```bash
./scripts/openadt-local-check DEV
```

This standalone command checks local SAP GUI / NWBC / JCo / Secure Login prerequisites and reports what still needs to be installed or started before `openadt fetch` / `openadt proxy` can work.

The local Security Hub (`https://127.0.0.1:34443`) is optional. SNC SSO needs JCo, the native library, and `sapcrypto` on the host OS; Secure Login is only required when your landscape uses that client for credentials.

## WSL Runtime Envelope

For WSL-native SAP runtime checks, use the repo-local wrapper:

```bash
./scripts/openadt-wsl-env --print-env
./scripts/openadt-wsl-env ./.devcontainer/dist/snc/sapgenpse seclogin -l
OPENADT_CONFIG="$(pwd)/tmp/wsl-openadt-config.toml" \
  ./scripts/openadt-wsl-env \
  java -jar apps/openadt-cli/target/openadt-1.0.0-SNAPSHOT.jar \
  fetch DEV /sap/bc/adt/core/http/systeminformation --pretty --raw
```

This wrapper:

- creates `~/.openadt/sec/` and uses it as Linux `SECUDIR`
- creates `tmp/snc-shim/sncgss.so` as a Linux SNC shim that points to `libsapcrypto.so`
- exports `SECUDIR`, `SNC_LIB`, and `LD_LIBRARY_PATH` for the staged Linux runtime

Current observed behavior in WSL:

- with these variables set, Linux SAP runtime can reach a configured system (see your private `~/.openadt/config.toml`)
- if `SECUDIR` has no Linux credential material, both `sapgenpse` and `openadt fetch` still fail with `No SSO credentials available` / `GSS-API(maj): No credentials were supplied`

## Devcontainer Bootstrap

For devcontainer usage with Linux-native SAP runtime, the host machine needs Bun and the SAP archives.

Primary bootstrap path:

1. Place SAP runtime archives somewhere on the host, for example under `~/.openadt/dist`
2. Install local tooling dependencies:

```bash
bun install
```

3. Run the TypeScript bootstrap:

```bash
bun run bootstrap:devcontainer -- --non-interactive --container-workspace /workspaces/openadt
```

This script:

- finds Linux JCo and CryptoLib archives
- extracts Linux runtime files into `./.devcontainer/dist/`
- stages the full Linux CryptoLib toolset, including `sapgenpse`
- mirrors host destination fragments into `./.openadt/destinations/`
- writes `.devcontainer/runtime.openadt.toml`
- writes `.devcontainer/openadt-config.toml`
- prepares `.devcontainer/sec/` as the Linux `SECUDIR`

Fallback wrappers still exist under `scripts/`, but the Bun/TypeScript bootstrap is the primary path.

The staged runtime directory is local-only and must stay out of git:

- `./.devcontainer/dist/`

When using VS Code Dev Containers, `.devcontainer/devcontainer.json` runs the same bootstrap in non-interactive mode through `initializeCommand` and fails early if the local runtime is not ready.

Known limitation for SNC SSO:

- Linux-native `sapjco3` + `libsapcrypto.so` are necessary but not always sufficient.
- Linux-native SAP tooling expects a Linux-visible `SECUDIR` with PSE and `cred_v2` material.
- If the host Secure Login setup keeps the usable credential in a Windows-only token store or in-memory PSE, a Linux devcontainer may still fail with `GSS-API(maj): No credentials were supplied`.
- A `LOGGED_IN` Web Adapter profile on the Windows host does not by itself make Linux JCo inside the container credential-ready.

## How It Works

```
tool / curl / ADT-aware client
  -> localhost OpenADT proxy or openadt fetch
  -> OpenADT ADT transport
     -> default: SAP ADT SDK destination/session stack
     -> fallback: JCo + RFC bridge
  -> /sap/bc/adt/... resource
```

ADT requests are forwarded through the configured ADT transport. The preferred path is the SAP ADT SDK destination/session stack loaded from the user's Eclipse/ADT plugin pool. The legacy fallback uses `SADT_REST_RFC_ENDPOINT` over JCo/SNC. This allows existing ADT-aware tools — including those that only support Basic auth — to connect through a local URL while OpenADT handles SAP authentication internally.

## CLI Reference

```
openadt setup [--check] [--config <path>]
openadt proxy <system> [--listen <host:port>] [--local-auth basic] [--local-password <pwd>]
openadt fetch <system> <url-or-path> [--method GET] [--header "Name: Value"] [--pretty] [--raw] [--include] [--fail] [--body @file] [--output <file>]
```

See [`specs/cli.md`](specs/cli.md) for the full CLI contract.

## Config

OpenADT uses TOML configuration. Default path:

- runtime commands (`fetch`, `proxy`) load the first existing config from:
  - `./.openadt/config.toml`
  - `~/.openadt/config.toml`
- `OPENADT_CONFIG` overrides both paths when set
- `openadt setup` writes host fragments under `~/.openadt/` by default:
  - `config.toml`
  - `destinations/detected.openadt.toml`
  - `local.openadt.toml`
- devcontainer bootstrap writes container fragments under `.devcontainer/`:
  - `openadt-config.toml`
  - `runtime.openadt.toml`

Example entrypoint `config.toml`:

```toml
version = 1

[merge]
strategy = "last-wins"
includes = [
  "destinations/*.openadt.toml",
  "local.openadt.toml"
]
```

Example destination fragment:

```toml
version = 1

[destinations.DEV]
system_id = "DEV"
client = "200"
language = "EN"
user = "DEVELOPER"

[destinations.DEV.jco]
mshost = "abap-dev-ms.example.com"
msserv = "3600"
r3name = "DEV"
group = "PUBLIC"
snc_mode = "1"
snc_qop = "9"
snc_partnername = "p:CN=SAPServiceDEV"
snc_sso = "1"

[destinations.DEV.adt]
transport = "sdk"
authentication_kind = "sso"
```

Example runtime fragment:

```toml
version = 1

[runtime]
jco_jar = "C:\\Users\\user\\.p2\\pool\\plugins\\com.sap.conn.jco_3.1.13.jar"
jco_native_dir = "C:\\Users\\user\\AppData\\Local\\OpenADT\\jco-native"
sapcrypto = "C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib\\sapcrypto.dll"
adt_plugins_dir = "C:\\Users\\user\\.p2\\pool\\plugins"
```

See [`specs/config.md`](specs/config.md) for the full config reference.

## Building

Requires JDK 17 or 21, Maven 3.x.

```bash
cd apps/openadt-cli
mvn package
java -jar target/openadt-1.1.1.jar setup
```

From the repo root after `package`, use the dev launchers (same config as `~/.openadt`, not Scoop):

```powershell
# Windows PowerShell
.\openadt.ps1 --help
.\openadt.cmd fetch DEV /sap/bc/adt/core/http/systeminformation --profile sso
```

```bash
# Git Bash / Linux / macOS
./openadt --help
```

SNC/SDK transport: `scripts/openadt-sdk.ps1` (full JCo/ADT classpath).

With [Nx](https://nx.dev) (build + run in one step, forwards CLI args):

```bash
bun install
bun run openadt -- fetch DEV /sap/bc/adt/core/http/systeminformation --profile=sso
# or: nx run openadt-cli:run -- fetch DEV ... --profile=sso   (note `--` before fetch args)
# SNC (default_profile=snc): nx run openadt-cli:run -- fetch DEV ...  (dev launcher uses sap-lib classpath)
# or: nx run openadt-cli:run-sdk -- fetch DEV ... --profile=snc
```

Nx caches `openadt-cli:build` when Java sources are unchanged (no Maven on cache hit). Force rebuild: `nx run openadt-cli:build --skip-nx-cache`. Nx TUI is disabled in `nx.json`.

Non-interactive SSO (no Enter prompts): set `OPENADT_HTTP_SSO_NON_INTERACTIVE=true` and `OPENADT_HTTP_SSO_BRIDGE_WAIT_SECONDS=20` in the environment before `run`.

## Prerequisites (not included)

OpenADT does not bundle SAP software. What you need depends on the transport and authentication you use:

| Goal                                  | Typical SAP pieces                                           |
| ------------------------------------- | ------------------------------------------------------------ |
| SNC SSO / SDK parity with Eclipse ADT | JCo jar + native lib, `sapcrypto`, often Secure Login Client |
| RFC bridge (`rest-rfc`)               | JCo jar + native lib, `sapcrypto` when SNC is enabled        |
| HTTP transport with ticket            | Valid `discovery_url` and `MYSAPSSO2` source — no JCo        |
| Localhost proxy hardening only        | No SAP runtime — use `--local-auth basic`                    |

Obtain licensed SAP artifacts from SAP or your organization. Run `openadt setup` on the host OS to detect paths and write `config.toml` fragments.

## Contributing

See [`AGENTS.md`](AGENTS.md) for agent and contributor guidelines.

## Disclaimer

> This project does not include or redistribute SAP software.
> Users must obtain SAP JCo, SAP ADT, SAP Secure Login Client, and related native libraries from SAP or their organization under applicable SAP license terms.
> SAP is a trademark of SAP SE. This project is not affiliated with or endorsed by SAP SE.

## License

[Apache License 2.0](LICENSE)
