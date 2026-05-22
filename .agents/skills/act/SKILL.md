---
name: act
description: >-
  Use when the user invokes /act on a change request, pull request, merge request,
  or code review. Acts on CI failures, review comments, suggestions, and merge
  blockers to move the PR toward merge-ready. Flags: --ci, --comments,
  --apply-suggestions, --resolve-threads.
disable-model-invocation: true
---

# /act

Use `/act` as a follow-up action command for a change request, pull request, merge request, or equivalent code review workflow. When invoked, the agent should inspect the current change request context, including CI pipeline status, failed checks, review comments, unresolved threads, inline suggestions, and general reviewer feedback, then take action to move the change request closer to a merge-ready state.

**Invocation:** `@agent_name /act [optional flags]`

**Examples:** `@copilot /act`, `@copilot /act --ci`, `@copilot /act --comments`, `@copilot /act --apply-suggestions`, `@copilot /act --resolve-threads`.

If arguments or flags are provided, follow them. If no arguments are provided, infer the required work from the current change request context.

## Expected behavior

- Acknowledge the request immediately with a reaction, preferably 👀.
- If 👀 is unavailable, use an equivalent reaction such as 👍 or 👎.
- Analyze the current change request:
  - CI/check failures
  - pipeline errors
  - review comments
  - unresolved threads
  - suggested code changes
  - merge blockers
- Fix the issues required to make the change request healthy:
  - repair failing tests or checks
  - update code
  - apply reviewer feedback
  - address comments
  - use “apply suggestion” or equivalent functionality when available
- Commit the changes using a clear, focused commit message.
- Resolve comment threads when they are fully addressed and the platform supports resolving them.
- Reply in the change request with a concise summary:
  - what was fixed
  - which comments or threads were addressed
  - whether CI is now expected to pass
  - any remaining blockers or follow-up needed

## Principles

- Treat `/act` as an instruction to act on existing findings, not to perform an open-ended review.
- Prefer minimal, targeted fixes over broad refactoring.
- Preserve the author’s intent and existing style.
- Do not resolve threads unless the underlying issue was actually addressed.
- Do not claim CI is green unless it has passed or the platform confirms it.
- Prefer native platform features such as reactions, suggested-change application, thread resolution, and pipeline re-runs when available.

## Flags

| Flag | Scope |
|------|--------|
| *(none)* | Infer from PR/MR state: fix whatever blocks merge |
| `--ci` | Failed checks, workflow logs, tests only |
| `--comments` | Human review comments and discussion only |
| `--apply-suggestions` | Inline/suggested edits only |
| `--resolve-threads` | Resolve threads already fixed; do not invent fixes to close threads |

## Workflow

1. Identify the change request (branch, PR/MR number, or URL from context).
2. Gather state with platform tools (`gh` for GitHub, GitLab CLI/API for GitLab, etc.).
3. Triage blockers in flag order; without flags, prioritize CI then blocking review comments.
4. Apply minimal fixes; run the same verification commands CI uses when possible.
5. Commit only when the user allows commits; use focused messages (`fix(ci): …`, `fix: address review …`).
6. Post a PR/MR reply summary; re-run or watch CI if the platform supports it.

## GitHub quick reference

```bash
gh pr view --json number,title,state,statusCheckRollup,reviewDecision,comments
gh pr checks
gh run list --branch "$(git branch --show-current)" --limit 5
gh run view <run-id> --log-failed
gh pr comment <number> --body "…"
```

For review threads: `gh api` GraphQL or the PR files/comments endpoints; resolve only after the fix is on the branch.
