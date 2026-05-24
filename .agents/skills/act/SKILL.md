---
name: act
description: >-
  Use when the user invokes /act on a PR/MR. Primary job: fix CI and implement
  review feedback in product code. Resolve threads only after each item is fixed
  or answered. Never resolve-only.
disable-model-invocation: true
---

# /act

**`/act` means fix the PR, not hide review comments.**

The main work is **P0–P3**: read each review thread, change **product code** (or post a substantive in-thread reply), commit, then close threads.  
Running the resolve script **without** doing that first is **wrong** — same as clicking “Resolve conversation” on every thread with no code changes.

Applies to `/act`, `@claude /act`, `@codex /act`, `@copilot /act`.

**No Playwright** for GitHub PR UI.

## Wrong vs right

| Wrong (do not do this) | Right |
|------------------------|--------|
| Run `resolve-open-threads.sh` to clear open threads | Read threads → fix code → reply in thread → then resolve |
| One PR comment “addressed feedback” | Per-thread fix or per-thread reply, then resolve that thread |
| Only touch `.agents/skills/` or the resolve script | Change `apps/`, `tools/`, `specs/`, `packaging/`, workflows per feedback |
| “Merge-ready” because `open_threads=0` | Merge-ready only if feedback is **implemented** and CI green on HEAD |
| Edit PR title/body to track agent progress | Leave author PR summary alone; reply in threads + commits |

## PR metadata

**Never change pull request title or description** unless the user explicitly asks.

Do not replace the author’s summary with checklists, thread counts, or CI notes. On GitHub Copilot, repository rules live in [`.github/copilot-instructions.md`](../../../.github/copilot-instructions.md) and [`.github/instructions/act.instructions.md`](../../../.github/instructions/act.instructions.md).

## On start

1. React 👀 (or 👍).
2. **HEAD SHA** — `gh pr view NUMBER --json headRefOid,statusCheckRollup,url`.
3. **Inventory open threads** — for each unresolved thread, capture: file/line, reviewer ask, whether it needs a **code change** or a **written answer**.

Build a short **thread plan** before editing (can be in your working notes / final summary):

```text
Thread 1 (path: …): [fix code | reply only] — what you will do
Thread 2 …
```

Do not start the resolve script until every open thread has a planned action and you have executed P0–P3.

## Work order (mandatory sequence)

| Step | What | Done when |
|------|------|-----------|
| **P0** | CI / merge blockers on **HEAD** | Required checks green on **current** HEAD |
| **P1** | Blocking review (“must fix”, changes requested) | **Code fixed** on branch + **reply in that thread** |
| **P2** | Nits, questions, style | **Fix or answer in thread** (not silent) |
| **P3** | Inline suggestions | **Applied in code** or declined with reason **in thread** |
| **P4** | Resolve pass | Only after P0–P3 for **all** open threads |
| **P5** | Hygiene | Optional cleanup after the above |
| **P6** | Evaluation | Retrospect, update durable knowledge, cycle check — **before** merge-ready |

**Resolve is step P4, not step 1.**  
**P6 is mandatory before merge-ready** when anything went wrong during the session (see [EVALUATE.md](EVALUATE.md)).  
If you cannot fix something in-repo, say so **in that thread**; do not resolve it without a visible reply.

## Per-thread loop (repeat for each open thread)

1. **Read** the full thread (all comments).
2. **Act on substance:**
   - Bug / design / correctness → edit product files, run relevant checks.
   - Question → answer in the thread with specifics.
   - Suggestion → apply diff or explain why not.
3. **Commit** product changes (group sensibly; no empty commits).
4. **Reply in the thread** pointing to the commit or your decision (short, factual).
5. **Then** mark that thread resolved (see P4).

Skipping steps 2–4 and only running the batch resolve script **violates `/act`**.

## What to change

**In scope:** `apps/`, `tools/`, `specs/`, `packaging/`, `.github/workflows/`, etc.

**Out of scope for “addressing review”:** `.agents/skills/`, `resolve-open-threads.sh` — unless the script literally cannot run (`bash -n` fails).

