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

## Code Health (CodeScene) — write clean-by-default

The CI gate at [`.github/workflows/codescene-delta.yml`](.github/workflows/codescene-delta.yml)
runs `cs delta origin/<base> HEAD --error-on-warnings` on every PR. Code-writing
agents must clear it on the first push, not chase it across three.

**Design-time ceilings** (from `.codescene/code-health-rules.json`):

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
bunx eslint . --max-warnings 0
bash scripts/ci-codescene-delta.sh origin/<baseRef> HEAD
```

Both must be clean. If a refactor is needed, load
[`.agents/skills/openadt-codescene/SKILL.md`](.agents/skills/openadt-codescene/SKILL.md)
first. The full contract is in
[`docs/codescene.md`](docs/codescene.md).

**Mental model:** design new code to 10.0 on the delta from the first push.
Never inherit low-CC code into a small PR — either split the refactor into
its own PR or suppress the deltas in CodeScene's UI before opening.

**Stop after 3 pushes on the same branch per `/act` cycle** and report back.

## Verify (before PR)

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -Pdistribution
bun run openadt:test
```
