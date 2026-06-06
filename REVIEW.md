# Review and PR feedback

Where to triage comments and static analysis on OpenADT PRs. Agents hub: [AGENTS.md](AGENTS.md). **`/act` workflow:** [.agents/skills/act/SKILL.md](.agents/skills/act/SKILL.md).

## Do not mix these systems

| System                             | List open items via                                                                                                | Not the same as                                        |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| **Codacy**                         | Codacy UI / MCP                                                                                                    | `gh api …/code-scanning`                               |
| **GitHub Code Scanning**           | `gh api repos/{o}/{r}/code-scanning/alerts` (state=open)                                                           | Codacy                                                 |
| **PR review threads**              | `gh pr view` / Files changed                                                                                       | Code Scanning API                                      |
| **Dependabot**                     | `gh api …/dependabot/alerts`                                                                                       | Codacy, Semgrep                                        |
| **Semgrep / Opengrep**             | CI `opengrep`, root [`.semgrep.yaml`](.semgrep.yaml)                                                               | Whole-file `# nosemgrep` — use line-level suppressions |
| **CodeScene GitHub App**           | PR comment `codescene-delta-analysis`, required check in branch rules                                              | CodeScene CLI job below                                |
| **CodeScene CLI**                  | CI [`.github/workflows/codescene-delta.yml`](.github/workflows/codescene-delta.yml) — delta log; fails on findings | CodeScene App                                          |
| **CodeScene PR Refactoring Agent** | CI `.github/workflows/refactoring-agent.yml` (`/cs-agent` PR comment)                                              | Codacy, GitHub Code Scanning                           |

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

## CodeScene delta (CLI in CI + GitHub App)

| Layer                                       | Role                                                                                                   | Blocks merge?                      |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------- |
| **GitHub App** (`codescene-delta-analysis`) | Official delta comment, per-finding **Suppress** links, quality-gate profile (e.g. Pay Down Tech Debt) | Yes, when required in branch rules |
| **CLI** (`CodeScene delta` job)             | `cs delta --error-on-warnings` — human-readable log; job **red** on findings                           | Yes, when required in branch rules |

Workflow: [`.github/workflows/codescene-delta.yml`](.github/workflows/codescene-delta.yml). CI runner: [`scripts/ci-codescene-delta.sh`](scripts/ci-codescene-delta.sh) (`codescene/codescene-mcp` image, `--entrypoint cs` — no runtime download from `downloads.codescene.io`). Local install (optional): [`scripts/ci-install-codescene-cli.sh`](scripts/ci-install-codescene-cli.sh). Runs on `pull_request` only; compares `origin/<base>` to `HEAD` (`fetch-depth: 0`).

**Required secret:** `CS_ACCESS_TOKEN` — **abapify org secret** ([CodeScene PAT](https://codescene.io/users/me/pat); not visible in `gh secret list -R abapify/openadt`). Must be granted to the `openadt` repo. Empty → `CodeScene CI not configured`; rejected → `CodeScene PAT rejected (403)` (retry run if transient).

Agents: `gh pr checks` for red/green; `gh run view <id> --log-failed` for delta output.

Local equivalent (`CS_ACCESS_TOKEN` + [CLI install](https://codescene.io/docs/cli/index.html)):

```bash
cs delta main HEAD --error-on-warnings
```

Do **not** treat a green `CI / main` job as “CodeScene passed” — check the App and/or `CodeScene delta` if required.

## CodeScene PR Refactoring Agent

Reviewers can request Code Health-guided refactoring on any PR by commenting `/cs-agent` (workflow: [`.github/workflows/refactoring-agent.yml`](.github/workflows/refactoring-agent.yml); docs: [CodeScene PR Refactoring Agent](https://codescene.io/docs/developer-tools/pr-refactoring-agent.html)). The agent pushes changes back to the PR branch, so treat its commits like any other contribution: re-run the verify chain above and re-check review threads.

Configure under _abapify → Settings → Secrets and variables → Actions_ (org secrets):

- **Secrets** `CS_ACCESS_TOKEN` — org secret shared by `/cs-agent` and CodeScene delta; plus at least one AI provider for the agent: `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GOOGLE_API_KEY`, or Kilo/OpenCode via `OPENCODE_AUTH_JSON` (optional `KILO_API_KEY`).
- **Variable** `CS_AGENT_MODEL` — repo/org variable (e.g. `kilo-auto/free`).
