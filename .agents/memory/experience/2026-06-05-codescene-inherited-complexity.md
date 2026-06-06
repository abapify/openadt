---
date: 2026-06-05
context: https://github.com/abapify/openadt/pull/47
tags: [codescene, delta, complexity]
---

## What went wrong

CodeScene "Code Health Review (main)" failed on every CI run for PR #47. The flagged complexity deltas are all on stdio-bridge code first introduced in #42/#43 — not on the fixes this PR added.

## Why

The PR title is "fix/dev-openadt-docs-followup" but the body landed 20+ product fixes (review feedback from a multi-bot round on the stdio bridge). Each push re-triggers the delta report against the same complex methods.

## Proposed fix

On PRs that carry a CodeScene workflow, treat the delta as part of the **scope** of the PR. If the inherited complexity is out of scope, either (a) split the refactor into its own PR, or (b) ask the user to suppress the specific deltas before the next `/act`.

## Scope

project
