# Packaging

OpenADT ships as a portable ZIP (`openadt.jar` + launchers). SAP binaries are never bundled.

## Windows

- Manifests: `packaging/winget/manifests/o/OpenADT/OpenADT/<version>/`
- Install: `winget install --id OpenADT.OpenADT`
- Maintainer: `bun run package:release -- --version=<semver>` then `winget validate --manifest packaging/winget/manifests`

## Linux / macOS

- Formula: `packaging/homebrew/openadt.rb`
- Install HEAD: `brew install --HEAD --formula packaging/homebrew/openadt.rb`
- Stable install uses the GitHub Release ZIP; `package:release` updates the formula `sha256`

## CI

Manual **Release** workflow (Actions → Release → Run workflow):

1. Choose **version bump**: `patch`, `minor`, `major`, `prerelease`, `prepatch`, `preminor`, `premajor`
2. Choose **prerelease id** when applicable: `rc`, `beta`, `alpha`
3. Job `version` reads the latest `v*` tag (or `pom.xml` baseline), bumps `pom.xml`, winget manifests, and Homebrew `STABLE`, then commits, tags `vX.Y.Z`, and pushes
4. Job `package` checks out the tag, builds, runs `package:release`, commits winget/homebrew SHA256 updates, and publishes GitHub Release assets

Local dry-run (no git writes):

```bash
bun run release:version -- --bump=patch
```
