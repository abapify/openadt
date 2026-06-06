---
agent: devin
llm: kimi-k2.6
session: 2026-06-06-fix-mcp-launcher
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/56
tags: [git, workflow, pr-hygiene]
---

## What went wrong

Merged `origin/main` into feature branch with `git merge`, creating a merge commit and pulling 40+ commits already in main. This bloated the PR history.

## Why

Used merge instead of rebase. Did not verify which commits were already in main before merging.

## Proposed fix

Always use `git rebase origin/main` when syncing a feature branch. Reset and restart if merge was already done. Verify with `git log --oneline origin/main..HEAD` before pushing.

## Scope

universal
