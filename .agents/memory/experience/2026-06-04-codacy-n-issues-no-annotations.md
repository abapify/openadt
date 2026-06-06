---
date: 2026-06-04
context: https://github.com/abapify/openadt/pull/40
tags: [codacy, linter, ci]
---

## What went wrong

First `/act` on PR #40 left the pipeline red: `Codacy Static Code Analysis` was `action_required` with output `3 new issues (0 max.) of at least severity.` and **zero** code annotations. The cloud app's UI requires JS and the API needs `CODACY_API_TOKEN`, so the issues were not visible from the agent.

## Why

Did not reproduce the linter locally. Codacy runs ShellCheck on the new `scripts/act/*.sh` files; running `shellcheck` locally found exactly the 3 reported issues.

## Proposed fix

When Codacy "N new issues (0 max.)" with `annotations=0` → install linter, run it, fix. Same pattern for Opengrep (`opengrep --config .semgrep.yaml`), SonarCloud, CodeQL.

## Scope

universal
