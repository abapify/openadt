# OpenADT Usage Guide

This guide explains how to install, configure, and use OpenADT on a developer workstation.

OpenADT is a local bridge for SAP ADT traffic on **Windows, Linux, and macOS**. Another local tool (curl, an IDE plugin, a script) can call `openadt fetch` or a localhost `openadt proxy` instead of speaking SAP authentication protocols directly.

OpenADT does not include SAP software. Licensed SAP JCo, ADT plugins, CryptoLib, Secure Login Client, and landscape data come from the user's machine or organization.

## Supported Platforms

| OS      | `openadt setup`                                                    | `openadt fetch` / `openadt proxy`                              |
| ------- | ------------------------------------------------------------------ | -------------------------------------------------------------- |
| Windows | Detects SAP GUI / NWBC / Eclipse paths                             | Native `sapjco3.dll`, `sapcrypto.dll`                          |
| Linux   | Detects staged or user paths; WSL can read `/mnt/c/...` for config | Native `libsapjco3.so`, `libsapcrypto.so`                      |
| macOS   | Detects `~/Library/Application Support/SAP/...` landscape files    | Native `libsapjco3.dylib`, `libsapcrypto.dylib` when installed |

Run runtime commands on the OS that owns the native SAP libraries. WSL and devcontainers can prepare config, but Linux Java cannot load Windows DLLs.

## SAP Runtime: Optional, Not Mandatory

OpenADT **supports** the full Eclipse-style stack — SAP ADT SDK plugins, JCo, CryptoLib (`sapcrypto`), SNC, and SAP Secure Login Client — and `openadt setup` auto-detects it when present.

That stack is **not required for every installation**:

- **SNC SSO** needs JCo, the matching native library, and `sapcrypto` on the host OS. Secure Login Client is common in corporate landscapes but not the only credential source (for example Linux `SECUDIR` with PSE material).
- **`rest-rfc`** needs JCo (and SNC artifacts when the destination uses SNC).
- **`http`** transport can work with a configured `base_url` and `MYSAPSSO2` ticket source without JCo.
- **Proxy `--local-auth basic`** only protects the localhost listener; it is unrelated to SAP logon and does not require JCo.

A basic or password-based SAP destination in config is a valid choice when your landscape allows it. Prefer the full stack when you need parity with Eclipse ADT and SNC SSO.

## Install Build Tools

