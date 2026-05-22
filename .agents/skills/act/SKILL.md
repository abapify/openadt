---
name: act
description: >-
  Use when the user invokes /act on a PR/MR or code review. Fix CI, apply
  feedback, and clear merge blockers. Idempotent on re-run. Flags: --ci,
  --comments, --apply-suggestions, --resolve-threads.
disable-model-invocation: true
---

# /act

Follow-up on an open change request: read CI, review comments, suggestions, and threads; fix what blocks merge; commit; summarize in the PR.

**Invoke:** `/act` or `/act --ci` | `--comments` | `--apply-suggestions` | `--resolve-threads`  
Without flags, infer work from current PR state (CI first, then blocking review).

## On start

1. React with 👀 (or 👍/👎 if unavailable).
2. Load PR/MR context (`gh pr view`, checks, comments, unresolved threads).
3. **Idempotency pass** (see below). If nothing actionable remains, reply briefly and stop — no drive-by edits.

## Do

- Targeted fixes only; match repo style; verify with the same commands CI uses.
- One focused commit per logical fix batch (only if user allows commits).
- Resolve threads only when the branch already contains the fix.
- PR reply: what changed, which threads addressed, CI status (passing / expected / still failing), remaining blockers.

## Do not

- Open-ended review or drive-by refactors.
- Claim green CI without a passing check or explicit platform confirmation.
- Close threads without a real fix on the branch.

## Idempotency (re-runs must not duplicate)

Before each action, check whether it is **already done**:

| Action | Skip when |
|--------|-----------|
| Code change | Diff already implements the suggestion; tree clean for that item |
| Commit | `git status` clean or change already in latest commit message for this fix |
| Apply suggestion | Hunk already matches suggestion on current branch |
| Resolve thread | Thread already resolved, or fix not on branch yet |
| PR comment | Prior `/act` summary on this PR covers the same state (read recent bot/agent comments); post only **delta** or “no changes needed” |
| CI fix | Latest run on HEAD already green for required checks |
| Push / re-run | No new commits since last push; workflow already running for same SHA |

**Rules:**

- Never create an empty commit.
- Never post the same summary twice; on re-run say what is *still* open or confirm merge-ready.
- Prefer `gh pr checks` / latest workflow run on **current HEAD**, not stale failures from an old SHA.
- If the user ran `/act` twice in a row with no new review/CI signal, stop after the idempotency check.

## Flags

| Flag | Only |
|------|------|
| `--ci` | Failing checks / logs |
| `--comments` | Review discussion |
| `--apply-suggestions` | Inline suggestions |
| `--resolve-threads` | Resolve already-fixed threads (no new code unless required to unblock resolve) |

## GitHub

```bash
gh pr view --json number,statusCheckRollup,commits
gh pr checks
gh run list --branch "$(git branch --show-current)" --limit 3
gh run view <id> --log-failed
```
