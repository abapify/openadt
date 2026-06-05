# Developing OpenADT

This guide is for **contributors** working from a **git clone** of [github.com/abapify/openadt](https://github.com/abapify/openadt). If you only installed OpenADT via Scoop or Homebrew and want to use the CLI against SAP, see [usage.md](usage.md).

Pull request rules and the short checklist: [CONTRIBUTING.md](../CONTRIBUTING.md). Agent layout: [AGENTS.md](../AGENTS.md).

## Contents

| Topic                          | Section                                         |
| ------------------------------ | ----------------------------------------------- |
| Build tools (JDK, Maven, Bun)  | [Build tools](#build-tools)                     |
| Clone, build, test             | [Build from source](#build-from-source)         |
| Run CLI from the repo          | [Run from clone](#run-from-clone)               |
| Install your built jar locally | [Local jar install](#local-jar-install)         |
| Scoop / Homebrew from checkout | [Package from checkout](#package-from-checkout) |
| WSL + Linux natives            | [WSL development](#wsl-development)             |
| Devcontainer                   | [Devcontainer](#devcontainer)                   |
| CI-style verification          | [Validation](#validation)                       |

<a id="build-tools"></a>

## Build tools

### Windows (Scoop)

```powershell
scoop install git maven bun
scoop bucket add java
scoop install openjdk21
java -version
mvn -version
```

### Linux and macOS (Homebrew)

```bash
brew install openjdk@21 maven git oven-sh/bun/bun
java -version
mvn -version
```

Use **JDK 17 or 21**. Newer JDK versions may break test-time bytecode instrumentation until the toolchain is verified.

<a id="build-from-source"></a>

## Build from source

```bash
git clone https://github.com/abapify/openadt.git
cd openadt
bun install
./mvnw -q verify -f pom.xml -Pdistribution
```

Shaded CLI jar: `apps/openadt-cli/target/openadt-*.jar` (version matches root `pom.xml`).

<a id="run-from-clone"></a>

## Run from clone

**Windows (repo scripts — explicit `./dev-openadt`, not bare `openadt`):**

```powershell
.\dev-openadt.ps1 --help
.\dev-openadt.cmd fetch DEV /sap/bc/adt/core/http/systeminformation --profile sso
```

Bare `openadt` on PATH is the **Scoop/Homebrew** install. From a clone, do not name dev scripts `openadt.*` at repo root (CMD searches the current directory first).

**Unix / Git Bash:**

```bash
chmod +x dev-openadt
./dev-openadt config bootstrap
```

**SDK classpath from `target/` (Windows):**

```powershell
.\scripts\openadt-sdk.ps1 discovery DEV
.\scripts\openadt-sdk.ps1 auth login DEV
```

<a id="local-jar-install"></a>

## Local jar install

Copy the built jar off-repo if you want a global `openadt` without Scoop/Homebrew.

**Windows:**

```powershell
New-Item -ItemType Directory -Force C:\Tools\OpenADT\bin | Out-Null
Copy-Item .\apps\openadt-cli\target\openadt-*.jar C:\Tools\OpenADT\openadt.jar -Force
```

`C:\Tools\OpenADT\bin\openadt.ps1`:

```powershell
param([Parameter(ValueFromRemainingArguments = $true)][string[]] $OpenAdtArgs)
java -jar C:\Tools\OpenADT\openadt.jar @OpenAdtArgs
exit $LASTEXITCODE
```

Add `C:\Tools\OpenADT\bin` to `PATH`.

**Linux / macOS:**

```bash
mkdir -p "$HOME/.local/share/openadt" "$HOME/.local/bin"
cp apps/openadt-cli/target/openadt-*.jar "$HOME/.local/share/openadt/openadt.jar"
printf '%s\n' '#!/usr/bin/env bash' 'exec java -jar "$HOME/.local/share/openadt/openadt.jar" "$@"' > "$HOME/.local/bin/openadt"
chmod +x "$HOME/.local/bin/openadt"
```

<a id="package-from-checkout"></a>

## Package from checkout

Test packaging manifests before release:

```powershell
scoop install .\packaging\scoop\openadt.json
```

```bash
brew install --HEAD --formula packaging/homebrew/openadt.rb
```

Maintainers: [packaging/README.md](../packaging/README.md), [specs/packaging.md](../specs/packaging.md).

<a id="wsl-development"></a>

## WSL development

WSL can read Windows paths under `/mnt/c/...` for **detectors and config**, but Linux Java cannot load `sapjco3.dll` or use Windows-only Secure Login state.

| Task                                             | Where                                                      |
| ------------------------------------------------ | ---------------------------------------------------------- |
| Edit code, unit tests, spec sync                 | WSL or any OS                                              |
| SNC SSO `fetch` / `proxy` against real landscape | **Windows host** (or WSL with Linux JCo + Linux `SECUDIR`) |

Linux-native experiment in WSL:

```bash
./scripts/openadt-wsl-env --print-env
OPENADT_CONFIG="$(pwd)/tmp/wsl-openadt-config.toml" \
  ./scripts/openadt-wsl-env \
  java -jar apps/openadt-cli/target/openadt-*.jar \
  fetch DEV /sap/bc/adt/core/http/systeminformation --json --fail
```

End-user WSL vs host notes: [usage.md#wsl-and-devcontainers](usage.md#wsl-and-devcontainers).

<a id="devcontainer"></a>

## Devcontainer

The devcontainer does **not** download or redistribute SAP software. Stage Linux JCo/CryptoLib from archives you obtained under SAP license, then:

```bash
bun install
bun run bootstrap:devcontainer -- --non-interactive --skip-if-missing --container-workspace /workspaces/openadt
```

Run `openadt setup` in the container with `adt_plugins_dir` pointing at licensed plugins (often `/mnt/c/.../.p2/pool/plugins` on WSL).

Generated paths (do not commit):

```text
.devcontainer/dist/
.devcontainer/sec/
.devcontainer/openadt-config.toml
.devcontainer/runtime.openadt.toml
.openadt/destinations/generated-*.openadt.toml
```

<a id="validation"></a>

## Validation

Before a PR:

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -f pom.xml -Pdistribution
bun run openadt:test
```

Single module:

```bash
./mvnw -q test -pl apps/openadt-config
```

With local SAP ADT plugins (example WSL path):

```bash
./mvnw -q test -pl apps/openadt-sap-adt -Dadt.plugins.dir=/mnt/c/Users/<user>/.p2/pool/plugins
```

Integration tests (`@Tag("integration")`) need a local SAP runtime and are skipped by default.

Optional doctor script (Windows):

```powershell
.\scripts\openadt-local-check.ps1 DEV
```

## Security when contributing

Do not commit SAP binaries, JCo jars, `sapcrypto`, Secure Login files, `~/.openadt/` dumps, or real landscape data. Use fictional fixtures only (`DEV`, `dev-ms.example.com`). See [usage.md#security](usage.md#security) for redaction before sharing logs.
