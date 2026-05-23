---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. Always run CI fixes, review fixes,
  suggestions, then attempt resolve. Copilot often has read-only GitHub MCP only.
  Idempotent re-runs.
disable-model-invocation: true
---

# /act

Drive an open PR/MR toward merge-ready. **Default `/act` = all steps on** (CI → review → suggestions → resolve attempt → summary).

## Default scope (all on)

| Step                       | Required |
| -------------------------- | -------- |
| P0 CI on HEAD              | yes      |
| P1–P3 review / suggestions | yes      |
| Resolve pass (attempt)     | yes      |
| Closing summary            | yes      |

**Never abort the whole `/act`** because resolve is blocked — still do P0–P3, then report what blocked resolve.

**No Playwright** for GitHub PR UI (often `ERR_BLOCKED_BY_CLIENT`).

## Work order

| P   | Tier                 | Done when                                  |
| --- | -------------------- | ------------------------------------------ |
| 0   | CI / merge blockers  | Required checks green on HEAD              |
| 1–3 | Review / suggestions | Fix or reply + resolve each handled thread |
| 4   | Hygiene              | If needed                                  |

## Copilot: check MCP tools first

At session start, read the **github-mcp-server tool list** from verbose logs.

### Read-only MCP (~33 tools) — typical for `@copilot`

If the list includes `pull_request_read`, `actions_list`, … but **does NOT** include:

- `pull_request_review_write`
- `add_pull_request_review_comment` (or `add_reply_to_pull_request_comment`)

then the repo MCP is **read-only** (`https://api.githubcopilot.com/mcp/readonly`). The agent **cannot** call `resolve_thread` via MCP — not a skill bug.

**With read-only MCP, `/act` must:**

1. Still fix code / CI (git + `pull_request_read`, `actions_*`, `get_file_contents`).
2. **Resolve pass:** use Bash only if allowed:

```bash
GH_TOKEN="$GITHUB_TOKEN" gh api graphql -f query='query($o:String!,$r:String!,$pr:Int!){
  repository(owner:$o,name:$r){pullRequest(number:$pr){
    reviewThreads(first:100){nodes{id isResolved}}}
}}' -f o=OWNER -f r=REPO -F pr=NUMBER

GH_TOKEN="$GITHUB_TOKEN" gh api graphql -f query='mutation($id:ID!){
  resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}' -f id=PRRT_…
```

3. If GraphQL is blocked or returns 403: status **blocked (resolve)** — list each open thread `html_url`; author resolves in UI or enables write MCP.

**To enable MCP resolve (repo admin):** Settings → Copilot → Cloud agent → MCP — remove `/readonly` from the GitHub MCP URL, or use `https://api.githubcopilot.com/mcp/` with header `X-MCP-Toolsets` including `pull_requests` and allow tool `pull_request_review_write`. See [GitHub docs: extend cloud agent with MCP](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/customize-cloud-agent/extend-cloud-agent-with-mcp).

### Write MCP available

If `pull_request_review_write` is in the tool list:

1. `pull_request_read` → `get_review_comments` → open threads.
2. Per thread: reply → `pull_request_review_write` → **`method: resolve_thread`**, **`threadId`** = thread **`id`** (`PRRT_…`).
3. If `id` is missing on threads, fall back to **GraphQL** (`gh api graphql`) as above.

Duplicate bot threads: resolve **each** open thread after one fix.

## Completion

- **Merge-ready:** CI green **and** `open M = 0` after resolve pass.
- **Blocked (resolve):** code/CI done but `open M > 0` — list links + say whether MCP was read-only or GraphQL failed.

One top-level PR comment ≠ per-thread resolve.

## Idempotency

Skip work already done on HEAD. No empty commits.

## PR closing summary

1. Status (merge-ready / blocked CI / blocked resolve)
2. Commits / fixes
3. Threads: resolved N, open M (+ MCP mode: readonly vs write)
4. CI
5. Left

## GitHub Copilot (`@copilot /act`)

- Reads: `pull_request_read`, `actions_*`, `get_file_contents`, `list_commits`, …
- Writes on PR reviews: only if `pull_request_review_write` appears in the MCP tool list; else GraphQL or human.
- `bunx nx format:write` on touched `tools/**/*.ts` before commit.

## GitHub (local CLI / Cursor)

Full MCP or `gh api graphql` for list + `resolveReviewThread`. UI resolve is equivalent.