### Windows (`winget`)

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK --source winget
winget install --id Apache.Maven --source winget
winget install --id Git.Git --source winget
winget install --id Oven-sh.Bun --source winget
```

Verify:

```powershell
java -version
mvn -version
```

### Linux and macOS (Homebrew)

Install [Homebrew](https://brew.sh/) if needed. On Linux, use the Linuxbrew install path from the Homebrew site; the same `brew` commands apply on macOS.

```bash
brew install openjdk@21 maven git oven-sh/bun/bun
```

Verify:

```bash
java -version
mvn -version
```

Use JDK 17 or JDK 21 for development and tests. Java versions newer than the dependency toolchain may break test-time bytecode instrumentation in some environments.

## Install SAP Prerequisites (when needed)

Install from SAP or your organization only for the authentication path you use:

| Component                      | When you need it                               |
| ------------------------------ | ---------------------------------------------- |
| SAP GUI / NWBC / Eclipse ADT   | Helpful for `openadt setup` auto-detection     |
| SAP JCo 3.x (jar + native lib) | `sdk` or `rest-rfc` transport                  |
| SAP CryptoLib / `sapcrypto`    | SNC-enabled destinations                       |
| SAP Secure Login Client        | Common for Windows SNC SSO; optional otherwise |

**Windows:** SAP GUI or SAP Business Client, Eclipse with ABAP Development Tools, optional Secure Login Client.

**Linux:** `libsapjco3.so`, `libsapcrypto.so`, and Linux-visible `SECUDIR` when using SNC SSO.

**macOS:** `libsapjco3.dylib`, `libsapcrypto.dylib`, Eclipse/ADT or manual config under `~/Library/Application Support/SAP/`.

Devcontainer setup is useful for reproducible Linux-native development. It is not a substitute for Windows Secure Login credentials inside a Linux container when the usable PSE lives only in a Windows token store.

## Install OpenADT Today (build from source)

When Scoop/Homebrew packages are not suitable, build from source on any supported OS.

```bash
git clone https://github.com/abapify/openadt.git
cd openadt
./mvnw -q verify -f pom.xml -Pdistribution
```

The shaded CLI jar is `apps/openadt-cli/target/openadt-*.jar` (version matches root `pom.xml`, currently `1.1.x`).

From the repository root (dev build, not Scoop):

```powershell
cd openadt
.\openadt.ps1 --help
.\openadt.cmd fetch DEV /sap/bc/adt/core/http/systeminformation --profile sso
```

```bash
chmod +x openadt   # once, on Unix / Git Bash
./openadt setup
```

For `--profile snc` with Eclipse ADT plugins on the classpath, use `scripts/openadt-sdk.ps1` on Windows (resolves the newest `openadt-*.jar` under `apps/openadt-cli/target/`).

### Windows launcher

```powershell
New-Item -ItemType Directory -Force C:\Tools\OpenADT | Out-Null
Copy-Item .\apps\openadt-cli\target\openadt-*.jar C:\Tools\OpenADT\openadt.jar -Force
New-Item -ItemType Directory -Force C:\Tools\OpenADT\bin | Out-Null
```

Create `C:\Tools\OpenADT\bin\openadt.ps1`:

```powershell
param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $OpenAdtArgs
)

java -jar C:\Tools\OpenADT\openadt.jar @OpenAdtArgs
exit $LASTEXITCODE
```

Add `C:\Tools\OpenADT\bin` to your user `PATH`, open a new terminal, and run `openadt --help`.

### Linux and macOS launcher

```bash
mkdir -p "$HOME/.local/share/openadt" "$HOME/.local/bin"
cp apps/openadt-cli/target/openadt-*.jar "$HOME/.local/share/openadt/openadt.jar"
cat > "$HOME/.local/bin/openadt" <<'EOF'
#!/usr/bin/env bash
exec java -jar "$HOME/.local/share/openadt/openadt.jar" "$@"
EOF
chmod +x "$HOME/.local/bin/openadt"
```

Ensure `$HOME/.local/bin` is on your `PATH`, then run `openadt --help`.

## Install OpenADT (Scoop / Homebrew)

### Windows — Scoop

Three steps: **install → setup → proxy + fetch**.

```powershell
scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket
scoop install openadt
openadt setup
openadt proxy DEV
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json
```

Keep `openadt proxy` running in one terminal; `fetch` reuses it automatically (fast). Without proxy, `fetch` still works but starts a cold SAP session each time (~15 s).

`openadt setup` runs **config bootstrap** (detect SAP paths, write config) and **config build** (full SDK runtime jar into `~\.openadt\runtime\`). First build can take a few minutes. After upgrading OpenADT, run `openadt config build`.

Inspect config anytime:

```powershell
openadt config
```

Bootstrap or build separately:

```powershell
openadt config bootstrap
openadt config build
```

Without adding a bucket (manifest URL):

```powershell
scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json
```

From a git checkout:

```powershell
scoop install .\packaging\scoop\openadt.json
```

Upgrade and uninstall:

```powershell
scoop update openadt
scoop uninstall openadt
```

Scoop installs OpenADT and suggests JDK 21 (`java/openjdk21` bucket). SAP JCo, ADT plugins, Secure Login, and landscape data are not bundled.

### Linux and macOS — Homebrew

Add the tap once:

```bash
brew tap abapify/openadt https://github.com/abapify/openadt.git
brew install openadt
openadt setup
```

Upgrade:

```bash
brew update
brew upgrade openadt
```

From a git checkout (builds from `main`):

```bash
brew install --HEAD --formula packaging/homebrew/openadt.rb
openadt --help
```

See [packaging/README.md](../packaging/README.md) for maintainers (`package:release`).

## First Setup

Run setup from the host OS first (bootstrap + SDK build):

```powershell
openadt setup
```

Or step by step:

```powershell
openadt config bootstrap
openadt config build
```

This detects local SAP configuration and writes fragments under:

```text
~\.openadt\config.toml
~\.openadt\destinations\detected.openadt.toml
~\.openadt\local.openadt.toml
```

On Windows, when `adt_plugins_dir` is detected, `openadt setup` also builds the full SAP SDK runtime jar for `fetch`/`proxy`. Use `--skip-build` to save config only. After upgrading OpenADT, run `openadt config build`.

Use check mode to inspect without writing:

```powershell
openadt setup --check
openadt config bootstrap --check
openadt config
openadt setup --skip-build
```

Use a custom config path when testing:

```powershell
openadt setup --config C:\Users\developer\.openadt\config.toml
```

Detected values are local machine configuration. Do not paste real output into issues, docs, commits, or examples.

## Config Basics

OpenADT loads config in this order:

1. `OPENADT_CONFIG`
2. `.\.openadt\config.toml`
3. `~\.openadt\config.toml`

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

Example destination:

```toml
version = 1

