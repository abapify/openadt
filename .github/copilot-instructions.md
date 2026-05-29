# OpenADT — Copilot repository instructions

This file is the **repository-wide custom instructions** for GitHub Copilot ([official docs](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions)). Copilot **cloud agent** and **Copilot code review** on GitHub load it automatically. Path-specific rules live under [`.github/instructions/`](instructions/).

Trust this file and **do not re-discover** layout each session. For full agent rules, read [AGENTS.md](../AGENTS.md) at the repo root.

## PR metadata — do not touch unless asked

**Never edit a pull request title or description** unless the user explicitly asks you to update them.

- Do **not** rename the PR to progress labels like “Addressing PR comments” or “Fix review feedback”.
- Do **not** replace the author’s summary with an agent checklist, thread counts, or CI status.
- On `/act`, track progress in **review thread replies** and commits — not by overwriting the PR body.
- If the user asks to restore title/body, use GraphQL `pullRequest.userContentEdits` to find the author’s last version before agent edits.

## What this repo is

OpenADT is a **thin Java wrapper around the official SAP ADT SDK** — `openadt fetch` and `openadt proxy` use the same destination/session stack as Eclipse. Do **not** reimplement ADT HTTP, logon, or SNC unless `adt.transport = "http"` in config and specs. `setup` / `config bootstrap` only writes `~/.openadt/config.toml`. Specs in `specs/` are authoritative. SAP binaries are **never** committed. Layout: [`apps/ARCHITECTURE.md`](../apps/ARCHITECTURE.md), [`specs/vision.md`](../specs/vision.md).

## Agent skills (read before specialized work)

| Skill                               | Path                                                            | When                                               |
| ----------------------------------- | --------------------------------------------------------------- | -------------------------------------------------- |
| `act`                               | [`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md) | **`/act`**, `@copilot /act`, `@codex /act` on a PR |
| `openadt-product`                   | `.agents/skills/openadt-product/SKILL.md`                       | fetch, proxy, MCP, transport choice                |
| `openadt-sdd`                       | `.agents/skills/openadt-sdd/SKILL.md`                           | Spec-driven changes, verify-spec-sync              |
| `openadt-local-sap-runtime`         | `.agents/skills/openadt-local-sap-runtime/SKILL.md`             | SDK runtime, fetch, proxy, bootstrap               |
| `openadt-devcontainer-host-runtime` | `.agents/skills/openadt-devcontainer-host-runtime/SKILL.md`     | Devcontainer / WSL vs host                         |

Index: [`.agents/skills/README.md`](../.agents/skills/README.md).

**On `/act`:** load the **`act` skill** ([`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md)) first. Codex uses the same skill + [AGENTS.md](../AGENTS.md) § Codex — not this Copilot-only section below.

## Copilot coding agent — GitHub access

Read-only GitHub MCP (~33 tools): no `pull_request_review_write` — use **`gh`** for resolve. **No Playwright** on github.com. **No** `gh pr view --json` from Bash if the agent firewall blocks GraphQL.

Assume **`gh` is installed and authenticated** (`gh auth status`).

## PR / review workflow (`/act`)

Follow [`.agents/skills/act/SKILL.md`](../.agents/skills/act/SKILL.md) and [`.github/instructions/act.instructions.md`](instructions/act.instructions.md).

**Do not** run `resolve-open-threads.sh` until review comments are **fixed in code** (or answered **in each thread**). The script only closes GitHub UI state; it does not implement feedback.

**Wrong:** resolve all threads with no replies, then post one PR comment “addressed feedback”.  
**Right:** per thread — fix or answer in that thread → commit → then resolve that thread (P4) → P6 evaluation → merge-ready only if cycle checks pass.

Order: read threads → product commits → **reply in every thread** → then:

```bash
bash .agents/skills/act/resolve-open-threads.sh abapify openadt <PR_NUMBER>
```

Before pushing TS under `tools/`: `bunx nx format:write`.

## Build and validate

```bash
bun install --frozen-lockfile
bun scripts/verify-spec-sync.ts
bunx nx format:check
./mvnw -q verify                                      # reactor from repo root
bun run openadt:test
bun run package:release -- --version=<semver>         # maintainers; Windows exe needs SDK
```

CI: `.github/workflows/ci.yml`. Release (manual dispatch): `.github/workflows/release.yml`. Packaging: `packaging/README.md`, `specs/packaging.md`.

## Must not commit

Real SIDs, hostnames, credentials, `~/.openadt/`, `tmp/` scratch (except gitignored), SAP jars/DLLs, `.devcontainer/dist/`. Use fictional fixtures from `AGENTS.md`.

## Tooling versions

Prefer **latest stable majors** for GitHub Actions (`checkout@v6`, `setup-java@v5`, `setup-bun@v2`, etc.). See `.cursor/rules/latest-stable-tooling.mdc` when editing workflows.
