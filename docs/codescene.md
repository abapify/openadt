# CodeScene contract for OpenADT

This document is the single source of truth for what passes `cs delta
origin/<base> HEAD --error-on-warnings` in CI. Code-writing agents **must** read
it before producing new code, and refactor agents **must** load
[`.agents/skills/openadt-codescene/`](../.agents/skills/openadt-codescene/SKILL.md).

## Thresholds (TypeScript / JavaScript)

| Rule                                | Threshold                        | Source                                             |
| ----------------------------------- | -------------------------------- | -------------------------------------------------- |
| Function cyclomatic complexity      | **≤ 9**                          | `function_cyclomatic_complexity_warning`           |
| File mean CC                        | **≤ 4**                          | `file_mean_cyclomatic_complexity_warning`          |
| Function lines of code              | **≤ 70**                         | `function_lines_of_code_warning`                   |
| File lines of code                  | **≤ 1000**                       | `file_lines_of_code_for_warning`                   |
| Nesting depth                       | **≤ 4**                          | `function_nesting_depth_warning`                   |
| Bumpy Road bumps (depth ≥ 2)        | **≤ 2**                          | `function_bumpy_road_bumps_for_warning`            |
| Complex Conditional branches        | **≤ 2**                          | `function_complex_conditional_branches_warning`    |
| Function arguments                  | **≤ 4**                          | `function_max_arguments`                           |
| Constructor arguments               | **≤ 5**                          | `constructor_max_arguments`                        |
| Primitive-arg % in a file (TS)      | **≤ 30**                         | `file_primitive_obsession_percentage_for_warning`  |
| Duplication LoC                     | **≥ 10 LoC @ ≥ 75 % similarity** | `function_duplication_*`                           |
| Test large assertion blocks / suite | **≤ 3**                          | `unit_test_suite_number_of_large_assertion_blocks` |

These are also locked in
[`.codescene/code-health-rules.json`](../.codescene/code-health-rules.json) and
mirrored in `eslint.config.js`.

## What CodeScene counts (TS)

| Construct                   | Adds CC? | Adds a branch? |
| --------------------------- | -------- | -------------- |
| `if`                        | yes      | —              |
| `else if` / `else`          | no       | —              |
| `for` / `for…of` / `for…in` | yes      | —              |
| `while` / `do…while`        | yes      | —              |
| `switch case` (non-default) | yes      | —              |
| `&&`                        | —        | yes            |
| `\|\|`                      | —        | yes            |
| `?:` (ternary)              | —        | yes            |
| `??` (nullish)              | —        | yes            |
| `catch`                     | yes      | —              |
| Function call               | no       | no             |

A 3-clause `\|\|` inside an `if` is **2** extra branches — exactly at the
warning threshold (2). Predicate-extract the chain before adding a third.

## Pre-verification

```bash
bunx tsc --noEmit
bunx eslint . --max-warnings 0
bunx prettier --check .
bun run openadt:test
./mvnw -q verify -Pdistribution
bash scripts/ci-codescene-delta.sh origin/<baseRef> HEAD
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
```

## Quality gates by area

| Path                   | Gate                    | Review                        |
| ---------------------- | ----------------------- | ----------------------------- |
| `apps/**/src/**`       | `pay_down_tech_debt`    | full review; fail on decline  |
| `scripts/**`           | `clean_code_collective` | full review; fail on warnings |
| `tools/**/src/**/*.ts` | `clean_code_collective` | full review; fail on warnings |
| `**/generated/**`      | `bare_minimum`          | lightweight                   |

## Mental model

> Design new code to 10.0 on the CodeScene delta from the first push. Never
> inherit low-CC code into a small PR.

After 3 pushes on the same branch per `/act` cycle, stop and report back.
