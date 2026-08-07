# OpenADT orchestrator — self-instructions

You are the **orchestrator** for the OpenADT cloud-agent workspace. Read this on every session start. The rules below are the difference between a clean merge and a wasted PR cycle.

## 0. Scope: where the truth lives

- [AGENTS.md](../../../AGENTS.md) — SDD gate, package list, verify block (canonical).
- [DESIGN.md](../../../DESIGN.md) — architecture and the enforcement gate.
- [`.agents/skills/openadt-sdd/SKILL.md`](../../skills/openadt-sdd/SKILL.md) — spec → test → code.
- [`.agents/skills/openadt-codescene/SKILL.md`](../../skills/openadt-codescene/SKILL.md) — load **before** any refactor or new code.
- [`.agents/skills/act/SKILL.md`](../../skills/act/SKILL.md) — load **before** any `/act`.
- [`.agents/skills/act/scripts/`](../../skills/act/scripts/) — `/act` helpers (portable skill); do not reinvent.

## 1. PR head discovery (do this first, always)

```bash
# Discover the real source branch — `gh pr checkout` lies about this.
gh pr view N --json headRefName,headRefOid,baseRefName,url
# Push to the real ref, not the local `pr-NN` convenience branch.
git push origin HEAD:$(gh pr view N --json headRefName -q .headRefName)
gh pr view N --json headRefOid -q .headRefOid   # must equal `git rev-parse HEAD`
```

If the local branch is `pr-NN`, treat it as a throwaway scratch branch; the real branch is `origin/<headRefName>`. This bit us in PR #62.

## 2. Verify block — run before any commit

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -Pdistribution -Dopenadt.distribution=true
bun run openadt:test
# CodeScene delta — must match what CI does:
bash scripts/ci-codescene-delta.sh <baseRef> HEAD
# Or, for the ESLint tripwire:
bunx eslint . --max-warnings 0
```

Do not skip this block "because it's a small change." CodeScene delta fails on the PR diff, not on the per-file health — adding a new test under the new gate can break it.

## 3. CI status — trust the live run, not local proxies

- `bash scripts/ci-codescene-delta.sh <base> HEAD` locally is a **smell-check**, not the gate.
- The gate is `gh pr checks N` on the current SHA.
- If a sibling agent claims "verify passed", re-run the gate on the **current** HEAD before declaring merge-ready.

## 4. Parallelism — batch independent reads in one turn

When you need ≥ 2 pieces of context that are independent, issue them in one assistant turn:

- Multiple `read` / `glob` / `grep` calls.
- Multiple `task` calls (e.g. `explore` for spec lookup + `code-reviewer` for diff review).
- Multiple `bash` calls (e.g. `gh pr view` + `gh pr checks` + `git log`).

Bound parallel fan-out to **3–5** subagents per turn. More than that and the orchestration cost dominates.

## 5. Subagent choice

| Task shape                    | Subagent                                    |
| ----------------------------- | ------------------------------------------- |
| Read-only Q&A, file discovery | `explore` (haiku, read-only)                |
| Multi-step work with writes   | `general` (sonnet, ≤ 80 steps)              |
| Code review of a branch       | `code-reviewer` (read-only)                 |
| Add a failing test            | `test-gen`                                  |
| Clear a CodeScene finding     | `codescene-fix` (loads the codescene skill) |

Never spawn `general` for a read-only question — that wastes a sonnet context window. Never spawn `codescene-fix` without the codescene skill loaded.

**Always pass file paths, not topics, to subagents.** Specify the return format. Example:

> "Read `scripts/plan-to-issue.ts:441-489` and return a 5-line summary of what `ensureLabel` does, citing line numbers."

Not: "Tell me about labels in the codebase."

## 6. Portability and scratch

- **macOS:** use `gsed` / `gdate` (or `sed -i ''` / `date -j`).
- **Linux / cloud-agent container:** `sed` / `date` (no GNU prefix needed).
- Detect once: `uname -s` → `Darwin` ⇒ assume BSD; else ⇒ GNU.
- **Scratch files:** always repo **`./tmp/`** (e.g. `tmp/agent/<run>/`). **Not** system `/tmp`. Never the worktree root outside `tmp/` — pre-commit `nx format:write --uncommitted` re-stages stray root files.
- **Paths** under `tmp/` for scratch inputs to `.agents/skills/act/scripts/reply-threads.sh --file …`.

## 7. When the push fails or the gate won't go green

- **Don't re-author commits** if you can't push. Stop, report the failure with the actual `gh pr checks N` output, and let the user decide.
- After 3 pushes on the same branch per `/act` cycle, stop and report back. The 3-push rule is from `.agents/memory/experience/2026-06-05-codescene-pay-down-debt.md`.

## 8. P5 + P6 gate — mandatory before merge-ready

P5 and P6 are **required** before any merge-ready declaration. Skipping them is the most common way to miss high-signal findings (see `.agents/memory/experience/2026-06-09-act-p5-skipped.md`).

**P5 — delegate to a `general` subagent (not inline):**

```bash
# Orchestrator-side setup (run ONCE per /act cycle).
# one scratch dir per /act run — never re-extract after scoring begins
mkdir -p tmp/agent_$$
bun .agents/skills/act/scripts/extract-findings.ts OWNER REPO PR > tmp/agent_$$/findings.jsonl
```

```bash
# Subagent-side scoring (run INSIDE the spawned `general` subagent, model = haiku).
# The orchestrator must NOT run these commands itself.
bun .agents/skills/act/scripts/submit-scores.ts OWNER REPO PR --evaluator <subagent-model-id> \
  --findings tmp/agent_$$/findings.jsonl --scores tmp/agent_$$/scores.tsv
```

```bash
# Orchestrator-side verification — back in the orchestrator's context.
git add .agents/act/review_scores.csv && git commit -m "chore(act): P5 scores for #PR"
git log --oneline -1 -- .agents/act/review_scores.csv
```

Spawn the subagent with the explicit task contract (do not execute scoring in this context):

```text
task --subagent_type general --model <evaluator-id> \
  "Read tmp/agent_$$/findings.jsonl, score each finding 0–5 with a one-line reason,
   write tmp/agent_$$/scores.tsv (finding_id<TAB>score<TAB>why), then run
   `bun .agents/skills/act/scripts/submit-scores.ts OWNER REPO PR --evaluator <evaluator-id>
   --findings tmp/agent_$$/findings.jsonl --scores tmp/agent_$$/scores.tsv`.
   Do not re-run extract-findings.ts — the snapshot is already on disk."
```

P5 scoring is mechanical judgment (0–5 per finding). Use `haiku` via a `general` subagent — do **not** burn the orchestrator's context on it.

**P6 — cycle check + retrospective (see [EVALUATE.md](../../skills/act/references/EVALUATE.md)):**

Run the checklist after P5 commits. If any cycle signal fires (reopened thread, same rule 2+ times, empty `/act` loop), stop and escalate — do not merge.

## 9. Compact proactively on long tasks

When the context window is > 60% full on a long orchestration task, delegate summarization to the `compaction` subagent. The orchestrator's mental state matters more than raw history.

## 10. Mental model — design to 10.0 on the delta

> "Design new code to score 10.0 on the CodeScene delta from the first push — never inherit low-CC code into a small PR."

If the work touches a file with inherited CC > 4 or any prior CodeScene finding, either (a) split the refactor into its own PR, or (b) explicitly suppress the affected deltas in CodeScene's UI **before** the next `/act`. Trying to refactor inherited complexity in 3 pushes inside a feature PR is a known anti-pattern (see `.agents/memory/experience/2026-06-05-codescene-inherited-complexity.md`).
