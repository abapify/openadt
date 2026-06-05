# Review and PR feedback

Where to triage comments and static analysis on OpenADT PRs. Agents hub: [AGENTS.md](AGENTS.md). **`/act` workflow:** [.agents/skills/act/SKILL.md](.agents/skills/act/SKILL.md).

## Do not mix these systems

| System                             | List open items via                                                   | Not the same as                                        |
| ---------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------ |
| **Codacy**                         | Codacy UI / MCP                                                       | `gh api …/code-scanning`                               |
| **GitHub Code Scanning**           | `gh api repos/{o}/{r}/code-scanning/alerts` (state=open)              | Codacy                                                 |
| **PR review threads**              | `gh pr view` / Files changed                                          | Code Scanning API                                      |
| **Dependabot**                     | `gh api …/dependabot/alerts`                                          | Codacy, Semgrep                                        |
| **Semgrep / Opengrep**             | CI `opengrep`, root [`.semgrep.yaml`](.semgrep.yaml)                  | Whole-file `# nosemgrep` — use line-level suppressions |
| **CodeScene PR Refactoring Agent** | CI `.github/workflows/refactoring-agent.yml` (`/cs-agent` PR comment) | Codacy, GitHub Code Scanning                           |

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

## CodeScene PR Refactoring Agent

Reviewers can request Code Health-guided refactoring on any PR by commenting `/cs-agent` (workflow: [`.github/workflows/refactoring-agent.yml`](.github/workflows/refactoring-agent.yml); docs: [CodeScene PR Refactoring Agent](https://codescene.io/docs/developer-tools/pr-refactoring-agent.html)). The agent pushes changes back to the PR branch, so treat its commits like any other contribution: re-run the verify chain above and re-check review threads.

Configure under _Settings → Secrets and variables → Actions_:

- **Variable** `CS_AGENT_MODEL` — backing model id (e.g. `anthropic/claude-sonnet-4-6-20251101` or a Kilo/OpenCode model).
- **Secrets** `CS_ACCESS_TOKEN` plus at least one AI provider: `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GOOGLE_API_KEY`, or Kilo/OpenCode via `OPENCODE_AUTH_JSON` (optional `KILO_API_KEY`).
