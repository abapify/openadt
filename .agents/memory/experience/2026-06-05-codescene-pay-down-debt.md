---
date: 2026-06-05
context: https://github.com/abapify/openadt/pull/47
tags: [codescene, debt, refactoring]
---

## What went wrong

Three pushes on the same branch refactored every method CodeScene flagged. The check still fails on the third push. The delta gate requires 10.0 ("new code is healthy") on every changed file, but the absolute file-level complexity of the stdio bridge keeps files below threshold.

## Why

CodeScene's "Pay Down Tech Debt" profile measures absolute file-level code health against the _previous commit on the same branch's base_, not against a per-method delta. Inheriting pre-existing complexity into a `fix/...` branch means the PR cannot merge without splitting the refactor or suppressing deltas.

## Proposed fix

- On a follow-up PR whose scope is docs/chore, **do not** include product code refactors that touch files with inherited low health.
- When the inherited complexity must be paid down, do it as its own PR and aim for a clean 10.0 on every changed file in one push, not three.
- Stop after 3 pushes per `/act` cycle and report back.

## Scope

project
