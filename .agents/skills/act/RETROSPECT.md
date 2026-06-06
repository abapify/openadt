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
