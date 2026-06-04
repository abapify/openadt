# /act retrospective log

Append-only durable learnings from `/act` P6 evaluation. One entry per session when something went wrong or almost went wrong.

**Format** (copy for each entry):

```markdown
## YYYY-MM-DD — PR #N — <one-line theme>

- **What happened:**
- **Root cause:**
- **Prevention:** (which file was updated: SKILL / REVIEW.md / codacy review.md)
- **Cycle signal:** none | reopened thread | same rule re-flagged | repeated /act without new fixes
```

---

## 2026-05-24 — PR #12 — premature merge before /act complete

- **What happened:** PR merged while review threads still needed code fixes; later `/act` runs chased already-merged work.
- **Root cause:** Merge-ready declared after resolve-only or before P0–P3 finished on all threads.
- **Prevention:** P6 cycle guard in [SKILL.md](SKILL.md) — do not merge until P4 **and** P6 pass; reopened threads block merge.
- **Cycle signal:** repeated /act without new fixes

## 2026-05-24 — PR #12 — PR review triage — Codacy vs GitHub APIs

- **What happened:** Agent queried GitHub Code Scanning / invented counts instead of Codacy or PR review threads; claimed “7 issues fixed” without matching API evidence.
- **Root cause:** Codacy, Code Scanning, Code Quality (Copilot review), and Dependabot treated as one bucket.
- **Prevention:** [REVIEW.md](../../../REVIEW.md) tool table; P6 requires naming the source before claiming fix counts.
- **Cycle signal:** none

## 2026-05-24 — PR #12 — Semgrep suppressions — whole-file exclusion rejected

- **What happened:** Agent added file-level semgrep exclusions for intentional loopback SSRF patterns.
- **Root cause:** Did not read [`.codacy/instructions/review.md`](../../../.codacy/instructions/review.md) — repo policy is line-specific `// nosemgrep: <rule-id>` only.
- **Prevention:** Domain false positives live in `.codacy/instructions/review.md`; do not edit `.semgrep.yml` to exclude whole production files.
- **Cycle signal:** same rule re-flagged

## 2026-06-04 — PR #38 — token-rationalism in /act

- **What happened:** A single `/act` on PR #38 (9 open threads, all docs) used ~30 tool calls and ~2.9k recoverable tokens: 6 separate `gh pr view` / `gh pr checks` calls, 2 failed `gh api graphql` attempts (`--input` + `-F` collision), 9 individual reply mutations, 9 individual resolve mutations, plus 3 Java-source greps just to confirm `openadt auth login` exists in the CLI.
- **Root cause:** No shared helpers; agents re-derive PR state, CLI surface, and thread plumbing from scratch every run.
- **Prevention:**
  - [`scripts/act/pr-state.sh`](../../../scripts/act/pr-state.sh) — one call: HEAD SHA, mergeability, open threads table, required CI pending count.
  - [`scripts/act/reply-threads.sh`](../../../scripts/act/reply-threads.sh) — batch N replies into one aliased GraphQL mutation from a TSV file.
  - [`scripts/derive-cli-surface.ts`](../../../scripts/derive-cli-surface.ts) — one-shot CLI surface index from `specs/cli.md` (`--check "openadt auth login"`).
  - [SKILL.md Token-rationalized workflow section](SKILL.md#token-rationalized-workflow) — points at the helpers and documents the `gh api graphql` `-F` gotcha.
- **Cycle signal:** none

## 2026-06-04 — PR #40 — Codacy "N new issues" with no annotations

- **What happened:** First `/act` on PR #40 left the pipeline red: `Codacy Static Code Analysis` was `action_required` with output `3 new issues (0 max.) of at least severity.` and **zero** code annotations. The cloud app's UI requires JS and the API needs `CODACY_API_TOKEN`, so the issues were not visible from the agent.
- **Root cause:** Did not reproduce the linter locally. Codacy runs ShellCheck on the new `scripts/act/*.sh` files; running `shellcheck` locally found exactly the 3 reported issues (1× SC2034 unused variable, 2× SC2015+SC2016 inline GraphQL-fragment construction). The fix was a one-round-trip: `apt-get install -y shellcheck && shellcheck scripts/act/*.sh`, then refactor the query builder.
- **Prevention:** [SKILL.md P0 — when CI is red, run linters locally first](SKILL.md#work-order-mandatory-sequence) now includes a "Codacy N new issues (0 max.) with annotations=0" → "install linter, run it, fix" table. Same pattern for Opengrep (`opengrep --config .semgrep.yaml`), SonarCloud, CodeQL.
- **Cycle signal:** none
