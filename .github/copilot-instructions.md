# OpenADT — Copilot repository instructions

Trust this file and **do not re-discover** layout each session. For full agent rules, read [AGENTS.md](../AGENTS.md) at the repo root.

## What this repo is

OpenADT: Java CLI (`apps/openadt-cli`) — local SAP ADT bridge (`setup`, `fetch`, `proxy`). Specs in `specs/` are authoritative for behavior. SAP binaries (JCo, sapcrypto, Secure Login) are **never** committed.

## Agent skills (read before specialized work)

| Skill                               | Path                                                                                                                           | When                                  |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------- |
| `act`                               | [`.github/skills/act/SKILL.md`](skills/act/SKILL.md) (same as [`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md)) | **`/act`** or `@copilot /act` on a PR |
| `openadt-local-sap-runtime`         | `.agents/skills/openadt-local-sap-runtime/SKILL.md`                                                                            | Setup, fetch, proxy, JCo/SNC          |
| `openadt-devcontainer-host-runtime` | `.agents/skills/openadt-devcontainer-host-runtime/SKILL.md`                                                                    | Devcontainer / WSL vs host            |

Index: [`.agents/skills/README.md`](../.agents/skills/README.md).

**On `/act`:** load the **`act` skill file first** (full body), then execute it. Do not use a generic built-in `act` shortcut without reading this repo’s skill.

## Copilot coding agent — GitHub API access

The SWE agent runs behind a **firewall**. Bash/`gh` calls to `api.github.com` / GraphQL return **403** (`Blocked by DNS monitoring proxy`).

- **Do not** use `gh api`, `gh api graphql`, or `gh pr view --json`.
- **Do not** add `api.github.com` to the custom firewall allowlist to work around this.
- **Do** use the **`github-mcp-server`** tools (`pull_request_read`, `pull_request_review_write`, `actions_*`, `reply_to_comment`).
- **Resolve conversation** via MCP `pull_request_review_write` → `resolve_thread` with `threadId` from `get_review_comments` (`PRRT_…`), or **Playwright** on the PR page if thread `id` is unavailable.

## PR / review workflow (`/act`)

Follow [`.github/skills/act/SKILL.md`](skills/act/SKILL.md):

- Priority: CI (HEAD) → blocking review → nits/suggestions
- **Resolve conversation** on GitHub for every handled thread (mandatory)
- Minimal fixes; idempotent re-runs (no duplicate commits or summaries)
- Before pushing TS under `tools/`: `bunx nx format:write`

## Build and validate

```bash
bun install --frozen-lockfile
bunx nx format:check
cd apps/openadt-cli && ./mvnw test                    # needs SAP plugins locally, or CI skips
cd apps/openadt-cli && ./mvnw -Pdistribution package -Dmaven.test.skip=true
bun run package:release -- --version=<semver>         # maintainers; Windows exe needs SDK
```

CI: `.github/workflows/ci.yml`. Release (manual dispatch): `.github/workflows/release.yml`. Packaging: `packaging/README.md`, `specs/packaging.md`.

## Must not commit

Real SIDs, hostnames, credentials, `~/.openadt/`, `tmp/` scratch (except gitignored), SAP jars/DLLs, `.devcontainer/dist/`. Use fictional fixtures from `AGENTS.md`.

## Tooling versions

Prefer **latest stable majors** for GitHub Actions (`checkout@v6`, `setup-java@v5`, `setup-bun@v2`, etc.). See `.cursor/rules/latest-stable-tooling.mdc` when editing workflows.
