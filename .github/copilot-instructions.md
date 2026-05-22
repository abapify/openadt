# OpenADT — Copilot repository instructions

Trust this file and **do not re-discover** layout each session. For full agent rules, read [AGENTS.md](../AGENTS.md) at the repo root.

## What this repo is

OpenADT: Java CLI (`apps/openadt-cli`) — local SAP ADT bridge (`setup`, `fetch`, `proxy`). Specs in `specs/` are authoritative for behavior. SAP binaries (JCo, sapcrypto, Secure Login) are **never** committed.

## Agent skills (read before specialized work)

Skills live under **`.agents/skills/<name>/SKILL.md`**. Index: [`.agents/skills/README.md`](../.agents/skills/README.md).

| Skill                               | When to read                                                   |
| ----------------------------------- | -------------------------------------------------------------- |
| `act`                               | User says **`/act`** or PR follow-up (CI, review, merge-ready) |
| `openadt-local-sap-runtime`         | Setup, fetch, proxy, JCo/SNC, Eclipse ADT                      |
| `openadt-devcontainer-host-runtime` | Devcontainer / WSL vs host SAP natives                         |

Invocation: read the matching `SKILL.md` **first**, then act. Skills are also listed in root `AGENTS.md`.

## PR / review workflow

On pull requests, follow **`.agents/skills/act/SKILL.md`**:

- Priority: CI (HEAD) → blocking review → nits/suggestions
- **Resolve conversation** on GitHub for every addressed thread (mandatory)
- Minimal fixes; idempotent re-runs (no duplicate commits or summaries)

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
