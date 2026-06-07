---
description: Fix a CodeScene finding. Usage: /cs-fix <file> <function> <rule>
subtask: true
---

Fix a CodeScene delta finding.

1. Read the CodeScene notes in `REVIEW.md` (CodeScene CLI section)
2. Read the target file and identify the function
3. Apply the smallest refactor that clears the rule
4. Run `bunx eslint <file> --max-warnings 0` and `bash scripts/ci-codescene-delta.sh <base> HEAD`
5. Report the new CC, nesting depth, and bump count
