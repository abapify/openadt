---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. CI, review fixes, then resolve open
  threads. Copilot: read-only MCP + gh api graphql (or resolve-open-threads.sh).
  Idempotent re-runs.
disable-model-invocation: true
---

# /act

**Default `/act` = all steps on:** CI → review fixes → **resolve pass (gh CLI)** → summary.

Never skip code fixes because resolve is hard. **No Playwright.**

## Resolve pass (required every run)

After P0–P3, close review threads. **Prefer GitHub CLI** — it returns `PRRT_…` ids that read-only MCP omits.

### 1. Check MCP tool list (verbose logs)

| MCP has `pull_request_review_write`? | Resolve via |
|--------------------------------------|-------------|
| **Yes** | MCP `pull_request_review_write` → `method: resolve_thread` + `threadId` |
| **No** (~33 read-only tools) | **`gh api graphql`** or repo script (below) |

### 2. Read-only MCP — use gh CLI (primary)

`GITHUB_TOKEN` is in the agent environment. **Always attempt** before claiming resolve blocked:

```bash
# List open threads (note PRRT_ ids — MCP get_review_comments often omits these)
GH_TOKEN="$GITHUB_TOKEN" gh api graphql -f query='query($o:String!,$r:String!,$pr:Int!){
  repository(owner:$o,name:$r){pullRequest(number:$pr){
    reviewThreads(first:100){nodes{id isResolved isOutdated path}}
}}' -f o=OWNER -f r=REPO -F pr=NUMBER

# Resolve one thread
GH_TOKEN="$GITHUB_TOKEN" gh api graphql -f query='mutation($id:ID!){
  resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}' -f id=PRRT_kwDO…
```

**Batch resolve all open threads** (preferred in Copilot after fixes are on branch):

```bash
bash .github/skills/act/resolve-open-threads.sh --dry-run OWNER REPO NUMBER   # inspect
bash .github/skills/act/resolve-open-threads.sh OWNER REPO NUMBER             # resolve all open
```

- **Do not** use `gh pr view --json` (GraphQL via different path; use `gh api graphql` or the script).
- **Do not** skip this step** because MCP lacks write tools — that is exactly when gh CLI is required.
- If `gh api graphql` returns **403** / DNS proxy block: report **blocked (resolve)**, list thread URLs, suggest repo firewall allowlist for `api.github.com` or enable write MCP.

### 3. Write MCP available

Use MCP `resolve_thread` when `pull_request_review_write` is listed; if thread `id` missing in MCP response, fall back to **§2 gh CLI**.

Resolve **every** open duplicate bot thread (same path, different bots).

## Work order

| P | Tier |
|---|------|
| 0 | CI green on HEAD |
| 1–3 | Review / suggestions (fix + reply in thread when possible) |
| — | **Resolve pass (gh or MCP)** |
| 4 | Hygiene |

## Completion

- **Merge-ready:** CI green **and** `open M = 0` (verify with script `--dry-run` or graphql list).
- **Blocked (resolve):** only after **attempted** `resolve-open-threads.sh` or manual `gh api graphql` with error output attached.

One PR comment ≠ per-thread resolve.

## Idempotency

Skip fixes/commits already on HEAD. Re-running resolve on resolved threads is a no-op (script skips `isResolved: true`).

## PR closing summary

1. Status
2. Commits / fixes
3. Threads: `resolved N`, `open M` (include gh command or script output)
4. CI
5. Left

## GitHub Copilot (`@copilot /act`)

- **Read PR/CI:** MCP `pull_request_read`, `actions_*`, …
- **Resolve:** **`gh api graphql`** or `bash .github/skills/act/resolve-open-threads.sh …` — mandatory when MCP is read-only
- `bunx nx format:write` on touched `tools/**/*.ts` before commit

## GitHub (local / Cursor)

Same resolve script or `gh api graphql`. `gh pr checks`, `gh run view` for CI.