[destinations.DEV]
alias = "DEV"
system_id = "DEV"
client = "100"
language = "EN"
user = "DEVELOPER"

[destinations.DEV.jco]
mshost = "dev-ms.example.com"
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
jco_jar = "C:\\Users\\developer\\.p2\\pool\\plugins\\com.sap.conn.jco-3.1.13.jar"
jco_native_dir = "C:\\Tools\\SAP\\jco-native"
sapcrypto = "C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib\\sapcrypto.dll"
adt_plugins_dir = "C:\\Users\\developer\\.p2\\pool\\plugins"
```

For Eclipse p2 JCo jars named like `com.sap.conn.jco_3.1.13.jar`, setup canonicalizes the jar to the JCo-required filename form `com.sap.conn.jco-3.1.13.jar`.

## Fetch A Single ADT Resource

Fetch system information:

```powershell
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

Fetch discovery:

```powershell
openadt fetch DEV /sap/bc/adt/discovery `
  --header "Accept: application/atomsvc+xml" `
  --include `
  --fail
```

Write a binary-safe response body:

```powershell
openadt fetch DEV /sap/bc/adt/repository/informationsystem/search `
  --raw `
  --output response.bin `
  --fail
```

Use verbose diagnostics when investigating local runtime issues:

```powershell
$env:OPENADT_VERBOSE = "true"
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

Do not share verbose logs until they have been checked for private landscape data.

## SDK diagnostics (`openadt adt`)

Typed SAP ADT SDK commands (discovery, logon) complement `fetch` / `proxy`. They use the same config and SDK runtime as fetch when `transport = "sdk"`.

See [specs/sdk-capabilities.md](../specs/sdk-capabilities.md) and [specs/cli.md](../specs/cli.md).

### Prerequisites

1. Run `openadt setup` or `openadt config bootstrap` then `openadt config build` so `runtime.adt_plugins_dir` and the SDK runtime jar exist.
2. Destination alias (for example `DEV`) in merged config with `transport = "sdk"` and working SNC/SSO or password logon for your landscape.

### Commands

```powershell
openadt adt discover DEV
openadt adt discover DEV --collection systems --category sap.adt --format json
openadt adt logon DEV
openadt adt logon-status DEV --format json
```

On Windows with plugins on the classpath but without installing the distribution jar globally:

```powershell
.\scripts\openadt-sdk.ps1 adt discover DEV
.\scripts\openadt-sdk.ps1 adt logon DEV
```

### Testing with SDK runtime

| Step | Command                                                                                 |
| ---- | --------------------------------------------------------------------------------------- |
| 1    | `openadt config` — confirm alias, `adt_plugins_dir`, JCo paths                          |
| 2    | `openadt adt logon DEV` — establish SDK session                                         |
| 3    | `openadt adt logon-status DEV` — should report logged on                                |
| 4    | `openadt adt discover DEV --format json` — atom discovery document                      |
| 5    | `openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json` — confirms transport |

Use `$env:OPENADT_VERBOSE = "true"` when diagnosing classpath or logon failures.

MCP: [tools/mcp-bridge](../tools/mcp-bridge/) exposes `adt_discover` and `adt_logon` via subprocess to the CLI.

## Start The Local Proxy

Start a loopback-only proxy:

```powershell
openadt proxy DEV --listen 127.0.0.1:8080
```

Use local Basic authentication for tools that require credentials:

```powershell
$env:OPENADT_PROXY_PASSWORD = "local-only-password"
openadt proxy DEV `
  --listen 127.0.0.1:8080 `
  --local-auth basic `
  --local-username openadt
```

