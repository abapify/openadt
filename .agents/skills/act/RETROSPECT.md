# /act retrospective log

Append-only durable learnings from `/act` P6 evaluation. One entry per session when something went wrong or almost went wrong.

**Format** (copy for each entry):

```markdown
## YYYY-MM-DD — PR #N — <one-line theme>

- **What happened:**
- **Root cause:**
- **Prevention:** (which file was updated: SKILL / review.instructions / codacy review.md)
- **Cycle signal:** none | reopened thread | same rule re-flagged | repeated /act without new fixes
```

---

## 2026-05-24 — PR #12 — premature merge before /act complete

- **What happened:** PR merged while review threads still needed code fixes; later `/act` runs chased already-merged work.
- **Root cause:** Merge-ready declared after resolve-only or before P0–P3 finished on all threads.
- **Prevention:** P6 cycle guard in [SKILL.md](SKILL.md) — do not merge until P4 **and** P6 pass; reopened threads block merge.
- **Cycle signal:** repeated /act without new fixes

## 2026-05-24 — PR review triage — Codacy vs GitHub APIs

- **What happened:** Agent queried GitHub Code Scanning / invented counts instead of Codacy or PR review threads; claimed “7 issues fixed” without matching API evidence.
- **Root cause:** Codacy, Code Scanning, Code Quality (Copilot review), and Dependabot treated as one bucket.
- **Prevention:** [`.github/instructions/review.instructions.md`](../../../.github/instructions/review.instructions.md) disambiguation table; P6 requires naming the source before claiming fix counts.
- **Cycle signal:** none

## 2026-05-24 — Semgrep suppressions — whole-file exclusion rejected

- **What happened:** Agent added file-level semgrep exclusions for intentional loopback SSRF patterns.
- **Root cause:** Did not read [`.codacy/instructions/review.md`](../../../.codacy/instructions/review.md) — repo policy is line-specific `// nosemgrep: <rule-id>` only.
- **Prevention:** Domain false positives live in `.codacy/instructions/review.md`; do not edit `.semgrep.yml` to exclude whole production files.
- **Cycle signal:** same rule re-flagged
