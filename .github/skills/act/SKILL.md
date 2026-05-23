---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. Always run CI fixes, review fixes,
  suggestions, then resolve handled threads. Keep working on code even if resolve
  is blocked. Idempotent re-runs.
disable-model-invocation: true
---

# /act

Drive an open PR/MR toward merge-ready. **Default `/act` = all steps on** (CI → review → suggestions → resolve → summary).

Re-runs must not duplicate commits or comments.

## Default scope (all on)

| Step                                          | Required                |
| --------------------------------------------- | ----------------------- |
| P0 CI on HEAD                                 | yes                     |
| P1–P3 review / suggestions                    | yes                     |
| Reply in handled threads                      | yes                     |
| **Resolve conversation** (current + outdated) | yes — attempt every run |
| Closing summary                               | yes                     |

**Never stop the whole `/act`** because resolve is hard. If resolve is blocked, still do P0–P3, then report open threads with links.

**Do not use Playwright** for PR review, resolve, or GitHub UI. The browser is often blocked (`ERR_BLOCKED_BY_CLIENT`) and is out of scope for `/act`.

## Work order

| P   | Tier                | Done when                                       |
| --- | ------------------- | ----------------------------------------------- |
| 0   | CI / merge blockers | Required checks green on HEAD (or re-run named) |
| 1   | Blocking review     | Fix + in-thread reply + resolve                 |
| 2   | Non-blocking review | Fix or reply + resolve                          |
| 3   | Inline suggestions  | Apply/decline + resolve                         |
| 4   | Hygiene             | If needed for readiness                         |

Then **resolve pass** (below).

## Resolve pass (github-mcp-server only)

1. `pull_request_read` → `get_review_comments` → every `review_threads[]` with `is_resolved: false`.
2. Per thread: in-thread reply (fix already on branch? cite commit) → **`pull_request_review_write`** with **`method: resolve_thread`** and **`threadId`** = thread **`id`** (`PRRT_…` from MCP).
3. Re-fetch threads; report `resolved N`, `open M`.

**If `id` is missing on thread objects:** call **`pull_request_review_write` / `resolve_thread`** using any `PRRT_` id the tool docs expose, or list open threads in the summary with `html_url` for the author to resolve in the UI. **Do not open Playwright.**

Duplicate bot threads on the same issue: resolve **each** open thread after one fix.

## Completion

- **Merge-ready** only if CI green **and** `open M = 0` after resolve pass.
- If `open M > 0` only because MCP lacks thread ids / API blocked: status **blocked (resolve)** — list thread links; still counts as incomplete `/act` but not an excuse to skip code fixes.

One top-level PR comment does **not** replace per-thread resolve.

## Idempotency

Skip code/commits/suggestions/resolve when already done on HEAD. No empty commits.

## PR closing summary

1. Status: merge-ready / blocked (CI vs resolve)
2. Done: P0–P3 changes (commits)
3. Threads: resolved N, open M (with links if M > 0)
4. CI evidence
5. Left: empty only if merge-ready

## GitHub Copilot coding agent (`@copilot /act`)

- **Reads / CI / replies:** `github-mcp-server` (`pull_request_read`, `actions_*`, `reply_to_comment`, `add_reply_to_pull_request_comment`).
- **Resolve:** `pull_request_review_write` → **`resolve_thread`** + `threadId` from MCP. **No Playwright. No `gh pr view --json`.**
- **Avoid** `gh api` in Bash unless resolve is impossible without GraphQL and the repo firewall allows `api.github.com`; prefer MCP first.
- Before commit: `bunx nx format:write` on touched `tools/**/*.ts`.

## GitHub (local CLI / Cursor)

```bash
gh pr checks
gh run view <id> --log-failed
gh api graphql …   # list threads + resolveReviewThread when MCP ids unavailable
```
