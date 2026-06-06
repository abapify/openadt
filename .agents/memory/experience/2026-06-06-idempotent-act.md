---
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/47
tags: [act, idempotency, workflow]
---

## What went wrong

PR #47 had 50 review threads, all already resolved by the author before `/act` was invoked. Running `/act` on an already-resolved PR is idempotent.

## Why

The PR was large (59 files, 4k+ additions) and had many bot reviewers. By the time `/act` was invoked, the author had already addressed everything.

## Proposed fix

Always run `pr-state.sh` first to check `OPEN_THREADS`. If 0 open threads + all review feedback addressed, skip to P0 (CI check) and report status without unnecessary thread work.

## Scope

universal
