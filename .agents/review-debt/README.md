# Review debt ledger

Queryable queue of **unresolved PR review threads** harvested **after merge** (or manual backfill). Used by `/act debt` to batch-fix technical review debt without blocking releases.

**Not GitHub issues** — this file is agent-first, supports duplicates/heat maps, and links back to `reviewThread` ids.

## Files

| File | Purpose |
| ---- | ------- |
| `debt.jsonl` | One JSON object per line; canonical row per `thread_id` |
| `debt-summary.json` | Generated hotspots (`by_area`, `by_author`, duplicate fingerprints) |
| `config.json` | `ignore_authors` / `nit_authors` classification |

## When harvest runs

| Trigger | Scope |
| ------- | ----- |
| `pull_request` **closed** + merged | That PR only |
| `workflow_dispatch` | Filtered batch (`pr_numbers`, dates, …) |

Ledger commits **rebase onto latest `main`** before push (avoids non-fast-forward when `main` moves during the job).

Harvest does **not** run on every `/act`, every CI run, or every push to `main`.

Only threads that are **`isResolved == false` and `isOutdated == false`** are harvested.

## Scripts

**Preferred entry points** (owner/repo default from `gh repo view` in a clone):

```bash
bun run act:debt:harvest-pr -- 72 --dry-run
bun run act:debt:harvest -- --pr-ids 72,67
bun run act:debt:harvest -- --merged-since 2026-06-09 --last 5
bun run act:debt:query -- --status open --limit 25 --format tsv
bun run act:debt:plan -- --limit 25 --out /tmp/agent_$$/debt-batch-plan.md
bun run act:debt:done -- --status done --fix-pr 99 --thread-id PRRT_…
bun run act:debt:test
```

Nx equivalents: `bunx nx run act-skill:act-debt-query -- --status open`, etc.

Ledger scripts live in [`.agents/skills/act/scripts/`](../skills/act/scripts/) (portable skill). Explicit `OWNER REPO` when not in a gh-authenticated clone:

```bash
bun .agents/skills/act/scripts/harvest-threads.ts OWNER REPO PR --merged-sha SHA --run-id local
bun .agents/skills/act/scripts/harvest-debt-batch.ts OWNER REPO --pr-ids 72,67
bun run act:debt:query -- --write-summary
bun run act:debt:query -- --duplicates
bun run act:debt:query -- --area apps/openadt-cli
```

## Agent workflow (`/act debt`)

See [.agents/skills/act/SKILL.md](../skills/act/SKILL.md) § Debt mode.

1. `query-debt.ts --status open --limit N --format tsv`
2. Fix in product code on branch `cursor/review-debt-YYYY-MM-DD-f7a9`
3. Open batch PR; link source PR numbers + thread ids
4. After merge: `update-debt-status.ts --status done --fix-pr N --threads-file …`
5. Optional: `reply-threads.sh` + `resolve-open-threads.sh` on source PRs

## Row schema

```json
{
  "thread_id": "PRRT_…",
  "thread_url": "https://github.com/…/pull/N#…",
  "status": "open",
  "priority": "nit",
  "needs": "code_change",
  "source_pr": 81,
  "fingerprint": "sha256:…",
  "area": "apps/openadt-cli",
  "times_seen": 1
}
```

Statuses: `open` | `claimed` | `done` | `wontfix` | `duplicate`

## Plan

Full design: [docs/plans/2026-06-09-review-debt-harvest.md](../../docs/plans/2026-06-09-review-debt-harvest.md)
