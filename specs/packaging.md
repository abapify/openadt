# Packaging

OpenADT ships as a portable ZIP (`openadt.jar` + launchers). SAP binaries are never bundled.

## Windows

- **Scoop** (recommended): `scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket` then `scoop install openadt` (updated every Release). Legacy [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket) requires `OPENADT_SCOOP_BUCKET_TOKEN` on the openadt repo.
- One-shot install: `scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json`
- Maintainer: `bun run package:release -- --version=<semver>`

## Linux / macOS

- Tap (once): `brew tap abapify/openadt https://github.com/abapify/openadt.git`
- Install: `brew install openadt`
- Upgrade: `brew update && brew upgrade openadt`
- Formula source: `Formula/openadt.rb` (synced from `packaging/homebrew/openadt.rb` on each release)
- Maintainer copy: `packaging/homebrew/openadt.rb`
- HEAD install from a git checkout: `brew install --HEAD --formula packaging/homebrew/openadt.rb`
- `package:release` updates formula `STABLE` and `sha256`

## CI action pins

Workflows use current stable major tags: `actions/checkout@v6`, `actions/setup-java@v5`, `actions/setup-dotnet@v5`, `oven-sh/setup-bun@v2`, `nrwl/nx-set-shas@v5`, `softprops/action-gh-release@v3`. Bump when upstream releases a new major.

## Release workflow

Manual **Release** workflow (Actions → Release → Run workflow):

1. Choose **version bump**: `patch`, `minor`, `major`, `prerelease`, `prepatch`, `preminor`, `premajor`
2. Optionally set **prerelease id** (`rc`, `beta`, `alpha`) — required only for `prerelease`, `prepatch`, `preminor`, and `premajor` (omit for `patch` / `minor` / `major`)
3. Job `bump` reads the latest `v*` tag (or `pom.xml` baseline), bumps `pom.xml`, Homebrew `STABLE`, Scoop `openadt.json`, and syncs `Formula/openadt.rb`, then commits and pushes the version-bump commit
4. Job `publish` checks out that bump commit, builds, runs `package:release`, commits homebrew/scoop checksum updates (including `Formula/openadt.rb`), tags `vX.Y.Z`, pushes, syncs Scoop manifests (branch `scoop-bucket` on this repo; optional `abapify/scoop-bucket` when secret `OPENADT_SCOOP_BUCKET_TOKEN` is set), and publishes GitHub Release assets

Local dry-run (no git writes):

```bash
bun run release:version -- --bump=patch
```
