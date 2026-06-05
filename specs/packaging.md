# Packaging

OpenADT ships as a portable ZIP (`openadt.jar` + launchers + `sap-adt-mcp-launcher/`). SAP binaries are never bundled.

`openadt mcp` delegates to the Bun launcher in `OPENADT_HOME/sap-adt-mcp-launcher/` — install [Bun](https://bun.sh) for MCP.

Scoop: `post_install` runs `bin/scoop-post-install.ps1` (Java/Bun checks, MCP launcher presence). Optional deps in manifest `suggest`: `java/openjdk21`, `main/bun`.

## Windows

- **Scoop** (recommended): `scoop bucket add openadt https://github.com/abapify/scoop-bucket` then `scoop install openadt` (updated every Release via [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket); CI uses org app **abapify-bro** — [packaging/abapify-bro-app.md](../packaging/abapify-bro-app.md)). Legacy monorepo branch: `git clone -b scoop-bucket --depth 1 https://github.com/abapify/openadt openadt-bucket` then `scoop bucket add openadt .\openadt-bucket\packaging\scoop`
- One-shot install: `scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json`
- Maintainer: `bun run package:release -- --version=<semver>`

## Linux / macOS

- Tap (once): `brew tap abapify/openadt` → [`abapify/homebrew-openadt`](https://github.com/abapify/homebrew-openadt)
- Install: `brew install openadt`
- Upgrade: `brew update && brew upgrade openadt`
- Formula source in main repo: `Formula/openadt.rb` (synced from `packaging/homebrew/openadt.rb` on each release)
- Tap mirror: `tools/sync-homebrew-tap/sync.sh` + org app **abapify-bro** ([packaging/abapify-bro-app.md](../packaging/abapify-bro-app.md)); workflow template `packaging/homebrew/homebrew-tap-mirror.yml`
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
4. Job `publish` checks out that bump commit, builds, runs `package:release`, commits homebrew/scoop checksum updates (including `Formula/openadt.rb`), tags `vX.Y.Z`, pushes, syncs Scoop/Homebrew external repos via **abapify-bro** app token (or legacy PAT secrets), optional legacy `scoop-bucket` branch on this repo, and publishes GitHub Release assets

Local dry-run (no git writes):

```bash
bun run release:version -- --bump=patch
```
