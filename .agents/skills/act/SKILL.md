---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. Fix blockers in priority order (CI
  → required review → suggestions → threads) until merge-ready; idempotent
  re-runs; no unresolved review threads left behind.
disable-model-invocation: true
---

# /act

Drive an open PR/MR to merge-ready: work **most critical first**, finish **all** actionable review items. Re-runs must not duplicate commits or comments.

**Invoke:** `/act` or `--ci` | `--comments` | `--apply-suggestions` | `--resolve-threads`  
Flags narrow scope; default `/act` runs the full sequence below.

## On start

1. React 👀 (or 👍/👎).
2. Snapshot PR state: checks on **HEAD**, unresolved review threads, open conversations, inline suggestions, draft notes.
3. Build a **work queue** (priority order). Skip items already done (idempotency table).

## Priority queue (default `/act`)

Work top → bottom. Do not skip a tier while a higher tier still blocks merge or has **unresolved** required feedback.

| P | Tier | Includes | Done when |
|---|------|----------|-----------|
| 0 | **Merge blockers** | Failing required CI, merge conflicts, broken build/test on HEAD | Required checks green (or platform confirms pass) on latest commit |
| 1 | **Blocking review** | “Must fix”, changes requested, security/correctness threads | Fix on branch + reply + **Resolve conversation** |
| 2 | **Non-blocking review** | Questions, nits, style, optional improvements | Code or **explicit PR reply** + **Resolve conversation** |
| 3 | **Inline suggestions** | GitHub “Apply suggestion” / equivalent | Apply/decline + **Resolve conversation** |
| 4 | **Hygiene** | Stale bot comments, label/check noise | Only if still blocking perception of readiness |

**Flags:** `--ci` → P0 only. `--comments` → P1–P2. `--apply-suggestions` → P3. `--resolve-threads` → resolve threads whose fix/reply is already on branch (P1–P3 after the fact).

## Completion rule (no loose ends)

Before finishing `/act`, verify:

- [ ] Required CI on HEAD: passing or explicitly re-run and pending (state named in summary).
- [ ] **Every human review thread** on the PR: fixed or answered in-thread, then **Resolve conversation** on GitHub (mandatory — not optional).
- [ ] No ignored comment without a PR-visible response (reply in thread beats silence).
- [ ] Inline suggestions: applied or declined with one-line reason in thread, then **Resolve conversation**.

If something cannot be fixed in-repo (needs product decision, external dependency), say so **in that thread**, then **Resolve conversation** — never leave it open.

**GitHub “Resolve conversation” is required** for every addressed thread. Replying without resolving counts as incomplete `/act`.

**Stop early** only when the queue is empty or the user scoped a flag and that scope is complete — not while unresolved threads remain (unless `--ci` alone was requested).

## Idempotency

| Action | Skip when |
|--------|-----------|
| Code fix | Already on branch |
| Commit | Clean tree / same fix in HEAD |
| Suggestion | Hunk already matches |
| Resolve | Already resolved |
| PR summary | Post **delta** vs last agent summary; “merge-ready” only if completion rule passes |
| CI work | HEAD already green |

No empty commits. No duplicate summaries. Use latest workflow run for HEAD.

## PR closing summary

One comment, structured:

1. **Status:** merge-ready / blocked (why)
2. **Done this run:** P0…Pn items (bullet list)
3. **Threads:** resolved N, replied M (link or numbers)
4. **CI:** pass / failing check names / re-run triggered
5. **Left:** only items that need author or reviewer (must be none for “merge-ready”)

## Principles

- Act on findings, not a new broad review.
- Minimal diffs; preserve author intent.
- Do not claim green CI without evidence.
- Do not **Resolve conversation** without a fix or a written answer on the branch/PR.
- Leaving any addressable thread **unresolved** on GitHub is a failed `/act`.

## GitHub

```bash
gh pr view --json number,statusCheckRollup,reviewDecision
gh pr checks
gh run list --branch "$(git branch --show-current)" --limit 3
gh run view <id> --log-failed
```

**Resolve conversation (required):** use GraphQL `resolveReviewThread` / `pullRequestReviewThread` after each fix or reply. List open threads first; exit `/act` only when none remain for handled feedback.

```bash
# List unresolved review threads (GraphQL)
gh api graphql -f query='
query($owner:String!, $repo:String!, $pr:Int!) {
  repository(owner:$owner, name:$repo) {
    pullRequest(number:$pr) {
      reviewThreads(first:100) {
        nodes { id isResolved path line comments(last:1){nodes{body}} }
      }
    }
  }
}' -f owner=ORG -f repo=REPO -F pr=NUMBER

# Resolve one thread
gh api graphql -f query='
mutation($id:ID!) {
  resolveReviewThread(input:{threadId:$id}) { thread { isResolved } }
}' -f id=THREAD_ID
```

If the UI is available, **Resolve conversation** on each thread is equivalent and mandatory.
