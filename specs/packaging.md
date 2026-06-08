# Packaging

OpenADT ships as **two independent installable products** per release `vX.Y.Z`. SAP binaries are never bundled.

| Product       | Artifact(s)                                                        | Installs via                                             | Runtime                                                                           |
| ------------- | ------------------------------------------------------------------ | -------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `openadt`     | `openadt-X.Y.Z.zip` (Windows + Linux + macOS, single portable zip) | `scoop install openadt` / `brew install openadt`         | JDK 21 + SAP ADT VS Code extension                                                |
| `openadt-mcp` | `openadt-mcp-X.Y.Z-{platform}.{zip\|tar.gz}` (4 platform archives) | `scoop install openadt-mcp` / `brew install openadt-mcp` | SAP ADT VS Code extension (JDK 21 for `adt-lsc`); no Bun, no Java at install time |

The two products share the `~/.openadt/` config and the `~/.openadt/mcp/endpoints/` store. They are installed, upgraded, and uninstalled independently. The Java `openadt mcp` subcommand wraps `openadt-mcp` (see [cli.md](cli.md#openadt-mcp), [mcp.md](mcp.md#product-openadt-mcp)).

## `openadt` ZIP

`openadt-X.Y.Z.zip` contents:

- `openadt.jar` — distribution jar
- `openadt.exe` — Windows launcher
- `bin/` — PowerShell + bash launchers
- `LICENSE`, `VERSION`

**Not** in the zip: `sap-adt-mcp-launcher/`. The `openadt mcp` Java subcommand resolves and spawns `openadt-mcp` on PATH at runtime (see [cli.md](cli.md#openadt-mcp)).

## `openadt-mcp` archives

`openadt-mcp-X.Y.Z-{platform}.{zip|tar.gz}` per release, one archive per matrix entry:

| Platform       | Archive                                 |
| -------------- | --------------------------------------- |
| `win-x64`      | `openadt-mcp-X.Y.Z-win-x64.zip`         |
| `linux-x64`    | `openadt-mcp-X.Y.Z-linux-x64.tar.gz`    |
| `darwin-arm64` | `openadt-mcp-X.Y.Z-darwin-arm64.tar.gz` |
| `darwin-x64`   | `openadt-mcp-X.Y.Z-darwin-x64.tar.gz`   |

Each archive contains the compiled Bun binary (`openadt-mcp.exe` on Windows, `openadt-mcp` elsewhere), `LICENSE`, `README.md`, `VERSION`. Bun is **not** required at install or runtime — the binary embeds the runtime.

## Windows

- **Scoop** (recommended): `scoop bucket add openadt https://github.com/abapify/scoop-bucket` then `scoop install openadt` (updated every Release via [`abapify/scoop-bucket`](https://github.com/abapify/scoop-bucket); CI uses org app **abapify-bro** — [packaging/abapify-bro-app.md](../packaging/abapify-bro-app.md)). Legacy monorepo branch: `git clone -b scoop-bucket --depth 1 https://github.com/abapify/openadt openadt-bucket` then `scoop bucket add openadt .\openadt-bucket\packaging\scoop`
- One-shot install: `scoop install https://raw.githubusercontent.com/abapify/openadt/main/packaging/scoop/openadt.json`
- **Standalone MCP install:** `scoop install openadt-mcp` (separate Scoop manifest `packaging/scoop/openadt-mcp.json`; post-install: `packaging/scoop/openadt-mcp-post-install.ps1`).
- Maintainer: `bun run package:release -- --version=<semver>`

### `openadt` Scoop manifest cleanup

The `openadt` manifest no longer carries MCP-specific entries:

- Drop `Bun (for openadt mcp)` from `suggest` (`java/openjdk21` remains for `fetch`/`proxy`).
- Remove MCP from `notes` (the "Next steps" footer may mention `scoop install openadt-mcp` as an alternative for users who want only the launcher).
- Remove the MCP post-install launcher check from `packaging/scoop/post-install.ps1` (the `bun` check and the `sap-adt-mcp-launcher\src\main.ts` existence test go away).

### `openadt-mcp` Scoop manifest

`packaging/scoop/openadt-mcp.json`:

- `bin`: `openadt-mcp.exe`
- `extract_dir`: `openadt-mcp-X.Y.Z`
- `suggest`: `JDK 21` (for `adt-lsc` only)
- No `Bun` suggest, no `Java/openjdk21` runtime dependency

`packaging/scoop/openadt-mcp-post-install.ps1`:

- Check SAP ADT VS Code extension presence (the `adt-lsc` binary).
- Check JDK 21 (for `adt-lsc`; no Bun check).
- Print next steps: install `sapse.adt-vscode` if missing, configure `.cursor/mcp.json` with `"command": "openadt-mcp"`.

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

### `openadt` Homebrew formula cleanup

- Drop the `libexec.install mcp_launcher` block from `packaging/homebrew/openadt.rb` (and its mirror `Formula/openadt.rb`).

### `openadt-mcp` Homebrew formula

`packaging/homebrew/openadt-mcp.rb` (mirror `Formula/openadt-mcp.rb`):

- `depends_on "openjdk@21"` (for `adt-lsc`; no `depends_on "bun"`)
- `on_macos` / `on_linux` blocks select the right archive URL by platform
- `bin.install "openadt-mcp"`
- Stable formulae must pin version + URL + checksum per platform (Homebrew requirement); values are release-automated

## CI action pins

Workflows use current stable major tags: `actions/checkout@v6`, `actions/setup-java@v5`, `actions/setup-dotnet@v5`, `oven-sh/setup-bun@v2`, `nrwl/nx-set-shas@v5`, `softprops/action-gh-release@v3`. Bump when upstream releases a new major.

## Release workflow

One workflow (`release.yml`) handles the full release pipeline as four sequential jobs.

### `release.yml` — bump → build → publish → sync (manual trigger)

Manual **Release** workflow (Actions → Release → Run workflow):

1. Choose **version bump**: `patch`, `minor`, `major`, `prerelease`, `prepatch`, `preminor`, `premajor`
2. Optionally set **prerelease id** (`rc`, `beta`, `alpha`) — required only for `prerelease`, `prepatch`, `preminor`, and `premajor` (omit for `patch` / `minor` / `major`)
3. Optionally check **Draft** — creates a draft release with assets attached; `sync` job is skipped until manually published

Four jobs run in sequence:

| Job          | Runner(s)                                                                                                                                             | Does                                                                                                                                                                                                                                                                                                                                  |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`bump`**   | ubuntu                                                                                                                                                | Reads latest `v*` tag (or `pom.xml` baseline), bumps `pom.xml` + both Scoop manifests + both Homebrew formula stubs, commits, pushes, creates tag                                                                                                                                                                                     |
| **`build`**  | **Matrix of 4 runners** — `windows-latest` (`win-x64`), `ubuntu-latest` (`linux-x64`), `macos-latest` (`darwin-arm64`), `macos-latest` (`darwin-x64`) | Each matrix entry runs `bun run mcp:build:compile -- --platform=<matrix.platform> --out=…` and produces `openadt-mcp-X.Y.Z-<platform>.{zip\|tar.gz}`. The `windows-latest` entry **additionally** builds the `openadt.jar` (`-Pdistribution`) and runs `package:release` to produce `openadt-X.Y.Z.zip` + `openadt-X.Y.Z.zip.sha256`. |
| **`create`** | ubuntu                                                                                                                                                | Downloads all artifacts (one `openadt.zip` from the `windows-latest` runner, four `openadt-mcp-<platform>` archives), creates GitHub Release titled `Release vX.Y.Z` with all assets attached at creation time (required for immutable releases)                                                                                      |
| **`sync`**   | ubuntu                                                                                                                                                | Downloads `.zip.sha256` and each `openadt-mcp-<platform>.{zip\|tar.gz}.sha256`, patches `openadt.json` + `openadt-mcp.json` (Scoop) and `Formula/openadt.rb` + `Formula/openadt-mcp.rb` (Homebrew), syncs `abapify/scoop-bucket` and `abapify/homebrew-openadt`, dispatches mirror events — skipped for draft releases                |

Notes:

- Assets are attached at `gh release create` time — no post-creation upload, compatible with immutable releases.
- `GITHUB_TOKEN` is sufficient; no GitHub App token needed (no cross-workflow event chaining).
- Draft releases skip `sync`; publish the draft from the GitHub UI when ready — then re-run `sync` manually if needed, or just retrigger the workflow.

Local dry-run (no git writes):

```bash
bun run release:version -- --bump=patch
```
