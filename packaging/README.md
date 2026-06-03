# OpenADT packaging

## Windows (Scoop) — recommended

One-time bucket setup, then plain `scoop install openadt`:

```powershell
scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket
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

Legacy [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket) needs repo secret **`OPENADT_SCOOP_BUCKET_TOKEN`** (PAT with `contents:write` on that repo) plus workflow `packaging/scoop/scoop-bucket-mirror.yml` copied to `.github/workflows/` there. Release then triggers `repository_dispatch` to refresh `openadt.json`.

Scoop installs OpenADT (`openadt.jar` + `openadt.exe`) and suggests JDK 21. SAP JCo, Secure Login, and landscape data are not bundled.

## Linux / macOS (Homebrew tap)

Add the tap once, then use normal Homebrew commands:

```bash
brew tap abapify/openadt https://github.com/abapify/openadt.git
brew install openadt
openadt --version
```

Upgrade:

```bash
brew update
brew upgrade openadt
```

The tap reads `Formula/openadt.rb` on `main` (stable release zip from GitHub Releases). Maintainer source: `packaging/homebrew/openadt.rb` — kept in sync by `package:release`.

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
