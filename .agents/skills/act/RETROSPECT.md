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

## 2026-06-05 — PR #47 — CodeScene delta check fails repeatedly on inherited complexity

- **What happened:** CodeScene "Code Health Review (main)" failed on every CI run for PR #47 (initial `744c44f`, after `a1a446e`, after `2deb67a`, after `520370b`). The flagged complexity deltas are all on stdio-bridge code first introduced in #42/#43 (`mcp-stdio-entry.ts`, `stdio-proxy.ts`, `mcp-framing.ts`, `runtime-env.ts`, `main.ts cmdServe`, `lsp-client.ts createProjectAndLogon`, `gui-import.ts resolveDestinationImport`, `config.ts parseServeArgv`) — not on the fixes this PR added. CodeScene is a required check on this repo, so `mergeStateStatus` stays `UNSTABLE` and the merge button is blocked.
- **Root cause:** The PR title is "fix/dev-openadt-docs-followup" but the body landed 20+ product fixes (review feedback from a multi-bot round on the stdio bridge). Each push re-triggers the delta report against the same complex methods. There is no per-method suppression path from the agent — every CodeScene thread carries a "Suppress" link only the author can click.
- **Prevention:** On PRs that carry a CodeScene workflow, treat the delta as part of the **scope** of the PR. If the inherited complexity is out of scope, either (a) split the refactor into its own PR, or (b) ask the user (or click in the GitHub UI) to suppress the specific deltas before the next `/act`. The P6 cycle guard already escalates "same rule flagged 2+ times" — that's the right call here; do **not** declare merge-ready while `CodeScene Code Health Review (main)` is `failure` even if all other required checks are green.
- **Cycle signal:** same rule re-flagged (3 consecutive CI runs)

## 2026-06-05 — PR #42 — stale review threads from an earlier PR scope

- **What happened:** PR #42 (TS-only `tools/sap-adt-mcp-launcher/`) carried 12+ open review threads pointing at `apps/openadt-cli/src/main/java/org/openadt/cli/McpLauncherInvoker.java`, `LauncherArgs.java`, `McpServeCommand.java`, and `McpStatusCommand.java` — files that are not in the current PR diff. An earlier scope of the PR included a Java CLI shim that was force-pushed out; the auto-reviews (Codacy, Copilot, Amazon Q, Gemini, cubic) were never pruned and stuck around as ghost feedback.
- **Root cause:** Reviewers (humans + bots) anchor a thread to a `path:line` at review time, but the file can move out of the PR between force-pushes; the PR UI still shows the thread as "open". `pr-state.sh` reports the count, not the staleness.
- **Prevention:** On every `/act`, the first thing to do after `pr-state.sh` is `git diff main..HEAD --stat` to confirm the files the threads reference are actually in the current PR. Threads whose path is outside the current diff are resolved as **stale** with an in-thread reply that names the file and explains the scope shrink. This is the only safe default; resolving without the in-thread reply is the wrong "resolve-only" path the SKILL.md calls out.
- **Cycle signal:** none

## 2026-06-05 — PR #47 — CodeScene "Pay Down Tech Debt" gate cannot be cleared by an in-PR refactor alone

- **What happened:** Three pushes on the same branch (`4e172aa` → `a2ab589` → `8be8e7b` → `815a872`) refactored every method CodeScene flagged on PR #47's HEAD. The check still fails on the third push. Trend:
  - **Before:** 9 files flagged, **2 critical** (Deep, Nested Complexity in `mcp-framing.ts McpNdjsonDecoder.drain` and `stdio-proxy.ts parseMcpHttpResponseBody`), 7 advisory.
  - **After 3 pushes:** 5 files flagged, **0 critical**, 5 advisory (all file-level `Primitive Obsession` / `Code Duplication` / `Complex Conditional`). `config.ts` health 7.84 → 9.13, `main.ts` 8.62 → 9.31. `lsp-client.ts` cleared the report entirely. The delta gate still requires 10.0 ("new code is healthy") on every changed file, and the absolute file-level complexity of the stdio bridge keeps `stdio-proxy.ts` (9.10), `mcp-framing.ts` (9.39), `runtime-env.ts` (9.39), `mcp-stdio-entry.ts` (9.69), and `gui-import.ts` (7.45) below the threshold.
- **Root cause:** CodeScene's "Pay Down Tech Debt" profile measures absolute file-level code health against the *previous commit on the same branch's base*, not against a per-method delta. When a follow-up PR touches a file that was already below 10.0, the file's health is reported on every push and the gate is red unless the entire file is rewritten to 10.0. Inheriting pre-existing complexity (stdio-bridge code first added in #42/#43) into a `fix/dev-openadt-docs-followup` branch means the PR cannot merge without either (a) splitting the refactor into its own PR whose base already has 10.0, or (b) clicking the per-method "Suppress" links in the GitHub UI (or asking the user to do so).
- **Prevention:**
  - On a follow-up PR whose scope is docs/chore, **do not** include product code refactors that touch files with inherited low health. The follow-up should be docs-only or split the refactor into a separate PR whose base is `main` (so the delta is the refactor alone, not refactor-on-top-of-complex-stdio-bridge).
  - When the inherited complexity must be paid down, do it as its own PR and aim for a clean 10.0 on every changed file in one push, not three. The "improvements" CodeScene credits are a useful proxy: if `config.ts` and `main.ts` jump to 9.13 / 9.31 after one refactor, the threshold is reachable with focus.
  - Stop after 3 pushes per `/act` cycle (matches the task's hard cap) and report back. The 3-push budget burns ~3 × ~9 min of CI plus reformat churn; further pushes on the same branch will not move the file-level health below the "new code is healthy" gate.
  - Add a follow-up to the SKILL work-order: "If a follow-up PR's CodeScene delta fails on `stdio-proxy.ts` / `mcp-framing.ts` / `runtime-env.ts` for `Primitive Obsession` / `Code Duplication` / `Complex Conditional`, treat the PR as scope-drift and recommend a split."
- **Cycle signal:** same rule re-flagged 3+ consecutive pushes (8 methods, then 6, then 5) — the rule is now structural, not actionable in a follow-up PR.
