# OpenADT Usage Guide

This guide explains how to install, configure, and use OpenADT on a developer workstation.

OpenADT is a local bridge for SAP ADT traffic. It is designed for systems where Eclipse ADT works through SAP ADT SDK, SAP JCo, SNC, and SAP Secure Login Client, while another local tool needs a simple CLI or localhost HTTP endpoint.

OpenADT does not include SAP software. SAP JCo, SAP ADT plugins, SAP CryptoLib, SAP Secure Login Client, and landscape data must come from the user's licensed local installation.

## Recommended Runtime Shape

For SNC SSO, run `openadt fetch` and `openadt proxy` on the OS that owns the native SAP libraries and Secure Login credentials.

Typical Windows setup:

- SAP GUI / SAP Business Client are installed on Windows.
- Eclipse ADT is installed on Windows and has a working connection.
- SAP Secure Login Client is installed and can log in on Windows.
- OpenADT runtime commands run with Windows Java.
- WSL or a devcontainer may detect and prepare config, but should not be treated as equivalent to Windows-native SNC execution.

Typical Linux setup:

- Linux JCo native library is available as `libsapjco3.so`.
- Linux CryptoLib is available as `libsapcrypto.so`.
- `SECUDIR` contains Linux-visible PSE and credential material.
- OpenADT runtime commands run with Linux Java.

Devcontainer setup is useful for future Linux-native workflows and reproducible development. It is not currently the reliable path for Windows Secure Login Client SNC SSO, because Windows in-memory or Windows-token-store credentials do not automatically become Linux container credentials.

## Install Prerequisites On Windows

Install general tools with `winget`:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK --source winget
winget install --id Apache.Maven --source winget
winget install --id Git.Git --source winget
winget install --id Oven-sh.Bun --source winget
```

Then install SAP prerequisites from SAP or your organization:

- SAP GUI for Windows or SAP Business Client
- SAP Secure Login Client
- Eclipse with ABAP Development Tools
- SAP JCo 3.x for the OS where runtime commands will execute
- SAP CryptoLib / `sapcrypto`

Verify Java and Maven:

```powershell
java -version
mvn -version
```

Use JDK 17 or JDK 21 for development and tests. Java versions newer than the dependency toolchain may break test-time bytecode instrumentation in some environments.

## Install OpenADT Today

Until an official release package is published, build from source.

```powershell
git clone https://github.com/abapify/openadt.git
cd openadt\apps\openadt-cli
mvn package -DskipTests
```

Create a small launcher directory:

```powershell
New-Item -ItemType Directory -Force C:\Tools\OpenADT | Out-Null
Copy-Item .\target\openadt-1.0.0-SNAPSHOT.jar C:\Tools\OpenADT\openadt.jar -Force
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

Add the launcher to your user `PATH`:

```powershell
[Environment]::SetEnvironmentVariable(
  'Path',
  [Environment]::GetEnvironmentVariable('Path', 'User') + ';C:\Tools\OpenADT\bin',
  'User'
)
```

Open a new terminal and verify:

```powershell
openadt --help
```

## Future Winget Package

The clean Windows distribution target is a GitHub release ZIP plus a winget manifest.

Expected user flow after publishing:

```powershell
winget install --id OpenADT.OpenADT --source winget
openadt --help
```

Expected update and uninstall:

```powershell
winget upgrade --id OpenADT.OpenADT --source winget
winget uninstall --id OpenADT.OpenADT
```

Winget should install only OpenADT files:

- `openadt.jar`
- `openadt.ps1` or `openadt.cmd` launcher
- license and notices

Winget must not install, download, or redistribute SAP JCo, SAP ADT plugins, SAP Secure Login Client, SAP GUI, CryptoLib, private config, tickets, cookies, or landscape data.

## First Setup

Run setup from the host OS first:

```powershell
openadt setup
```

This detects local SAP configuration and writes fragments under:

```text
~\.openadt\config.toml
~\.openadt\destinations\detected.openadt.toml
~\.openadt\local.openadt.toml
```

Use check mode to inspect without writing:

```powershell
openadt setup --check
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

Use HTTP mode only when you have a valid frontend discovery URL and an explicit `MYSAPSSO2` source:

```toml
[destinations.DEV.adt]
transport = "http"
discovery_url = "https://dev.example.com:8001/sap/bc/adt"
authentication_kind = "sso"
```

Then provide the ticket through an environment variable or a local-only config fragment:

```powershell
$env:OPENADT_MYSAPSSO2 = "<ticket-value>"
```

Prefer SDK mode for normal SAP GUI / Eclipse ADT / Secure Login Client setups.

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
  java -jar apps/openadt-cli/target/openadt-1.0.0-SNAPSHOT.jar \
  fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

If this fails with `No SSO credentials available`, the Linux runtime does not have usable Linux-visible credential material in `SECUDIR`.

## Devcontainer Usage

The devcontainer bootstrap prepares Linux-native runtime files when SAP archives are available locally:

```bash
bun install
bun run bootstrap:devcontainer -- --non-interactive --container-workspace /workspaces/openadt
```

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

| Symptom                                      | Likely cause                                          | Action                                                                    |
| -------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------- |
| `JCo jar not configured`                     | Runtime fragment is missing                           | Run `openadt setup`, then check `~\.openadt\local.openadt.toml`           |
| `Illegal JCo archive`                        | JCo jar has Eclipse p2 filename                       | Re-run setup so `JCoJarCanonicalizer` creates the canonical jar           |
| `SessionReferenceProvider` class not found   | Core JCo jar is missing or loaded after `jco.eclipse` | Build/package with the SDK runner and keep canonical JCo first            |
| `GSS-API(maj): No credentials were supplied` | SNC runtime has no usable credential                  | Verify Secure Login Client on host OS or Linux `SECUDIR`                  |
| HTTP 406 for system information              | Missing ADT Accept header                             | Use `--json` default behavior or pass the systeminformation Accept header |
| Proxy works differently from fetch           | Commands use different config or transport            | Check `OPENADT_CONFIG`, alias, and `[destinations.<alias>.adt].transport` |

## Security Checklist

Before sharing logs, config, screenshots, issues, or pull requests:

- Remove real SIDs, clients, usernames, hostnames, FQDNs, logon groups, SNC partner names, profile UUIDs, discovery URLs, cookies, tickets, and tokens.
- Use fictional examples such as `DEV`, `DEVELOPER`, `dev-ms.example.com`, `PUBLIC`, and `p:CN=SAPServiceDEV`.
- Do not commit SAP binaries, JCo jars, `sapcrypto`, Secure Login files, `.openadt/`, `.devcontainer/dist/`, `.devcontainer/sec/`, generated config fragments, or `tmp/`.

## Developer Validation

Run unit tests from WSL with an explicit ADT plugin directory when the plugin pool is Windows-side:

```bash
cd apps/openadt-cli
../../tmp/apache-maven-3.9.9/bin/mvn \
  -Dmaven.repo.local=../../tmp/m2 \
  -Dadt.plugins.dir=/mnt/c/Users/<user>/.p2/pool/plugins \
  test
```

For a normal workstation Maven install:

```bash
cd apps/openadt-cli
mvn -Dadt.plugins.dir=/mnt/c/Users/<user>/.p2/pool/plugins test
```

Use JDK 17 or JDK 21 unless the dependency toolchain has been verified on newer Java versions.
