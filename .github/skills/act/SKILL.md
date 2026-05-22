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

| P   | Tier                    | Includes                                                        | Done when                                                          |
| --- | ----------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------ |
| 0   | **Merge blockers**      | Failing required CI, merge conflicts, broken build/test on HEAD | Required checks green (or platform confirms pass) on latest commit |
| 1   | **Blocking review**     | “Must fix”, changes requested, security/correctness threads     | Fix on branch + reply + **Resolve conversation**                   |
| 2   | **Non-blocking review** | Questions, nits, style, optional improvements                   | Code or **explicit PR reply** + **Resolve conversation**           |
| 3   | **Inline suggestions**  | GitHub “Apply suggestion” / equivalent                          | Apply/decline + **Resolve conversation**                           |
| 4   | **Hygiene**             | Stale bot comments, label/check noise                           | Only if still blocking perception of readiness                     |

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

| Action     | Skip when                                                                          |
| ---------- | ---------------------------------------------------------------------------------- |
| Code fix   | Already on branch                                                                  |
| Commit     | Clean tree / same fix in HEAD                                                      |
| Suggestion | Hunk already matches                                                               |
| Resolve    | Already resolved                                                                   |
| PR summary | Post **delta** vs last agent summary; “merge-ready” only if completion rule passes |
| CI work    | HEAD already green                                                                 |

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

## GitHub Copilot coding agent (`@copilot /act`)

This runtime runs in **GitHub Actions** with a **firewall**. Direct calls from Bash to `https://api.github.com` (including GraphQL) return **HTTP 403** (`Blocked by DNS monitoring proxy`). That is expected — **do not** add `api.github.com` to the repo firewall allowlist to “fix” it.

### Forbidden in Copilot sessions

- `gh api graphql`, `gh api`, and any `gh` subcommand that hits `api.github.com/graphql`
- `gh pr view --json …` (uses GraphQL under the hood)

### Required: `github-mcp-server`

Use MCP tools for all GitHub reads/writes:

| Task                     | MCP approach                                                                                   |
| ------------------------ | ---------------------------------------------------------------------------------------------- |
| PR comments / reviews    | `pull_request_read` — `get_comments`, `get_reviews`, `get_review_comments`                     |
| CI / Actions             | `pull_request_read` — `get_check_runs`; `actions_list` / `actions_get`                         |
| Reply in thread          | `reply_to_comment` or `add_reply_to_pull_request_comment`                                      |
| **Resolve conversation** | `pull_request_review_write` — method **`resolve_thread`**, `threadId` = thread `id` (`PRRT_…`) |

**List threads:** `pull_request_read` → `get_review_comments` → `review_threads[]` with `is_resolved`, `path`, and **`id`** (GraphQL node id). Count unresolved before claiming done.

**Resolve one thread** (after fix + reply on branch):

```json
{
  "method": "resolve_thread",
  "owner": "ORG",
  "repo": "REPO",
  "pullNumber": 2,
  "threadId": "PRRT_kwDO…"
}
```

- Use only **`id` values from the MCP response** — never invent `PRRT_` ids.
- If `id` is missing on threads, upgrade/use current `github-mcp-server`, or fall back to **Playwright**: open the PR, click **Resolve conversation** on each handled thread.

### Playwright fallback (resolve only)

When MCP cannot resolve: navigate to the PR → snapshot → **Resolve conversation** per handled thread. Mandatory if GraphQL/`gh` is blocked.

### Before commit (TypeScript)

Run `bunx nx format:write` on touched files under `tools/` (CI runs `nx format:check`).

### Copilot `/act` is incomplete if

- Any handled review thread is still **unresolved** on GitHub, or
- Required CI on HEAD is failing without a named re-run, or
- Summary claims “merge-ready” while unresolved threads remain.

## GitHub (local CLI / Cursor)

When `gh` can reach the API (no Copilot firewall):

```bash
gh pr view --json number,statusCheckRollup,reviewDecision
gh pr checks
gh run list --branch "$(git branch --show-current)" --limit 3
gh run view <id> --log-failed
```

**Resolve conversation** via GraphQL after each fix or reply:

```bash
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

gh api graphql -f query='
mutation($id:ID!) {
  resolveReviewThread(input:{threadId:$id}) { thread { isResolved } }
}' -f id=THREAD_ID
```

UI **Resolve conversation** is equivalent and mandatory.