The local proxy username and password are not SAP credentials. They only protect the localhost proxy from accidental unauthenticated use.

Point ADT-aware local tools at:

```text
http://127.0.0.1:8080/sap/bc/adt/...
```

The proxy strips incoming SAP authentication headers such as `Authorization`, SAP logon tokens, SNC tokens, and cookies before forwarding requests through the configured OpenADT transport.

## Transport Modes

Default mode is `sdk` when `runtime.adt_plugins_dir` is configured.

Use SDK mode for SNC SSO parity with Eclipse ADT:

```toml
[destinations.DEV.adt]
transport = "sdk"
authentication_kind = "sso"
```

Use RFC bridge mode only as a fallback:

```toml
[destinations.DEV.adt]
transport = "rest-rfc"
authentication_kind = "sso"
```

Use HTTP mode only when you have a valid SAP frontend `base_url` and an explicit `MYSAPSSO2` source:

```toml
[destinations.DEV.adt]
transport = "http"
base_url = "https://dev.example.com:8001"
authentication_kind = "sso"
```

Then provide the ticket through an environment variable or a local-only config fragment:

```powershell
$env:OPENADT_MYSAPSSO2 = "<ticket-value>"
```

Prefer SDK mode when Eclipse ADT and the JCo/SNC stack are available. Use `http` or password-based config when that matches your landscape.

## WSL Usage

WSL can detect Windows-side files under `/mnt/c/...`, but Linux Java cannot load Windows `sapjco3.dll` or use Windows-only Secure Login credentials.

Use WSL for:

- source development
- config inspection
- detector work
- Linux-native runtime experiments

Use Windows host runtime for:

- normal SNC SSO `openadt fetch`
- normal SNC SSO `openadt proxy`
- validation against Windows SAP GUI, Windows Eclipse ADT, and Windows Secure Login Client

If you intentionally stage Linux SAP runtime for WSL:

