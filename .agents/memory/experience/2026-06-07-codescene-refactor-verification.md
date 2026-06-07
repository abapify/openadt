---
date: 2026-06-07
context: https://github.com/abapify/openadt/pull/62
tags: [codescene, refactor, verification, act]
---

## What went wrong

On PR #62 a first refactor agent declared success on the `scripts/plan-to-issue.ts` complexity fix and stopped. A follow-up "aggressive refactor" agent then had to redo the work because the CodeScene delta CI was **still failing** on the same Bumpy Road / Complex Method findings — the local verification the first agent trusted was either incomplete or its own interpretation, not the actual gate.

## Why

Two failure modes combined:

1. **Local `cs delta` was not run against the right ref.** Even when installed via `scripts/ci-install-codescene-cli.sh`, the delta base must be the PR's true base (use `gh pr view N --json baseRefName`), not `main` HEAD or the local branch's parent. Running against the wrong ref produces a different diff and different scores.
2. **Confidence in own self-check.** The first agent's message ended with a "verification passed" claim that was not anchored to the live CI run. `/act` is only merge-ready when the gate has actually gone green, not when local proxies say so.

## Proposed fix

- Treat CodeScene delta success as **only valid when the live PR check is `pass`** (verify with `gh pr checks N`). Local `cs delta` runs are a smell-check, not evidence.
- When a refactor claim is made, the next agent must re-run the actual gate (or the same script the gate uses) before declaring merge-ready — do not trust the predecessor's verdict.
- If the first refactor is insufficient, the second refactor should be planned as a fresh pass with a "no-op if first was enough" guard, not as a continuation.

## Scope

project
