# Packaging

OpenADT ships as a portable ZIP (`openadt.jar` + launchers). SAP binaries are never bundled.

## Windows

- **Scoop** (simplest): `scoop-bucket` branch + `packaging/scoop/openadt.json` — after `scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket`, run `scoop install openadt`
- **Winget**: manifests under `packaging/winget/manifests/o/OpenADT/OpenADT/<version>/`
- Install: `scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json` or `winget install --id OpenADT.OpenADT` (after winget-pkgs merge)
- Maintainer: `bun run package:release -- --version=<semver>` then validate winget manifest if needed

## Linux / macOS

- Formula: `packaging/homebrew/openadt.rb`
- Install HEAD: `brew install --HEAD --formula packaging/homebrew/openadt.rb`
- Stable install uses the GitHub Release ZIP; `package:release` updates the formula `sha256`

## CI action pins

Workflows use current stable major tags: `actions/checkout@v6`, `actions/setup-java@v5`, `actions/setup-dotnet@v5`, `oven-sh/setup-bun@v2`, `nrwl/nx-set-shas@v5`, `softprops/action-gh-release@v3`. Bump when upstream releases a new major.

## Release workflow

Manual **Release** workflow (Actions → Release → Run workflow):

1. Choose **version bump**: `patch`, `minor`, `major`, `prerelease`, `prepatch`, `preminor`, `premajor`
2. Choose **prerelease id** when applicable: `rc`, `beta`, `alpha`
3. Job `bump` reads the latest `v*` tag (or `pom.xml` baseline), bumps `pom.xml`, winget manifests, Homebrew `STABLE`, and Scoop `openadt.json`, then commits and pushes the version-bump commit
4. Job `publish` checks out that bump commit, builds, runs `package:release`, commits winget/homebrew/scoop checksum updates, tags `vX.Y.Z`, pushes, and publishes GitHub Release assets

Local dry-run (no git writes):

```bash
bun run release:version -- --bump=patch
```