```bash
./scripts/openadt-wsl-env --print-env
./scripts/openadt-wsl-env ./.devcontainer/dist/snc/sapgenpse seclogin -l
OPENADT_CONFIG="$(pwd)/tmp/wsl-openadt-config.toml" \
  ./scripts/openadt-wsl-env \
  java -jar apps/openadt-cli/target/openadt-*.jar \
  fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

If this fails with `No SSO credentials available`, the Linux runtime does not have usable Linux-visible credential material in `SECUDIR`.

## Devcontainer Usage

The devcontainer does **not** download or redistribute SAP software. Stage Linux JCo/CryptoLib from archives you obtained under SAP license (for example under `~/.openadt/dist` on the host), then run bootstrap:

```bash
bun install
bun run bootstrap:devcontainer -- --non-interactive --skip-if-missing --container-workspace /workspaces/openadt
```

Run `openadt setup` inside the container so `adt_plugins_dir` points at your licensed Eclipse plugins (often via `/mnt/c/.../.p2/pool/plugins` on WSL). SDK `fetch`/`proxy` use the same classpath model as the Windows release (`openadt-full.jar` built on your machine).

It writes local-only generated files:

```text
.devcontainer/dist/
.devcontainer/sec/
.devcontainer/openadt-config.toml
.devcontainer/runtime.openadt.toml
.openadt/destinations/generated-*.openadt.toml
```

These files must not be committed.

Current SNC limitation:

- A Windows Secure Login Client `LOGGED_IN` state does not make Linux container JCo credential-ready.
- Linux JCo needs Linux native libraries and Linux-visible `SECUDIR` credential material.
- If the credential lives only in Windows Secure Login Client state, run `fetch` and `proxy` on Windows.

## Troubleshooting

Run the local setup doctor:

```powershell
.\scripts\openadt-local-check.ps1 DEV
```

Common failures:

| Symptom                                      | Likely cause                                                                                    | Action                                                                               |
| -------------------------------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `JCo jar not configured`                     | Runtime fragment is missing                                                                     | Run `openadt setup`, then check `~\.openadt\local.openadt.toml`                      |
| `no sapjco3 in java.library.path`            | `jco_native_dir` is for another OS (often `.devcontainer/dist/jco` with Linux `.so` on Windows) | Run `openadt setup` on the **host**; directory must contain `sapjco3.dll` on Windows |
| `Illegal JCo archive`                        | JCo jar has Eclipse p2 filename                                                                 | Re-run setup so `JCoJarCanonicalizer` creates the canonical jar                      |
| `SessionReferenceProvider` class not found   | Core JCo jar is missing or loaded after `jco.eclipse`                                           | Build/package with the SDK runner and keep canonical JCo first                       |
| `GSS-API(maj): No credentials were supplied` | SNC runtime has no usable credential                                                            | Verify Secure Login Client on host OS or Linux `SECUDIR`                             |
| HTTP 406 for system information              | Missing ADT Accept header                                                                       | Use `--json` default behavior or pass the systeminformation Accept header            |
| Proxy works differently from fetch           | Commands use different config or transport                                                      | Check `OPENADT_CONFIG`, alias, and `[destinations.<alias>.adt].transport`            |
| `adt discover` / `logon` ClassNotFoundError  | SAP ADT bundles not on classpath                                                                | Run `openadt config build`; use `openadt-sdk.ps1` or distribution jar with runtime   |
| `adt logon` succeeds but `fetch` fails       | Different transport or profile                                                                  | Align `transport` and profile; compare `openadt config` output                       |
| Discovery empty or errors                    | Not logged on or wrong destination id                                                           | Run `openadt adt logon` first; verify alias matches SDK destination id               |

## Security Checklist

Before sharing logs, config, screenshots, issues, or pull requests:

- Remove real SIDs, clients, usernames, hostnames, FQDNs, logon groups, SNC partner names, profile UUIDs, discovery URLs, cookies, tickets, and tokens.
- Use fictional examples such as `DEV`, `DEVELOPER`, `dev-ms.example.com`, `PUBLIC`, and `p:CN=SAPServiceDEV`.
- Do not commit SAP binaries, JCo jars, `sapcrypto`, Secure Login files, `.openadt/`, `.devcontainer/dist/`, `.devcontainer/sec/`, generated config fragments, or `tmp/`.

## Developer Validation

From the repository root (full reactor, distribution profile, no SAP plugins required for unit tests):

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -f pom.xml -Pdistribution
bun run openadt:test
```

Integration tests (`@Tag("integration")`) need a local SAP runtime and are skipped by default.

Optional: run a single module from root, for example `openadt-config`:

```bash
./mvnw -q test -pl apps/openadt-config
```

With SAP ADT plugins on the classpath (WSL reading Windows p2 pool):

```bash
./mvnw -q test -pl apps/openadt-sap-adt -Dadt.plugins.dir=/mnt/c/Users/<user>/.p2/pool/plugins
```

Use JDK 17 or JDK 21 unless the dependency toolchain has been verified on newer Java versions.
