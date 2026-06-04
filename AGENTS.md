# OpenADT — agents

**SDD is mandatory:** read **[DESIGN.md](DESIGN.md)** (enforcement gate) before any behavior or spec change. Product: `openadt fetch`, `openadt proxy` ([specs/vision.md](specs/vision.md)).

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

| Skill                               | Path                                                                                                                   | When                         |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| `act`                               | [.agents/skills/act/SKILL.md](.agents/skills/act/SKILL.md)                                                             | `/act` on a PR               |
| `openadt-product`                   | [.agents/skills/openadt-product/SKILL.md](.agents/skills/openadt-product/SKILL.md)                                     | fetch, proxy, transport, MCP |
| `openadt-sdd`                       | [.agents/skills/openadt-sdd/SKILL.md](.agents/skills/openadt-sdd/SKILL.md)                                             | spec → test → code           |
| `openadt-sap-sdk-apis`              | [.agents/skills/openadt-sap-sdk-apis/SKILL.md](.agents/skills/openadt-sap-sdk-apis/SKILL.md)                           | SDK discover / logon         |
| `openadt-local-sap-runtime`         | [.agents/skills/openadt-local-sap-runtime/SKILL.md](.agents/skills/openadt-local-sap-runtime/SKILL.md)                 | JCo, SNC, HTTP SSO, failures |
| `openadt-devcontainer-host-runtime` | [.agents/skills/openadt-devcontainer-host-runtime/SKILL.md](.agents/skills/openadt-devcontainer-host-runtime/SKILL.md) | WSL / devcontainer vs host   |

`/act` helpers: [EVALUATE.md](.agents/skills/act/EVALUATE.md), [RETROSPECT.md](.agents/skills/act/RETROSPECT.md), `act/resolve-open-threads.sh`.

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

1. **SDD** — [DESIGN.md](DESIGN.md) → update `specs/*.md` with behavior changes (no undocumented product behavior).
2. **Fixtures only** in git: `DEV`, `dev-ms.example.com`. No SAP jars, no real landscape.
3. **Host OS owns JCo natives** — see `openadt-devcontainer-host-runtime` skill.
4. **`tmp/`** for scratch; redact secrets in logs.

## Verify (before PR)

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -Pdistribution
bun run openadt:test
```
