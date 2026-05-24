# OpenADT Agent Guidelines

Read specs first, prefer TDD, keep SAP binaries external, never commit private SAP landscape data, redact secrets, and update specs when command behavior changes.

## Rules

1. Read specs/ before broad implementation changes
2. Prefer TDD: write tests first for config parsing, request/response mapping, proxy auth, header redaction, setup detectors
3. SAP/JCo integration tests must be opt-in (annotated @Tag("integration")) and skipped by default
4. Keep SAP binaries external: never bundle or commit JCo jars, sapcrypto.dll, or Secure Login Client files
5. **Never commit private SAP landscape data** — no real SIDs, clients, usernames, logon groups, hostnames/FQDNs, SNC partner names, Secure Login profile UUIDs, discovery URLs, or organization names. Use only fictional fixtures: `DEV`, `DEVELOPER`, `dev-ms.example.com`, `DEV_100_developer_en`, `PUBLIC`, `p:CN=SAPServiceDEV`, and clearly fake UUIDs. Do not copy values from the user's machine, live command output, or `~/.openadt/config.toml` into the repo.
6. Redact secrets in logs: never log SAP credentials, cookies, tickets, SNC tokens, or authorization headers
7. Update specs/ when command behavior changes
8. Put temporary repo-local artifacts under `tmp/`, which must stay gitignored; do not leave ad hoc scratch files elsewhere in the workspace

## Specs

- specs/cli.md — CLI contract and command behavior
- specs/config.md — Config file format and field descriptions
- specs/proxy.md — Proxy server behavior and security requirements
- specs/setup.md — Setup analyzer behavior and detector descriptions

## Skills

Reusable workflows: **`.agents/skills/<name>/`** — `SKILL.md` plus optional helpers (e.g. `act/resolve-open-threads.sh`). Index: `.agents/skills/README.md`. Copilot also reads `.github/copilot-instructions.md`.

| Skill                               | Trigger                                                         |
| ----------------------------------- | --------------------------------------------------------------- |
| `act`                               | `/act` on a PR — fix review in code first, then resolve threads |
| `openadt-local-sap-runtime`         | SAP runtime, fetch, proxy, setup                                |
| `openadt-devcontainer-host-runtime` | Devcontainer / WSL vs host                                      |

**GitHub Copilot** (cloud agent / code review on GitHub): [`.github/copilot-instructions.md`](.github/copilot-instructions.md) (repository-wide) plus path-specific [`.github/instructions/`](.github/instructions/) — see [GitHub docs](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions). **Never edit PR title or description** unless the user asks.

**OpenAI Codex** (cloud on GitHub): reads this file and `.agents/skills/act/SKILL.md` for `@codex /act` — use the shared skill, not Copilot instructions.

**Cursor** and other agents: `.agents/skills/<name>/SKILL.md`; global copies may exist under `~/.agents/skills/`.

## Cloud agents on GitHub (`@codex /act`, `@claude /act`)

**`/act` is not “resolve all threads”.** It means: implement review feedback in **product code**, reply **in each thread**, **then** resolve. **Do not edit PR title or description** unless the user explicitly asks.

Follow [`.agents/skills/act/SKILL.md`](.agents/skills/act/SKILL.md):

1. List every **open** review thread and what change each requires.
2. Fix CI (P0), then **code + in-thread reply** for P1–P3 on each thread.
3. Only then run `resolve-open-threads.sh` (P4).
4. Never claim merge-ready if you only closed conversations without product commits addressing the comments.

Phrases like “address review comments” still mean **full `/act`** (code fixes first), not resolve-only.

**Do not** use GitHub Copilot SWE rules (read-only MCP, Playwright) unless you are Copilot.

If resolve fails (`gh auth` or GraphQL error), report it and list open thread URLs; still complete code/CI fixes.
