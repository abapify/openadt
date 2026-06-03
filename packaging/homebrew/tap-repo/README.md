# abapify/homebrew-openadt

Standard Homebrew tap for OpenADT. End users run:

```bash
brew tap abapify/openadt
brew install openadt
```

Homebrew resolves `abapify/openadt` to this repository (`homebrew-openadt`).

## One-time setup (maintainers)

1. Create a public repo **`abapify/homebrew-openadt`** (empty is fine).
2. Copy [homebrew-tap-mirror.yml](../homebrew-tap-mirror.yml) to `.github/workflows/sync-from-openadt.yml` in that repo and push.
3. On **`abapify/openadt`**, configure org app [**abapify-bro**](../../abapify-bro-app.md) (recommended) or legacy PAT secret **`OPENADT_HOMEBREW_TAP_TOKEN`** with `contents:write` on **`abapify/homebrew-openadt`**.
4. Seed the formula once (from a machine with `gh auth` or a PAT):

   ```bash
   GH_TOKEN=$(gh auth token) bash tools/sync-homebrew-tap/sync.sh
   ```

   Or run the mirror workflow manually on `homebrew-openadt`.

Each OpenADT release runs `tools/sync-homebrew-tap/sync.sh` and dispatches `openadt-release` to the tap repo.

`Formula/openadt.rb` in the main **openadt** repo stays the release source of truth; this tap repo is a mirror for `brew tap abapify/openadt`.

## Why `STABLE`, `url`, and `sha256` look hardcoded

Homebrew **stable** formulae must pin an exact release URL and `sha256` checksum — that is normal tap design, not instability. OpenADT updates those fields automatically on each release: `release:version` bumps `STABLE`, `package:release` writes the zip hash, and CI runs `tools/sync-homebrew-tap/sync.sh` to copy `Formula/openadt.rb` here. Maintainers edit `packaging/homebrew/openadt.rb` in the main repo only; do not hand-edit this mirror.
