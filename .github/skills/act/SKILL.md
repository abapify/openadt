---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. CI, review fixes, resolve threads via
  gh CLI with OPENADT_GH_PR_TOKEN (GITHUB_TOKEN cannot resolve). Idempotent re-runs.
disable-model-invocation: true
---

# /act

**Default `/act` = all steps on:** CI → review fixes → **resolve pass** → summary. **No Playwright.**

## Token for resolve (Copilot)

`GITHUB_TOKEN` in the Copilot agent can **push commits** but usually **cannot** `resolveReviewThread` (GraphQL: insufficient permissions).

**One-time setup (repo admin):**

1. Create a **fine-grained PAT** (user account): repository **abapify/openadt**, permission **Pull requests: Read and write** (Contents: Read is enough for resolve).
2. Repo → **Settings → Secrets and variables → Agents** (not Actions) → secret **`OPENADT_GH_PR_TOKEN`** = PAT.
3. Copilot exposes Agents secrets as env vars in the agent VM.

Resolve script picks tokens in order: `OPENADT_GH_PR_TOKEN` → `GH_AW_GITHUB_TOKEN` → `GH_TOKEN` → `GITHUB_TOKEN`.

**Local / Cursor:** `export GH_TOKEN=$(gh auth token)` or use `OPENADT_GH_PR_TOKEN` the same way.

**Alternative:** enable write GitHub MCP (`pull_request_review_write`) with `COPILOT_MCP_GITHUB_PERSONAL_ACCESS_TOKEN` in MCP config (same PAT). See [extend cloud agent with MCP](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/customize-cloud-agent/extend-cloud-agent-with-mcp).

## Resolve pass (after P0–P3)

| MCP has `pull_request_review_write`? | Resolve via                                           |
| ------------------------------------ | ----------------------------------------------------- |
| **Yes**                              | MCP `resolve_thread` + `threadId`                     |
| **No** (~33 read-only tools)         | **`bash .github/skills/act/resolve-open-threads.sh`** |

```bash
bash .github/skills/act/resolve-open-threads.sh --dry-run abapify openadt 2
bash .github/skills/act/resolve-open-threads.sh abapify openadt 2
```

Script uses `OPENADT_GH_PR_TOKEN` automatically. Do **not** pass only `GITHUB_TOKEN` and expect resolve to work.

If resolve still fails: paste GraphQL error; verify PAT has **Pull requests: Write** on this repo.

## Work order

| P   | Tier                 |
| --- | -------------------- |
| 0   | CI green on HEAD     |
| 1–3 | Review / suggestions |
| —   | Resolve pass         |
| 4   | Hygiene              |

Never skip P0–P3 because resolve failed.

## Completion

- **Merge-ready:** CI green **and** `open M = 0` after resolve script or MCP resolve.
- **Blocked (resolve):** list open thread URLs + token hint if permissions error.

## GitHub Copilot (`@copilot /act`)

- Read PR/CI: MCP `pull_request_read`, `actions_*`
- Resolve: **`resolve-open-threads.sh`** (needs `OPENADT_GH_PR_TOKEN`)
- `bunx nx format:write` on touched `tools/**/*.ts`

## GitHub (local / Cursor)

`gh auth login` with repo scope, or `OPENADT_GH_PR_TOKEN`, then the same script.
