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

| Skill                               | Trigger                                                        |
| ----------------------------------- | -------------------------------------------------------------- |
| `act`                               | `/act` on a PR — CI, review, **Resolve conversation** required |
| `openadt-local-sap-runtime`         | SAP runtime, fetch, proxy, setup                               |
| `openadt-devcontainer-host-runtime` | Devcontainer / WSL vs host                                     |

**GitHub Copilot** (cloud agent): `.github/copilot-instructions.md` for Copilot-only `/act` rules (MCP, firewall).

**OpenAI Codex** (cloud on GitHub): reads this file and `.agents/skills/act/SKILL.md` for `@codex /act` — use the shared skill, not Copilot instructions.

**Cursor** and other agents: `.agents/skills/<name>/SKILL.md`; global copies may exist under `~/.agents/skills/`.

## Codex (`@codex /act` on a pull request)

When a comment says **`@codex /act`** (or `/act` on a PR), run the full **`act`** workflow in [`.agents/skills/act/SKILL.md`](.agents/skills/act/SKILL.md):

1. Fix CI on HEAD, then all actionable review items (P0→P3).
2. **Resolve pass:** run `bash .agents/skills/act/resolve-open-threads.sh OWNER REPO NUMBER` when `gh` is installed and authenticated.
3. Report merge-ready only when CI is green and open review threads = 0.

Codex may also support phrasing like “address unresolved review comments” — treat that the same as the resolve pass in the act skill.

**Do not** use GitHub Copilot SWE rules (read-only MCP, Playwright) unless you are Copilot.

If resolve fails (`gh auth` or GraphQL error), report it and list open thread URLs; still complete code/CI fixes.