## Resolve pass (P4 only)

**Prerequisites (all must be true):**

- Every open thread has a **commit** and/or **in-thread reply** for its feedback.
- `gh auth status` succeeds.

```bash
bash -n .agents/skills/act/resolve-open-threads.sh
bash .agents/skills/act/resolve-open-threads.sh --dry-run OWNER REPO NUMBER
bash .agents/skills/act/resolve-open-threads.sh OWNER REPO NUMBER
bash .agents/skills/act/resolve-open-threads.sh --dry-run OWNER REPO NUMBER
```

The script only clicks “Resolve conversation” in GitHub — it does **not** implement review fixes.  
Resolve outdated threads too, but only after the underlying comment was handled on the branch.

## Evaluation (P6 — after P4, before merge-ready)

Follow [EVALUATE.md](EVALUATE.md). Summary:

1. **Retrospective** — if anything went wrong (wrong API, resolve-only, bad suppressions, premature merge pressure): what / root cause / prevention in closing summary; append to [RETROSPECT.md](RETROSPECT.md) when the pattern may recur.
2. **One sink per finding** — route durable updates to a **single** primary file:

   | Finding type | Primary sink |
   |--------------|--------------|
   | `/act` workflow (resolve-only, merge too early) | This skill + optional RETROSPECT.md |
   | API/tool confusion (Codacy vs GitHub) | [`.github/instructions/review.instructions.md`](../../../.github/instructions/review.instructions.md) |
   | Codacy/domain false positive | [`.codacy/instructions/review.md`](../../../.codacy/instructions/review.md) |

3. **Cycle guard** — if any signal fires, **do not merge**; escalate to the user with evidence:

   - A review thread was **reopened** after an earlier resolve on this PR.
   - The **same rule/alert** (Codacy, Semgrep, Code Scanning) was flagged **2+ times** after a fix commit — verify fix on current HEAD before another merge attempt.
   - **2+ `/act` runs** on the same PR with **no new product commits** since the last run — report an `/act` cycle; do not resolve-only again.

4. **Fix counts** — never claim “N issues fixed” without naming the **source system** and showing it was queried on **current HEAD** (see review.instructions.md).

## Merge-ready

Say **merge-ready** only when:

1. Review feedback is **done in code** (or explicitly declined in threads with reason).
2. CI required checks **success on current HEAD**.
3. `open_threads=0` from final `--dry-run`.
4. Summary lists **what you changed per theme/file**, not only “resolved N threads”.
5. **P6 passed** — no cycle signals (reopened threads, duplicate rule flags, empty `/act` loop); retrospective + sink update done if anything went wrong this session.

## PR closing summary

1. Status  
2. **HEAD** SHA  
3. **Review fixes** (bullet per theme / file — this is the main section)  
4. Threads: how many resolved **after** fixes; `open_threads=0`  
5. CI on HEAD  
6. **P6:** cycle signals (none / blocked — list)  
7. Left  

## Memory reminder template

When P6 finds a lesson the **user** should carry across agents (personal tooling prefs, org-specific review sources), suggest they add to Cursor user rules or Copilot instructions — **do not commit secrets or real landscape data**:

```markdown
## OpenADT /act reminders
- On abapify/openadt PRs: triage Codacy via [Codacy UI/MCP], not `gh api …/code-scanning`.
- `/act` = code fix + per-thread reply before resolve; never merge until P6 cycle check passes.
- Semgrep false positives: line-specific `// nosemgrep: <rule-id>` only — see `.codacy/instructions/review.md`.
```

## Idempotency

If feedback is already fixed on HEAD and threads are closed → short “already done”, no resolve-only rerun.

## Validation

`bunx nx format:write` on touched `tools/**/*.ts` before commit.

## Runtime extras

- **Copilot SWE:** [`.github/copilot-instructions.md`](../../../.github/copilot-instructions.md)
- **Codex / Claude:** [AGENTS.md](../../../AGENTS.md) § Cloud agents on GitHub
