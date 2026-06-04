# Review and PR feedback

Where to triage comments and static analysis on OpenADT PRs. Agents hub: [AGENTS.md](AGENTS.md). **`/act` workflow:** [.agents/skills/act/SKILL.md](.agents/skills/act/SKILL.md).

## Do not mix these systems

| System                   | List open items via                                      | Not the same as                                        |
| ------------------------ | -------------------------------------------------------- | ------------------------------------------------------ |
| **Codacy**               | Codacy UI / MCP                                          | `gh api …/code-scanning`                               |
| **GitHub Code Scanning** | `gh api repos/{o}/{r}/code-scanning/alerts` (state=open) | Codacy                                                 |
| **PR review threads**    | `gh pr view` / Files changed                             | Code Scanning API                                      |
| **Dependabot**           | `gh api …/dependabot/alerts`                             | Codacy, Semgrep                                        |
| **Semgrep / Opengrep**   | CI `opengrep`, root [`.semgrep.yaml`](.semgrep.yaml)     | Whole-file `# nosemgrep` — use line-level suppressions |

Before claiming “N issues fixed”: name the **source**, query it on **current HEAD**, cite rule IDs or thread URLs.

## OpenADT review context

- **Loopback bind** (`127.0.0.1`, SSO callback, hub TLS probe) is intentional — not SSRF. Details: [.codacy/instructions/review.md](.codacy/instructions/review.md).
- **TOML keys** like `http_truststore_password` are field names, not secrets — Semgrep hard-coded-password rules are disabled in [`.semgrep.yaml`](.semgrep.yaml).
- **Fixtures only** in repo: `DEV`, `DEVELOPER`, `dev-ms.example.com`, `p:CN=SAPServiceDEV`. No real landscape in commits.

## Durable learnings (one sink each)

| Finding                        | Update                                                                                                                               |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| Wrong review API               | This file                                                                                                                            |
| Codacy false positive / domain | [`.codacy/instructions/review.md`](.codacy/instructions/review.md)                                                                   |
| `/act` mistakes                | [`.agents/skills/act/SKILL.md`](.agents/skills/act/SKILL.md), [`.agents/skills/act/RETROSPECT.md`](.agents/skills/act/RETROSPECT.md) |

## PR hygiene

- Do not edit PR title/body unless asked.
- Per-thread reply + product fix before `resolve-open-threads.sh`.
- Verify: `bun scripts/verify-spec-sync.ts`, `bun scripts/verify-package-docs.ts`, `./mvnw -q verify -Pdistribution`, `bun run openadt:test`.
