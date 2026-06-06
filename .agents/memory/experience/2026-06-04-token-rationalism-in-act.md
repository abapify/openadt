---
date: 2026-06-04
context: https://github.com/abapify/openadt/pull/38
tags: [token-rationalism, act, workflow]
---

## What went wrong

A single `/act` on PR #38 (9 open threads, all docs) used ~30 tool calls and ~2.9k recoverable tokens: 6 separate `gh pr view` / `gh pr checks` calls, 2 failed `gh api graphql` attempts (`--input` + `-F` collision), 9 individual reply mutations, 9 individual resolve mutations, plus 3 Java-source greps just to confirm `openadt auth login` exists in the CLI.

## Why

No shared helpers; agents re-derive PR state, CLI surface, and thread plumbing from scratch every run.

## Proposed fix

- `scripts/act/pr-state.sh` — one call: HEAD SHA, mergeability, open threads table, required CI pending count.
- `scripts/act/reply-threads.sh` — batch N replies into one aliased GraphQL mutation from a TSV file.
- `scripts/derive-cli-surface.ts` — one-shot CLI surface index from `specs/cli.md` (`--check "openadt auth login"`).

## Scope

universal
