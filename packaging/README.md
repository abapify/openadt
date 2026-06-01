# OpenADT packaging

## Windows (Scoop) — recommended

One-time bucket setup, then plain `scoop install openadt`:

```powershell
scoop bucket add openadt https://github.com/abapify/scoop-bucket.git
scoop install openadt
openadt --version
```

Upgrade:

```powershell
scoop update openadt
```

Alternative without adding a bucket (manifest URL):

```powershell
scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json
```

Each release updates branch [`scoop-bucket`](https://github.com/abapify/openadt/tree/scoop-bucket) on this repo automatically.

**Recommended bucket** (always in sync with Release):

```powershell
scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket
```

Legacy [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket) needs repo secret **`OPENADT_SCOOP_BUCKET_TOKEN`** (PAT with `contents:write` on that repo) plus workflow `packaging/scoop/scoop-bucket-mirror.yml` copied to `.github/workflows/` there. Release then triggers `repository_dispatch` to refresh `openadt.json`.

Scoop installs OpenADT (`openadt.jar` + `openadt.exe`) and suggests JDK 21. SAP JCo, Secure Login, and landscape data are not bundled.

## Windows (winget)

From a repo root with a built jar:

```powershell
.\mvnw.cmd -q verify -f pom.xml -Pdistribution

# Release zip: OpenADT only (MIT). SAP ADT/JCo are not bundled — use openadt setup + your adt_plugins_dir.
$env:OPENADT_PACKAGE_WIN_EXE = "1"
bun run package:release -- --version=1.1.2
winget validate --manifest packaging\winget\manifests\o\OpenADT\OpenADT\1.1.2
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
brew install --HEAD --formula packaging/homebrew/openadt.rb
```

After `v1.0.0` is tagged on GitHub, install from the release tarball URL in the formula (update `sha256` with `brew fetch --force openadt`).

## Release assets

`bun run package:release` writes:

- `packaging/dist/openadt-<version>.zip`
- `packaging/dist/openadt-<version>.zip.sha256`
- updates `InstallerSha256` in the winget installer manifest
- updates `hash` in `packaging/scoop/openadt.json`

GitHub Actions **Release** workflow is manual (`workflow_dispatch`) with a version bump dropdown; it tags the repo and publishes the GitHub Release.
