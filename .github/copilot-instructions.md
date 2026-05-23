# OpenADT — Copilot repository instructions

Trust this file and **do not re-discover** layout each session. For full agent rules, read [AGENTS.md](../AGENTS.md) at the repo root.

## What this repo is

OpenADT: Java CLI (`apps/openadt-cli`) — local SAP ADT bridge (`setup`, `fetch`, `proxy`). Specs in `specs/` are authoritative for behavior. SAP binaries (JCo, sapcrypto, Secure Login) are **never** committed.

## Agent skills (read before specialized work)

| Skill                               | Path                                                            | When                                               |
| ----------------------------------- | --------------------------------------------------------------- | -------------------------------------------------- |
| `act`                               | [`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md) | **`/act`**, `@copilot /act`, `@codex /act` on a PR |
| `openadt-local-sap-runtime`         | `.agents/skills/openadt-local-sap-runtime/SKILL.md`             | Setup, fetch, proxy, JCo/SNC                       |
| `openadt-devcontainer-host-runtime` | `.agents/skills/openadt-devcontainer-host-runtime/SKILL.md`     | Devcontainer / WSL vs host                         |

Index: [`.agents/skills/README.md`](../.agents/skills/README.md).

**On `/act`:** load the **`act` skill** ([`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md)) first. Codex uses the same skill + [AGENTS.md](../AGENTS.md) § Codex — not this Copilot-only section below.

## Copilot coding agent — GitHub access

Read-only GitHub MCP (~33 tools): no `pull_request_review_write` — use **`gh`** for resolve. **No Playwright** on github.com. **No** `gh pr view --json` from Bash if the agent firewall blocks GraphQL.

Assume **`gh` is installed and authenticated** (`gh auth status`).

## PR / review workflow (`/act`)

Follow [`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md).

**Do not** run `resolve-open-threads.sh` until review comments are **fixed in code** (or answered in each thread). The script only closes GitHub UI state; it does not implement feedback.

Order: read threads → product commits → reply in threads → then:

```bash
bash .agents/skills/act/resolve-open-threads.sh abapify openadt 2
```

Before pushing TS under `tools/`: `bunx nx format:write`.

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
