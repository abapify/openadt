# Packaging

OpenADT ships as a portable ZIP (`openadt.jar` + launchers). SAP binaries are never bundled.

## Windows

- **Scoop** (recommended): `scoop bucket add openadt https://github.com/abapify/scoop-bucket` then `scoop install openadt` (updated every Release via [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket); maintainer secret `OPENADT_SCOOP_BUCKET_TOKEN` on openadt). Legacy monorepo branch: `scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket`
- One-shot install: `scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json`
- Maintainer: `bun run package:release -- --version=<semver>`

## Linux / macOS

- Tap (once): `brew tap abapify/openadt` → [`abapify/homebrew-openadt`](https://github.com/abapify/homebrew-openadt)
- Install: `brew install openadt`
- Upgrade: `brew update && brew upgrade openadt`
- Formula source in main repo: `Formula/openadt.rb` (synced from `packaging/homebrew/openadt.rb` on each release)
- Tap mirror: `tools/sync-homebrew-tap/sync.sh` + optional `OPENADT_HOMEBREW_TAP_TOKEN` (PAT with `contents:write` on `homebrew-openadt`); workflow template `packaging/homebrew/homebrew-tap-mirror.yml`
- Maintainer copy: `packaging/homebrew/openadt.rb`
- HEAD install from a git checkout: `brew install --HEAD --formula packaging/homebrew/openadt.rb`
- Legacy monorepo tap: `brew tap abapify/openadt https://github.com/abapify/openadt.git` (same `Formula/` on `main`)
- `package:release` updates formula `STABLE` and `sha256`
- Stable formulae must pin version + URL + checksum (Homebrew requirement for verified, reproducible installs). Values are release-automated; `abapify/homebrew-openadt` is a mirror of `Formula/openadt.rb`, not a second source of truth.

## CI action pins

Workflows use current stable major tags: `actions/checkout@v6`, `actions/setup-java@v5`, `actions/setup-dotnet@v5`, `oven-sh/setup-bun@v2`, `nrwl/nx-set-shas@v5`, `softprops/action-gh-release@v3`. Bump when upstream releases a new major.

## Release workflow

Manual **Release** workflow (Actions → Release → Run workflow):

1. Choose **version bump**: `patch`, `minor`, `major`, `prerelease`, `prepatch`, `preminor`, `premajor`
2. Optionally set **prerelease id** (`rc`, `beta`, `alpha`) — required only for `prerelease`, `prepatch`, `preminor`, and `premajor` (omit for `patch` / `minor` / `major`)
3. Job `bump` reads the latest `v*` tag (or `pom.xml` baseline), bumps `pom.xml`, Homebrew `STABLE`, Scoop `openadt.json`, and syncs `Formula/openadt.rb`, then commits and pushes the version-bump commit
4. Job `publish` checks out that bump commit, builds, runs `package:release`, commits homebrew/scoop checksum updates (including `Formula/openadt.rb`), tags `vX.Y.Z`, pushes, syncs Scoop manifest to `abapify/scoop-bucket` when `OPENADT_SCOOP_BUCKET_TOKEN` is set (and optional legacy branch `scoop-bucket` on this repo), syncs Homebrew tap (`abapify/homebrew-openadt` when `OPENADT_HOMEBREW_TAP_TOKEN` is set), and publishes GitHub Release assets

Local dry-run (no git writes):

```bash
bun run release:version -- --bump=patch
```
