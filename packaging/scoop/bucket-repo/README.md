# abapify/scoop-bucket

Standard Scoop bucket for OpenADT. End users run:

```powershell
scoop bucket add openadt https://github.com/abapify/scoop-bucket
scoop install openadt
```

Scoop has no Homebrew-style name shorthand for custom buckets; use the full Git URL (see [Scoop buckets wiki](https://github.com/ScoopInstaller/Scoop/wiki/Buckets)).

## One-time setup (maintainers)

1. Ensure a public repo **`abapify/scoop-bucket`** exists (empty is fine).
2. Copy [scoop-bucket-mirror.yml](../scoop-bucket-mirror.yml) to `.github/workflows/sync-from-openadt.yml` in that repo and push.
3. On **`abapify/openadt`**, configure org app [**abapify-bro**](../../abapify-bro-app.md) (recommended) or legacy PAT **`OPENADT_SCOOP_BUCKET_TOKEN`** with `contents:write` on **`abapify/scoop-bucket`**.
4. Seed the manifest once (from a machine with `gh auth` or a PAT):

   ```bash
   GH_TOKEN=$(gh auth token) bash tools/sync-scoop-bucket/sync.sh
   ```

   Or run the mirror workflow manually on `scoop-bucket`.

Each OpenADT release runs `tools/sync-scoop-bucket/sync.sh` and dispatches `openadt-release` to the bucket repo.

`packaging/scoop/openadt.json` in the main **openadt** repo stays the release source of truth; this bucket repo is a mirror for `scoop bucket add openadt`.

Legacy monorepo bucket branch (optional): `git clone -b scoop-bucket --depth 1 https://github.com/abapify/openadt openadt-bucket` then `scoop bucket add openadt .\openadt-bucket\packaging\scoop`
