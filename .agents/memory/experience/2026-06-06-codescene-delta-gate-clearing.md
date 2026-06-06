---
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/50
tags: [codescene, delta, cli]
---

## What went wrong

PR #50 introduced `.github/workflows/codescene-delta.yml` with a blocking `gate` running `cs delta origin/<base> HEAD --error-on-warnings`. The `gate` failed with three CodeScene findings: **Bumpy Road Ahead** + **Complex Method** in `gui-import.ts` and **Complex Conditional** in `mcp-stdio-entry.ts`.

## Why

The `cs delta` CLI treats boolean chains inside `if`/`for`/`while` as one "complex conditional" with N-1 branches. A 3-clause `||` is 2 branches — at threshold.

## Proposed fix

- **Per-source helper extraction for dispatch functions.** Thin `switch` over cases; each helper has cyclomatic ≤ 4.
- **Predicate extraction for boolean guards.** Move boolean chain out of `if` into a return expression.
- **Verify locally:** `bash scripts/ci-install-codescene-cli.sh` then `cs delta origin/<base> HEAD --error-on-warnings`.

## Scope

project
