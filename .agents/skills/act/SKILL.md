---
name: act
description: >-
  Use when the user invokes /act on a PR/MR (Cursor, Codex, Copilot, or local gh).
  CI, review fixes, resolve open threads via gh CLI/script. Idempotent re-runs.
disable-model-invocation: true
---

# /act

**Default `/act` = all steps on:** CI → review fixes → **resolve pass** → summary.

Applies to **`/act`**, **`@codex /act`**, **`@copilot /act`**, and local agent sessions — unless the user narrows scope in the same message.

**No Playwright** for GitHub PR UI.

## Resolve pass (all runtimes)

After P0–P3, close **every** open review thread (current + outdated; duplicate bot threads each get resolved after one fix).

**Prerequisite:** `gh` installed and authenticated (`gh auth status`).

```bash
bash .agents/skills/act/resolve-open-threads.sh --dry-run OWNER REPO NUMBER
bash .agents/skills/act/resolve-open-threads.sh OWNER REPO NUMBER
```

**If MCP exposes `pull_request_review_write`:** may use `resolve_thread` when thread `id` is available; otherwise use the script.

Never skip P0–P3 because resolve failed. One top-level PR comment ≠ per-thread resolve.

## Work order

| P | Tier |
|---|------|
| 0 | CI green on HEAD |
| 1–3 | Review / suggestions (fix + in-thread reply when possible) |
| — | Resolve pass |
| 4 | Hygiene |

## Completion

- **Merge-ready:** CI green **and** `open M = 0` (verify with script `--dry-run`).
- **Blocked (resolve):** list open thread URLs + attach `gh`/GraphQL error output.

## PR closing summary

1. Status (merge-ready / blocked CI / blocked resolve)
2. Commits / fixes
3. Threads: resolved N, open M
4. CI
5. Left

## Idempotency

Skip work already on HEAD. No empty commits.

## Validation

- `bunx nx format:write` on touched `tools/**/*.ts` before commit (CI runs `nx format:check`).

## Runtime extras

- **Copilot SWE:** [`.github/copilot-instructions.md`](../../../.github/copilot-instructions.md) (read-only MCP, firewall) — not for Codex/Cursor.
- **Codex on GitHub:** [AGENTS.md](../../../AGENTS.md) § Codex.
