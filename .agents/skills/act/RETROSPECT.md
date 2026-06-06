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
## 2026-06-06 — PR #50 — token-efficiency retro

- **What happened:** A single `/act` on PR #50 (36 open threads, then 2 follow-up cubic comments) accumulated several token-inefficient patterns in one session: scratch `.tsv` files written to the worktree root leaked through the pre-commit hook into the PR branch (4 extra commits + 2 rebase cycles); `pr-state.sh` was re-run 3 times and `gh pr view`/`gh pr checks` were called independently 4 more times; CI was polled via 4 sequential `sleep N && gh pr view` rounds instead of one `gh pr checks --watch`; two near-duplicate test functions triggered a fresh CodeScene "Code Duplication" delta on the *new tests themselves* and forced a `test.each` reshape.
- **Root cause:** Three missing pieces of guard-rail guidance in the skill:
  1. **No rule that scratch artifacts must live outside the worktree.** `reply-threads.sh --file` accepts absolute paths; the cloud agent's pre-approved scratch dir is `/tmp/agent_*/` (see cloud-agent rules). Putting `replies.tsv` at the worktree root is wrong, full stop — no `.gitignore` entry can substitute for that.
  2. The "call `pr-state.sh` once, refresh only on state change" rule was implicit. Agents re-derive HEAD SHA / merge state / open-thread count at multiple points in the run.
  3. The new `cs delta --error-on-warnings` gate applies to the *PR diff*, not just the pre-PR file health. New tests are fair game, so the same dedup rules that drove the refactor also apply to the new tests.
