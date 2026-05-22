# OpenADT packaging

## Windows (winget)

From a repo root with a built jar:

```powershell
cd apps\openadt-cli
..\mvnw.cmd -Pdistribution package -DskipTests
cd ..\..
$env:OPENADT_PACKAGE_WIN_EXE = "1"
bun run package:release -- --version=1.0.0
winget validate --manifest packaging\winget\manifests\o\OpenADT\OpenADT\1.0.0
winget install --manifest packaging\winget\manifests -e --id OpenADT.OpenADT
```

`package:release` builds `openadt.exe` (Go launcher) on Windows or when `OPENADT_PACKAGE_WIN_EXE=1` and Go is on `PATH`.

Published installs:

```powershell
winget install --id OpenADT.OpenADT
```

Winget installs only OpenADT (`openadt.jar` + launchers). It does not install SAP JCo, Secure Login, or landscape data.

## Linux / macOS (Homebrew)

From a git checkout:

```bash
brew install --formula packaging/homebrew/openadt.rb
```

After `v1.0.0` is tagged on GitHub, install from the release tarball URL in the formula (update `sha256` with `brew fetch --force openadt`).

## Release assets

`bun run package:release` writes:

- `packaging/dist/openadt-<version>.zip`
- `packaging/dist/openadt-<version>.zip.sha256`
- updates `InstallerSha256` in the winget installer manifest

GitHub Actions **Release** workflow is manual (`workflow_dispatch`) with a version bump dropdown; it tags the repo and publishes the GitHub Release.
