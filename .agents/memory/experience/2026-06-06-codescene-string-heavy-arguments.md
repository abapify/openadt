---
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/47
tags: [codescene, typescript, false-positive]
---

## What went wrong

Spent 6+ rounds chasing CodeScene's "String Heavy Function Arguments" advisory on `runtime-env.ts` (64.3% string args vs 39% threshold). Attempts: branded types, consolidated params, split `Env` class, extracted helpers — new issues kept appearing.

## Why

CodeScene's TypeScript analysis resolves branded types (`string & { ... }`) back to `string` for its metric. The metric is fundamentally about the domain — env var accessors inherently take string parameters.

## Proposed fix

When CodeScene flags a domain-inherent pattern (strings for env vars, numbers for ports), **do not chase the metric with type gymnastics**. Reply in the thread explaining the domain constraint and ask the repo owner to suppress the advisory in CodeScene's UI.

## Scope

project
