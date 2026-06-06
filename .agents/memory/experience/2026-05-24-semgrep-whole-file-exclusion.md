---
date: 2026-05-24
context: https://github.com/abapify/openadt/pull/12
tags: [semgrep, codacy, false-positive]
---

## What went wrong

Agent added file-level semgrep exclusions for intentional loopback SSRF patterns.

## Why

Did not read `.codacy/instructions/review.md` — repo policy is line-specific `// nosemgrep: <rule-id>` only.

## Proposed fix

Domain false positives live in `.codacy/instructions/review.md`; do not edit `.semgrep.yml` to exclude whole production files.

## Scope

project
