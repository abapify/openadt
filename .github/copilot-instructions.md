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

Use **`github-mcp-server`** for PR/CI/review (`pull_request_read`, `pull_request_review_write`, `actions_*`, `reply_to_comment`).

- **Do not** use **Playwright** for `/act` (GitHub UI is often `ERR_BLOCKED_BY_CLIENT`).
- **Do not** use `gh pr view --json` from Bash.
- **Resolve threads** only via MCP `pull_request_review_write` → `resolve_thread` + `threadId` (`PRRT_…`). If MCP omits thread `id`, say so in the summary with thread links — still finish code/CI work.

## PR / review workflow (`/act`)

Follow [`.github/skills/act/SKILL.md`](skills/act/SKILL.md). **Bare `/act` = all steps on.** Never abort the run because resolve is blocked; fix code first, then resolve pass. Before pushing TS under `tools/`: `bunx nx format:write`.

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
