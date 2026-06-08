---
name: codescene-contract
description: OpenADT CodeScene health contract and design principles binding all agents
metadata:
  type: project
---

# CodeScene Health Contract

**All code must pass `cs delta origin/main HEAD --error-on-warnings` before merge.**

## Quality Gates (By Directory)

| Path | Gate | Fail On |
|------|------|---------|
| `tools/**/src/**/*.ts` | clean_code_collective | warnings OR health < 10.0 |
| `scripts/**` | clean_code_collective | warnings OR health < 10.0 |
| `apps/**/src/**` | pay_down_tech_debt | decline OR low health in new |
| `**/generated/**` | bare_minimum | lightweight only |

## Non-Negotiable Thresholds

- **Function cyclomatic complexity** ≤ 9 → Extract Method if ≥ 9
- **File mean CC** ≤ 4 → Guard clauses + extract predicates
- **Function arguments** ≤ 4 → Parameter Object if > 4
- **Primitive args (file)** ≤ 30% → Use domain types
- **String args (file)** ≤ 39% → Use domain types + Parameter Objects
- **Function LoC** ≤ 70 → Extract Method
- **File LoC** ≤ 1000 → Extract helpers
- **Nesting depth** ≤ 4 → Guard clauses

## Design-to-10.0 Principle

**Never inherit low-health code into a PR.** Simplify or extract from the first push. CodeScene score should be ≥ 10.0 for new code in `tools/**/src/**` and `scripts/**`.

## Parameter Object Pattern (Primitive Obsession Fix)

When a function takes multiple related primitives (especially strings), bundle into a domain type:

```typescript
// Bad: 4 string params
function packArchive(stageDir: string, stageDirName: string, archivePath: string, ext: string): void

// Good: Parameter Object
type ArchivePackingOptions = { stageDir: string; stageDirName: string; archivePath: string; ext: string };
function packArchive(opts: ArchivePackingOptions): void
```

## Guard Clauses (Reduce Nesting & CC)

```typescript
// Bad: 4 nested levels
if (x) { if (y) { if (z) { if (w) { work() } } } }

// Good: guard clauses
if (!x) return; if (!y) return; if (!z) return; if (!w) return; work()
```

## Extract Predicates (Complex Conditionals)

```typescript
// Bad: 2+ branches in one expression
if (a && b && c && d && e) { ... }

// Good: extract predicate
const isReady = a && b && c && d && e; if (isReady) { ... }
```

## Agent Rules

- **Read before writing:** `.agents/skills/openadt-codescene/SKILL.md` (refactor recipes)
- **Stop after 3 pushes:** If CodeScene findings persist, report and do NOT iterate unilaterally
- **Domain-inherent strings are NOT obsession:** env var names, file paths, platform names do not need wrapping
- **Verify locally:** Run `bash scripts/ci-codescene-delta.sh origin/main HEAD` before considering work done
- **Test everything:** Run the 8-command pre-verification checklist (see `docs/codescene.md`)

## Why

- **Design-to-10.0** ensures every PR is an improvement, not a regression
- **Parameter Objects** reduce argument count + primitive ratio, improving readability and testability
- **Guard clauses** keep nesting ≤ 4 and cyclomatic complexity ≤ 9, improving maintainability
- **Predicate extraction** makes complex conditionals readable and easier to test
- **Early verification** catches gate violations before CI, reducing iteration cycles

## Related Memories

- [[design-to-10.0]] — design principle for new code
- [[refactoring-recipes]] — Extract Method, Predicate Extraction, Parameter Object, Guard Clauses
