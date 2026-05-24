---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**,packaging/**,.codacy/**"
---

# Review tooling and APIs

Read this before querying “review findings”, “code quality alerts”, or “security scan results” on a PR.

These are **path-specific repository instructions** for GitHub Copilot. Codex/Cursor agents should read this file when triaging PR feedback outside Codacy’s own UI.

## Do not conflate these systems

| System                               | Owner                | What it is                                                                                                                                                          | How to list open items                                                                                 | Not the same as                                                                                                                                               |
| ------------------------------------ | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Codacy**                           | Codacy (third party) | Static analysis + AI reviewer configured in [`.codacy/`](../../.codacy/). Domain context: [`.codacy/instructions/review.md`](../../.codacy/instructions/review.md). | Codacy UI/API or Codacy MCP — **not** `gh api …/code-scanning`.                                        | GitHub Code Scanning, Dependabot                                                                                                                              |
| **GitHub Code Scanning**             | GitHub (GHAS)        | SARIF uploads from CodeQL, Semgrep Action, etc.                                                                                                                     | `gh api repos/{owner}/{repo}/code-scanning/alerts --jq '.[] \| select(.state=="open")'`                | Codacy, Dependabot                                                                                                                                            |
| **GitHub Code Quality**              | GitHub               | Copilot **code review** comments on the PR + optional quality dashboard signals.                                                                                    | PR **Files changed** review threads; `gh pr view NUMBER --comments`; Copilot review summary on the PR. | Codacy API, `code-scanning/alerts`                                                                                                                            |
| **Dependabot**                       | GitHub               | Dependency vulnerability/version alerts.                                                                                                                            | `gh api repos/{owner}/{repo}/dependabot/alerts --jq '.[] \| select(.state=="open")'`                   | Code Scanning, Codacy                                                                                                                                         |
| **Semgrep / Opengrep (repo config)** | This repo            | Local rules in [`.semgrep.yml`](../../.semgrep.yml); may also feed Code Scanning when uploaded as SARIF.                                                            | CI logs, `semgrep` locally, or Code Scanning alerts if wired.                                          | Whole-file `# nosemgrep` exclusions — use **line-specific** inline suppressions per [`.codacy/instructions/review.md`](../../.codacy/instructions/review.md). |

## Before claiming “N issues fixed”

1. **Name the source** (Codacy vs Code Scanning vs Dependabot vs inline review thread).
2. **Query that source’s API/UI** — do not infer counts from a different system.
3. **Verify on current HEAD** — re-run the query or refresh the PR checks after your commit.
4. **Per-item evidence** — list rule IDs or alert URLs, not a single aggregate number unless you fetched it from the correct API response.

## Where to put durable learnings

| Finding type                                            | Primary sink                                                                                                                                                  |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wrong API/tool used for triage                          | This file (extend the table or add a row)                                                                                                                     |
| Codacy reviewer false positive / domain context         | [`.codacy/instructions/review.md`](../../.codacy/instructions/review.md)                                                                                      |
| `/act` workflow mistake (resolve-only, premature merge) | [`.agents/skills/act/SKILL.md`](../../.agents/skills/act/SKILL.md) and append to [`.agents/skills/act/RETROSPECT.md`](../../.agents/skills/act/RETROSPECT.md) |
| Recurring pattern across PRs                            | [`.agents/skills/act/RETROSPECT.md`](../../.agents/skills/act/RETROSPECT.md)                                                                                  |

After `/act` P6, update **one** sink per finding — do not duplicate the same lesson in three files.
