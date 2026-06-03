# OpenADT packaging

## Windows (Scoop) — recommended

One-time bucket setup, then plain `scoop install openadt`:

```powershell
scoop bucket add openadt https://github.com/abapify/scoop-bucket
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

Each release updates [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket) via org app [**abapify-bro**](abapify-bro-app.md). See `packaging/scoop/bucket-repo/README.md` for bucket-repo setup.

Legacy monorepo bucket branch (Scoop does not parse `#branch` in URLs): clone then add local path — `git clone -b scoop-bucket --depth 1 https://github.com/abapify/openadt openadt-bucket` then `scoop bucket add openadt .\openadt-bucket\packaging\scoop` (branch [`scoop-bucket`](https://github.com/abapify/openadt/tree/scoop-bucket) on this repo, updated automatically in Release CI).

Scoop installs OpenADT (`openadt.jar` + `openadt.exe`) and suggests JDK 21. SAP JCo, Secure Login, and landscape data are not bundled.

## Linux / macOS (Homebrew tap)

Add the tap once (standard name → [`abapify/homebrew-openadt`](https://github.com/abapify/homebrew-openadt)):

```bash
brew tap abapify/openadt
brew install openadt
openadt --version
```

Upgrade:

```bash
brew update
brew upgrade openadt
```

One-time maintainer setup: create `abapify/homebrew-openadt`, copy `packaging/homebrew/homebrew-tap-mirror.yml` to `.github/workflows/sync-from-openadt.yml`, configure [**abapify-bro**](abapify-bro-app.md) on `openadt`, then `GH_TOKEN=$(gh auth token) bash tools/sync-homebrew-tap/sync.sh` to seed `Formula/openadt.rb`. See `packaging/homebrew/tap-repo/README.md`.

Each release updates `Formula/openadt.rb` on `main` here and mirrors it to the tap repo. Maintainer source: `packaging/homebrew/openadt.rb` — kept in sync by `package:release`.

Legacy monorepo tap (no separate repo): `brew tap abapify/openadt https://github.com/abapify/openadt.git`

From a git checkout (builds from `main`):

```bash
brew install --HEAD --formula packaging/homebrew/openadt.rb
```

## Release assets

`bun run package:release` writes:

- `packaging/dist/openadt-<version>.zip`
- `packaging/dist/openadt-<version>.zip.sha256`
- updates `hash` in `packaging/scoop/openadt.json`
- updates `sha256` in `packaging/homebrew/openadt.rb` and `Formula/openadt.rb`

GitHub Actions **Release** workflow is manual (`workflow_dispatch`) with a version bump dropdown; it tags the repo and publishes the GitHub Release.
