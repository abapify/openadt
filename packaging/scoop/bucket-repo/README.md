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
3. On **`abapify/openadt`**, add secret **`OPENADT_SCOOP_BUCKET_TOKEN`**: fine-grained or classic PAT with `contents:write` on `scoop-bucket`.
4. Seed the manifest once (from a machine with the secret):

   ```bash
   OPENADT_SCOOP_BUCKET_TOKEN=<pat> bash tools/sync-scoop-bucket/sync.sh
   ```

   Or run the mirror workflow manually on `scoop-bucket`.

Each OpenADT release runs `tools/sync-scoop-bucket/sync.sh` and dispatches `openadt-release` to the bucket repo.

`packaging/scoop/openadt.json` in the main **openadt** repo stays the release source of truth; this bucket repo is a mirror for `scoop bucket add openadt`.

Legacy monorepo bucket branch (optional): `scoop bucket add openadt https://github.com/abapify/openadt.git#scoop-bucket`
