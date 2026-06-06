---
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/50
tags: [token-rationalism, scratch-files, codescene]
---

## What went wrong

A single `/act` on PR #50 accumulated several token-inefficient patterns: scratch `.tsv` files written to the worktree root leaked through the pre-commit hook into the PR branch; `pr-state.sh` was re-run 3 times; CI was polled via 4 sequential `sleep N && gh pr view` rounds; two near-duplicate test functions triggered a fresh CodeScene "Code Duplication" delta.

## Why

1. No rule that scratch artifacts must live outside the worktree.
2. The "call `pr-state.sh` once" rule was implicit.
3. The new `cs delta --error-on-warnings` gate applies to the _PR diff_, not just pre-PR file health.

## Proposed fix

- Never write scratch `.tsv` inside the worktree — use `/tmp/agent_*/`.
- Call `pr-state.sh` once at start; re-invoke only after push/rebase/new commit.
- Write `test.each` from the start whenever two test cases have similar shape.
- Replace `sleep N && gh pr view` polling with `gh pr checks --watch`.

## Scope

universal
