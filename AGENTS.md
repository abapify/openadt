# OpenADT — agents

**SDD is mandatory.** [DESIGN.md](DESIGN.md) is the enforcement gate — not optional reading.

Product: `openadt fetch`, `openadt proxy` ([specs/vision.md](specs/vision.md)). MCP launcher: [specs/mcp.md](specs/mcp.md) (includes the official SAP server interface).

## SDD gate (before any code)

**No spec → no merge.** Undocumented behavior is out of scope for PRs.

| Step | Action                                                                                  |
| ---- | --------------------------------------------------------------------------------------- |
| 1    | Read [DESIGN.md](DESIGN.md) + the area spec from the table below                        |
| 2    | **Edit `specs/*.md` first** (same PR as code; spec-only PRs are fine)                   |
| 3    | Tests + implementation in the package from [apps/ARCHITECTURE.md](apps/ARCHITECTURE.md) |
| 4    | Run the [verify block](#verify-before-pr) — all must pass                               |

**Stop** if the change is not yet described in `specs/`: update the spec, then implement. Do not ship from chat summaries, agent notes, or gitignored `tmp/` (including decompiled SAP research) unless the contract is written in `specs/` first.

| Area                                              | Spec                                                                                                   |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| CLI commands, flags, exit codes                   | [specs/cli.md](specs/cli.md)                                                                           |
| Config / profiles                                 | [specs/config.md](specs/config.md)                                                                     |
| Proxy                                             | [specs/proxy.md](specs/proxy.md)                                                                       |
| Setup / detectors                                 | [specs/setup.md](specs/setup.md)                                                                       |
| SDK usage                                         | [specs/sdk-capabilities.md](specs/sdk-capabilities.md), [specs/sdk-services.md](specs/sdk-services.md) |
| **MCP / `adt-lsc` / stdio bridge / SAP HTTP MCP** | [specs/mcp.md](specs/mcp.md)                                                                           |
| Packaging / releases                              | [specs/packaging.md](specs/packaging.md)                                                               |
| Product scope                                     | [specs/vision.md](specs/vision.md)                                                                     |

Workflow detail: [openadt-sdd skill](.agents/skills/openadt-sdd/SKILL.md).

## Documentation map

| Doc                                                                | Purpose                                                        |
| ------------------------------------------------------------------ | -------------------------------------------------------------- |
| [DESIGN.md](DESIGN.md)                                             | **SDD enforcement** — spec gate, architecture, verify workflow |
| [specs/README.md](specs/README.md)                                 | Spec index + `verify-spec-sync`                                |
| [README.md](README.md)                                             | User-facing overview                                           |
| [docs/usage.md](docs/usage.md)                                     | Installed CLI (Scoop/Homebrew)                                 |
| [docs/contributing.md](docs/contributing.md)                       | Clone, build, test, devcontainer                               |
| [docs/integrations/abap-fs.md](docs/integrations/abap-fs.md)       | ABAP FS + `openadt proxy`                                      |
| [CONTRIBUTING.md](CONTRIBUTING.md)                                 | PR checklist (short)                                           |
| [REVIEW.md](REVIEW.md)                                             | PR review tools, `/act` sinks                                  |
| [SECURITY.md](SECURITY.md)                                         | Vulnerability reporting                                        |
| [apps/ARCHITECTURE.md](apps/ARCHITECTURE.md)                       | Maven modules, Java packages                                   |
| [.github/copilot-instructions.md](.github/copilot-instructions.md) | GitHub Copilot (repo-wide)                                     |

## Skills (load by task)

| Skill                               | Path                                                                                                                   | When                              |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| `act`                               | [.agents/skills/act/SKILL.md](.agents/skills/act/SKILL.md)                                                             | `/act` on a PR                    |
| `memory-bank`                       | [.agents/skills/memory-bank/SKILL.md](.agents/skills/memory-bank/SKILL.md)                                             | `/remember` — agent memory        |
| `retrospect`                        | [.agents/skills/retrospect/SKILL.md](.agents/skills/retrospect/SKILL.md)                                               | `/retrospect` — reflect + backlog |
| `backlog`                           | [.agents/skills/backlog/SKILL.md](.agents/skills/backlog/SKILL.md)                                                     | action items                      |
| `openadt-product`                   | [.agents/skills/openadt-product/SKILL.md](.agents/skills/openadt-product/SKILL.md)                                     | fetch, proxy, transport, MCP      |
| `openadt-sdd`                       | [.agents/skills/openadt-sdd/SKILL.md](.agents/skills/openadt-sdd/SKILL.md)                                             | spec → test → code                |
| `openadt-sap-sdk-apis`              | [.agents/skills/openadt-sap-sdk-apis/SKILL.md](.agents/skills/openadt-sap-sdk-apis/SKILL.md)                           | SDK discover / logon              |
| `openadt-local-sap-runtime`         | [.agents/skills/openadt-local-sap-runtime/SKILL.md](.agents/skills/openadt-local-sap-runtime/SKILL.md)                 | JCo, SNC, HTTP SSO, failures      |
| `openadt-devcontainer-host-runtime` | [.agents/skills/openadt-devcontainer-host-runtime/SKILL.md](.agents/skills/openadt-devcontainer-host-runtime/SKILL.md) | WSL / devcontainer vs host        |

`/act` helpers: [EVALUATE.md](.agents/skills/act/EVALUATE.md), `act/resolve-open-threads.sh`.

Index: [.agents/skills/README.md](.agents/skills/README.md).

## Packages

| Area                     | Package                                                  |
| ------------------------ | -------------------------------------------------------- |
| SDK client               | `org.openadt.sap.adt.sdk`                                |
| Destinations             | `org.openadt.sap.adt.destination`                        |
| JCo / Secure Login       | `org.openadt.sap.adt.bootstrap`                          |
| HTTP / REST-RFC fallback | `org.openadt.sap.adt.fallback.*`                         |
| Fetch / proxy            | `org.openadt.product.fetch`, `org.openadt.product.proxy` |
| Config / setup           | `org.openadt.config`, `org.openadt.bootstrap`            |
| CLI                      | `org.openadt.cli`                                        |

## Rules

1. **SDD** — follow the [SDD gate](#sdd-gate-before-any-code); [DESIGN.md](DESIGN.md) is the full spec index.
2. **Fixtures only** in git: `DEV`, `dev-ms.example.com`. No SAP jars, no real landscape.
3. **Host OS owns JCo natives** — run `./dev-openadt` from a clone (not bare `openadt` in the repo root on Windows CMD); see `openadt-devcontainer-host-runtime` skill.
4. **`tmp/`** for scratch and local SAP research; redact secrets in logs — mirror any product contract into `specs/` before code changes.
5. **Never pin models in agent configs** — let the agent inherit the active plan's default. A pinned `model` field in `kilo.jsonc` or per-agent overrides routes every invocation to the named provider, even if the user's plan is on a different/cheaper tier. This burns tokens against a balance the user did not intend. Drop the field; the active plan wins.
6. **Always-loaded instructions go in `AGENTS.md`** (root or per-subdir), or `CLAUDE.md` / `CONTEXT.md`. `kilo.jsonc`'s `instructions` array also works but is one extra hop. Do not put orchestrator self-instructions in undocumented locations like `.kilo/rules/*.md` that are not in Kilo's recognized auto-load list.

## Orchestrator self-instructions (PR work, multi-step tasks)

These apply when orchestrating `/act` or any multi-step PR workflow. Inline with the cloud-agent `act` skill.

- **PR head discovery** — before any push, run `gh pr view N --json headRefName,baseRefName,headRefOid`. `gh pr checkout N` creates a local `pr-NN` branch tracking `origin/pr-NN`; that is **not** the PR's actual source branch. Push to `origin/<headRefName>` and verify `git rev-parse HEAD == gh pr view N --json headRefOid`.
- **CI is the source of truth** — `bash scripts/ci-codescene-delta.sh <base> HEAD` locally is a smell-check. The gate is `gh pr checks N` on the current SHA. If a sibling agent claims "verify passed", re-run the gate on the **current** HEAD before declaring merge-ready.
- **Batch independent reads in one turn** — multiple `read`/`glob`/`grep`; multiple `task` calls. Bound parallel fan-out to 3–5 subagents per turn.
- **Subagent choice** — `explore` for read-only research; `general` for multi-step work with writes. Never spawn `general` for a read-only question. Pass file paths, not topics; specify return format.
- **3-push limit** — after 3 pushes on the same branch per `/act` cycle, stop and report back.
- **Scratch in `/tmp/agent_*/`** — never in the worktree root. The `nx format:write --uncommitted` pre-commit hook re-stages whatever sits there.
- **macOS portability** — `gsed`/`gdate` on Darwin; `sed`/`date` on Linux. Detect once via `uname -s`.
- **Design to 10.0 on the CodeScene delta** — target function CC ≤ 6 (hard cap 9); group args at 4+; extract method at 2+ cohesive blocks at depth ≥ 2; extract predicate at 2+ logical operators. Never inherit low-CC code into a small PR — split the refactor or suppress deltas in the CodeScene UI first.

## Code Health (CodeScene) — write clean-by-default

The CI gate at [`.github/workflows/codescene-delta.yml`](.github/workflows/codescene-delta.yml) runs `cs delta origin/<base> HEAD --error-on-warnings` on every PR. Code-writing agents must clear it on the first push, not chase it across three.

**Design-time ceilings** (from the default TS/JS CodeScene rules, mirrored in [`eslint.config.mjs`](eslint.config.mjs) for the `scripts/` and `.agents/skills/` tiers):

- Function cyclomatic complexity ≤ 9, file mean CC ≤ 4.
- Function LoC ≤ 70, file LoC ≤ 1000.
- Nesting depth ≤ 4; Bumpy Road bumps ≤ 2 (depth ≥ 2).
- Complex Conditional branches ≤ 2.
- Function arguments ≤ 4; constructor arguments ≤ 5.
- Primitive-arg % in a TS file ≤ 30.
- Duplication: ≥ 10 LoC @ ≥ 75 % similarity.
- Test suites: ≤ 3 large assertion blocks.

**Before any commit**, run:

```bash
# Strict gate on code-writing hot paths (0 errors, 0 warnings).
bunx eslint scripts/ .agents/skills/ --max-warnings 0
# Advisory on the rest of the TS surface (errors fail, warnings are visible).
bunx eslint .
# CodeScene delta — must match what CI does.
bash scripts/ci-codescene-delta.sh origin/<baseRefName> HEAD
```

Both must be clean. If a refactor is needed, use the `codescene-fix` subagent (see the "Orchestrator self-instructions" § above).

**Mental model:** design new code to 10.0 on the delta from the first push. Never inherit low-CC code into a small PR — split the refactor or suppress the affected deltas in CodeScene's UI before opening. **Stop after 3 pushes on the same branch per `/act` cycle** and report back.

## Verify (before PR)

```bash
bunx eslint scripts/ .agents/skills/ --max-warnings 0
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -Pdistribution
bun run openadt:test
```
