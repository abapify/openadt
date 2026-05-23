---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. Always run the full pipeline: CI,
  all review fixes, suggestions, and resolve every handled thread until
  merge-ready. Idempotent re-runs. Do not narrow scope unless the user explicitly
  names a subset in the same message.
disable-model-invocation: true
---

# /act

Drive an open PR/MR to merge-ready. **Default `/act` runs every step below — nothing is optional.**

Re-runs must not duplicate commits or comments.

## Default scope (all true)

Unless the user **explicitly** asks for a subset in the **same** message (e.g. “only fix CI”), treat every step as **required**:

| Step                                                                       | Always on for `/act` |
| -------------------------------------------------------------------------- | -------------------- |
| Fix CI on HEAD (P0)                                                        | yes                  |
| Fix blocking review (P1)                                                   | yes                  |
| Fix non-blocking review / nits (P2)                                        | yes                  |
| Apply or decline inline suggestions (P3)                                   | yes                  |
| Reply in each handled thread                                               | yes                  |
| **Resolve conversation** on each handled thread (current **and** outdated) | yes                  |
| Post structured closing summary                                            | yes                  |

**Do not** skip resolve because code is already fixed. **Do not** skip outdated threads if the fix is on HEAD. **Do not** treat a single PR comment as a substitute for per-thread resolve.

Optional narrowing flags (`--ci`, etc.) are **deprecated in prompts** — ignore them unless the user literally typed that flag text.

## On start

1. React 👀 (or 👍/👎).
2. Snapshot PR state: checks on **HEAD**, **all** review threads (`resolved` / `outdated` / `path` / `id`), inline suggestions.
3. Build a work queue (P0→P3). Skip items already done (idempotency table).

## Work order

Do not skip a tier while a higher tier still blocks merge. After code/suggestions, run a **dedicated resolve pass** (see below) before claiming done.

| P   | Tier                                                     | Done when                                                  |
| --- | -------------------------------------------------------- | ---------------------------------------------------------- |
| 0   | **Merge blockers** — CI, conflicts, broken build on HEAD | Required checks green on latest commit (or re-run named)   |
| 1   | **Blocking review**                                      | Fix on branch + in-thread reply + **Resolve conversation** |
| 2   | **Non-blocking review**                                  | Fix or explicit reply + **Resolve conversation**           |
| 3   | **Inline suggestions**                                   | Apply/decline + **Resolve conversation**                   |
| 4   | **Hygiene**                                              | Only if still blocking readiness                           |

## Mandatory resolve pass (after P0–P3)

1. List **every** thread with `is_resolved: false` (include **outdated**).
2. For each: if fix/reply already on branch → short in-thread reply → **`resolve_thread`** (MCP) or UI **Resolve conversation** (Playwright).
3. Re-list threads; **unresolved count must be 0** for handled feedback before “merge-ready”.

Duplicate threads (same path/issue from different bots): one fix → resolve **all** matching open threads.

## Completion rule

Before finishing `/act`:

- [ ] Required CI on HEAD: passing or re-run pending (name the run).
- [ ] **Every** actionable review thread: answered + **resolved** (not only Cubic; include Copilot/Gemini/Amazon Q).
- [ ] Inline suggestions: applied or declined + resolved.
- [ ] Closing summary lists **resolved N / open 0** (with evidence).

Replying in one PR comment without resolving threads = **failed `/act`**.

**Stop early** only if the user explicitly scoped down in the same message — never because of time, CI pending, or “mostly done”.

## Idempotency

| Action     | Skip when                        |
| ---------- | -------------------------------- |
| Code fix   | Already on branch                |
| Commit     | Clean tree / same fix in HEAD    |
| Suggestion | Hunk already matches             |
| Resolve    | `is_resolved` already true       |
| PR summary | Post delta vs last agent summary |

No empty commits. No “merge-ready” while any addressable thread stays open.

## PR closing summary

1. **Status:** merge-ready / blocked
2. **Done this run:** P0…P3 + resolve pass (bullets)
3. **Threads:** resolved N, **open 0** (or list blockers)
4. **CI:** pass / failing / re-run
5. **Left:** must be empty for merge-ready

## Principles

- Act on existing review findings; do not open a new broad review.
- Minimal diffs.
- Do not claim green CI without evidence.
- Leaving addressable threads **unresolved** is a failed `/act`.

## GitHub Copilot coding agent (`@copilot /act`)

Firewall blocks Bash → `api.github.com` / GraphQL (`403`, DNS monitoring proxy). **Do not** whitelist `api.github.com` to fix this.

**Forbidden:** `gh api`, `gh api graphql`, `gh pr view --json`.

**Required:** `github-mcp-server` for PR/CI/review/resolve.

| Task        | MCP                                                                                                   |
| ----------- | ----------------------------------------------------------------------------------------------------- |
| Threads     | `pull_request_read` → `get_review_comments` → `review_threads[]` (`id`, `is_resolved`, `is_outdated`) |
| CI          | `get_check_runs`, `actions_list`                                                                      |
| Reply       | `reply_to_comment` / `add_reply_to_pull_request_comment`                                              |
| **Resolve** | `pull_request_review_write` → **`resolve_thread`**, `threadId` = `PRRT_…` from response               |

Resolve loop is **mandatory**, not a separate optional mode. If `id` is missing, use **Playwright** → **Resolve conversation** on each handled thread.

Before commit: `bunx nx format:write` on touched `tools/**/*.ts`.

## GitHub (local CLI / Cursor)

```bash
gh pr view --json number,statusCheckRollup,reviewDecision
gh pr checks
gh run list --branch "$(git branch --show-current)" --limit 3
gh run view <id> --log-failed
```

Resolve via GraphQL or UI after each handled thread:

```bash
gh api graphql -f query='
query($owner:String!, $repo:String!, $pr:Int!) {
  repository(owner:$owner, name:$repo) {
    pullRequest(number:$pr) {
      reviewThreads(first:100) {
        nodes { id isResolved isOutdated path }
      }
    }
  }
}' -f owner=ORG -f repo=REPO -F pr=NUMBER

gh api graphql -f query='
mutation($id:ID!) {
  resolveReviewThread(input:{threadId:$id}) { thread { isResolved } }
}' -f id=THREAD_ID
```