- **Prevention:**
  - **Never write scratch `.tsv` (or any `/act` helper artifact) inside the worktree.** Always pass an absolute path under `/tmp/agent_*/` to `reply-threads.sh --file` and friends. Do **not** add `.tsv` patterns to `.gitignore` — that turns the bug into an ever-growing allowlist. One line in [SKILL.md Token-rationalized workflow](SKILL.md#token-rationalized-workflow): "scratch artifacts used by the `/act` helpers must live under `/tmp/agent_*/`, never in the worktree."
  - In [SKILL.md P0–P6](SKILL.md#work-order-mandatory-sequence), call out that `pr-state.sh` should be invoked **once at start** and re-invoked only after a push, a rebase, or a new commit hash; the rest of the run should treat its output as the source of truth.
  - Same place, add: under the new CodeScene delta gate, write `test.each` from the start whenever two test cases have similar shape. The pre-PR file-health retro on PR #50 ([`ef7e633`](https://github.com/abapify/openadt/commit/ef7e633)) already noted the gate; this is the second time it has re-flagged a duplicate test pair.
  - Replace the "sleep N && gh pr view" polling pattern with `gh pr checks --watch --interval 30` (or `gh run watch <run-id>`) in any example.
- **Cycle signal:** same rule re-flagged (CodeScene delta on duplicate test functions) — fixed in the same session

## 2026-06-06 — PR #50 — scratch `.tsv` files leaked into the PR via the pre-commit hook

- **What happened:** Wrote `replies.tsv` and `reply-update.tsv` at the worktree root to feed `scripts/act/reply-threads.sh`. The repo's pre-commit hook runs `nx format:write --uncommitted && git update-index --again`, which re-staged the scratch file if it had been `git restore --staged`'d but left on disk. Both files were committed and pushed to the PR branch in this session, then cleaned up in a follow-up `git rm` commit.
- **Root cause:** **Wrote scratch files in the worktree at all.** The pre-commit hook re-staging was a downstream effect. The real fix is to never put scratch artifacts where `git add` or `git restore --staged` can see them.
- **Prevention:** Use the cloud-agent pre-approved scratch dir `/tmp/agent_*/` (see cloud-agent rules) for everything the `/act` helpers need as input or produce as output. `reply-threads.sh --file /tmp/agent_*/replies.tsv` works; nothing in the worktree, nothing to leak, nothing to `.gitignore`. Do not add `.tsv` patterns to `.gitignore` — that turns the bug into an ever-growing allowlist.
- **Cycle signal:** none (caught in the same session, two extra follow-up commits to remove the file)

## 2026-06-06 — PR #50 — CodeScene "Code Duplication" delta on the new tests

- **What happened:** Added two test functions to `gui-import.test.ts` for `destinationFileUris` (relative vs absolute path). The new `cs delta --error-on-warnings` job correctly flagged them as code-duplication (the new delta gate did its job) and broke the PR. One extra round-trip to consolidate into `test.each`.
- **Root cause:** Did not anticipate the new delta gate flagging *the new tests themselves* the moment they were added. The gate is a per-PR diff check, not just a per-file check, so anything added in the PR is fair game.
- **Prevention:** When adding tests under the new CodeScene delta gate, write similar-shape assertions as a `test.each` from the start. The same anti-duplication rules that applied to the source file in this PR now apply to the test file in the same PR. Worth noting in [SKILL.md P0](SKILL.md#work-order-mandatory-sequence): if a PR enables a CodeScene delta gate, re-run `cs delta` locally (or at minimum scan the diff) before pushing review-fix tests.
- **Cycle signal:** same rule re-flagged (CodeScene delta flagged the test file the moment it appeared in the diff) — fixed in the same session

## 2026-06-06 — PR #50 — CodeScene CLI "latest" is not pinnable from public CI

- **What happened:** Tried to address cubic's "CodeScene CLI version should be pinned" thread by setting `CS_CLI_VERSION=2.4.4` in `scripts/ci-install-codescene-cli.sh`. CI install step immediately 403'd. The versioned download endpoint requires `CS_ACCESS_TOKEN` on the request (the token is exposed to the delta step, not the install step), and the guessed version string also does not exist.
- **Root cause:** Did not reproduce locally; the versioned URL only works for authenticated enterprise installs, and the available versions are not advertised. The right fix needs both a known-good version string and exposing `CS_ACCESS_TOKEN` to the install step.
- **Prevention:** When a reviewer asks to pin an externally-installed binary that's behind auth, do a `curl -fsSLI` check on the candidate URL **with and without the token header** before committing the change — and if the public path is `latest`, reply in-thread explaining the tradeoff rather than shipping a half-fix that breaks CI. The author fixed the same problem from the other direction in `d54ba37` (added a clearer fail-fast message when the PAT is rejected in the Docker image path).
- **Cycle signal:** none (caught by the next CI run on the same push; reverted in `069eb03`)

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

## 2026-06-06 — PR #50 — clearing the new `cs delta --error-on-warnings` gate

- **What happened:** PR #50 introduced `.github/workflows/codescene-delta.yml` with two jobs: an advisory `report` (uploads `codescene-delta-pr-<N>.json`) and a blocking `gate` running `cs delta origin/<base> HEAD --error-on-warnings`. The `gate` failed on `030d493` with three CodeScene-introduced findings: **Bumpy Road Ahead** + **Complex Method** in `gui-import.ts:resolveDestinationImportByRequest` (cyclomatic 10, 2 nested conditional blocks) and **Complex Conditional** in `mcp-stdio-entry.ts:validatePort` (`!Number.isInteger(port) || port < 1024 || port > 65535` — 2 operators inside one branch, threshold 2). Refactor at `7a0e0ed` cleared all three; the gate went green. One re-run later showed `License check failed: [403]` on the same SHA — transient CS_ACCESS_TOKEN 403, not a regression, fixed by `gh run rerun`.
- **Root cause:** The `cs delta` CLI rule treats boolean chains inside `if`/`for`/`while` as one "complex conditional" with N-1 branches (per [CodeScene docs](https://codescene.io/docs/guides/technical/code-health.html)). A 3-clause `||` is 2 branches — at threshold. The same applies to `&&`. The fix is to move the boolean chain out of the branch into a return expression, because the rule only counts operators inside control flow, not inside return statements.
- **Prevention (refactor patterns that worked):**
  - **Per-source helper extraction for dispatch functions.** `resolveDestinationImportByRequest` (cyclomatic 10, 2 nested blocks) became a thin `switch` over `none / adtls / gui / auto / openadt`; each `resolveXxxImport` helper has cyclomatic ≤ 4 and no nested blocks. The 5-case `switch` itself adds 5 to the dispatcher — well under the 9 threshold.
  - **Predicate extraction for boolean guards.** `validatePort`'s 3-clause `||` was replaced by a local `isValidPort` returning `A && B && C` (no `if` wrapper, so the rule doesn't count it) and a single-branch `if (!isValidPort(port))` in `validatePort`.
  - **`?? emptyImport(req.workspace)`** in `resolveAdtlsImport` collapses the `if (x) return x; if (source === "adtls") return empty` pattern to one line, removing a branch and a nested block simultaneously.
- **Workflow / tooling learnings:**
  - `cs delta` exit code is the gate signal; the JSON artifact is the evidence. Always read the JSON's `findings[].change-details[]` to see *which* function triggered the rule, not just the file — CodeScene sometimes attributes the rule to the enclosing function (e.g. `pickMcpPort` for the `validatePort` call).
  - `CodeScene delta (gate)` failures can be transient `License check failed: [403]` from `CS_ACCESS_TOKEN`. Re-run with `gh run rerun <run-id>`; if the same SHA passes on re-run, the finding is infra, not code.
  - To verify locally: `bash scripts/ci-install-codescene-cli.sh` then `cs delta origin/<base> HEAD --error-on-warnings`. Needs `CS_ACCESS_TOKEN`; the cloud token works for both [codescene.io](https://codescene.io) and the on-prem `CS_ONPREM_URL`.
  - The `cs delta` gate is the new contract for follow-up PRs that touch stdio-bridge code. Add `cs delta origin/<base> HEAD --error-on-warnings` to the SDD verify block in [DESIGN.md](../../../DESIGN.md) (currently only Sonar, Opengrep, Maven, `openadt:test`).
- **Cycle signal:** none — single push cleared the gate after the dispatch + predicate refactors.
