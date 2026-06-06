---
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/50
tags: [codescene, tests, duplication]
---

## What went wrong

Added two test functions to `gui-import.test.ts` for `destinationFileUris` (relative vs absolute path). The new `cs delta --error-on-warnings` job correctly flagged them as code-duplication and broke the PR.

## Why

Did not anticipate the new delta gate flagging _the new tests themselves_ the moment they were added. The gate is a per-PR diff check, not just a per-file check.

## Proposed fix

When adding tests under the new CodeScene delta gate, write similar-shape assertions as a `test.each` from the start. The same anti-duplication rules that applied to the source file now apply to the test file.

## Scope

project
