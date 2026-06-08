# OpenADT Claude Code Guidelines

This document establishes how Claude agents and code reviews interact with the OpenADT project, with emphasis on CodeScene code health standards.

## CodeScene Health Contract

**Single Source of Truth:** [`docs/codescene.md`](./docs/codescene.md)

All code must pass the CodeScene delta gate before merging. The gate is configured per-directory:

- **`tools/**/src/**/\*.ts`** → `clean_code_collective`: fail on warnings or health < 10.0
- **`scripts/**`** → `clean_code_collective`: fail on warnings or health < 10.0
- **`apps/**/src/**`** → `pay_down_tech_debt`: fail on decline or low health in new code
- **`**/generated/**`** → `bare_minimum`: lightweight review

### Key Thresholds (TypeScript / JavaScript / Java)

| Metric                         | Threshold | Pattern                                 |
| ------------------------------ | --------- | --------------------------------------- |
| Function cyclomatic complexity | ≤ 9       | Extract Method if CC ≥ 9                |
| File mean CC                   | ≤ 4       | Guard clauses or extract predicates     |
| Function arguments             | ≤ 4       | Use Parameter Object for > 4 args       |
| Primitive argument ratio       | ≤ 30%     | Use domain types instead of raw strings |
| String argument ratio          | ≤ 39%     | Use domain types / Parameter Objects    |
| File lines of code             | ≤ 1000    | Extract helpers                         |

## Refactoring Recipes

See [`.agents/skills/openadt-codescene/SKILL.md`](./.agents/skills/openadt-codescene/SKILL.md) for detailed recipes:

1. **Extract Method** → Complex Method, Bumpy Road, Large Method
2. **Predicate Extraction** → Complex Conditional (> 2 branches)
3. **Parameter Object** → Excess Function Arguments, Primitive Obsession
4. **Guard Clauses** → Deep Nesting, Bumpy Road

### Parameter Object Pattern

When a function takes multiple related primitives (especially strings), bundle them into a domain type:

**Before (Primitive Obsession):**

```typescript
function packArchive(stageDir: string, stageDirName: string, archivePath: string, ext: string): void { ... }
```

**After (Parameter Object):**

```typescript
type ArchivePackingOptions = { stageDir: string; stageDirName: string; archivePath: string; ext: string };
function packArchive(opts: ArchivePackingOptions): void { ... }
```

## Pre-Verification Checklist

Before pushing or asking for review, verify locally:

```bash
bunx tsc --noEmit
bunx eslint . --max-warnings 0
bunx prettier --check .
bun run openadt:test
./mvnw -q verify -Pdistribution
bash scripts/ci-codescene-delta.sh origin/main HEAD
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
```

The delta gate is the final arbiter — test against the actual base branch.

## Agentic Guidelines

### When Writing Code

1. **Design to 10.0 on first push** — Never inherit low-complexity code into a small PR. Extract or simplify from the start.

2. **Use domain types, not raw primitives:**
   - ✓ `type Coordinate = { x: number; y: number }` then `fn(coord: Coordinate)`
   - ✗ `fn(x: number, y: number)` (counts as 2 primitives per invocation)

3. **Keep functions ≤ 4 parameters** — beyond that, use a Parameter Object.

4. **Cyclomatic complexity ≤ 9 per function:**
   - Each `if` / `else` / `for` / `while` / `catch` adds 1
   - Each `&&` / `||` / `?:` / `??` in an expression adds 1 branch
   - Extract predicates before chaining `||` or `&&` chains with > 2 clauses

5. **Guard clauses before nested blocks:**

   ```typescript
   // Bad: nested 4 levels
   if (x) {
     if (y) {
       if (z) {
         if (w) {
           /* work */
         }
       }
     }
   }

   // Good: guard clauses
   if (!x) return;
   if (!y) return;
   if (!z) return;
   if (!w) return;
   /* work */
   ```

6. **Stop refactoring after 3 pushes per task cycle** — report back with findings rather than iterating endlessly.

### When Reviewing Code

1. Run `cs delta <base-branch> HEAD --error-on-warnings` before approving.

2. If CodeScene fails, do NOT approve — request refactoring per `.agents/skills/openadt-codescene/SKILL.md`.

3. Check for primitive obsession: if a file is > 30% primitive args or > 39% string args, it needs domain types.

4. For new code in `tools/**/src/**` or `scripts/**`, verify health score ≥ 10.0 before merge.

### Agentic Behavior

- **Agents must read `docs/codescene.md` and `.agents/skills/openadt-codescene/SKILL.md` before writing code in tools/ or scripts/.**
- **After 3 pushes with CodeScene findings on the same branch, stop and report to the user** — don't iterate unilaterally.
- **Domain-inherent strings (env var names, file paths, platform names) should NOT be wrapped in Parameter Objects** — these are not Primitive Obsession; suppress the warning in the CodeScene UI if needed.

## Configuration

- **CodeScene CLI**: Requires `CS_ACCESS_TOKEN` environment variable (org PAT from https://codescene.io/users/me/pat)
- **CI/CD**: Manual workflow at [`.github/workflows/codescene-delta.yml`](./.github/workflows/codescene-delta.yml) — runs `bash scripts/ci-codescene-delta.sh <base> <head>`
- **Rules**: Locked in [`.codescene/code-health-rules.json`](./.codescene/code-health-rules.json); do not edit without team discussion.

## References

- **Contract & Thresholds**: [`docs/codescene.md`](./docs/codescene.md)
- **Refactoring Recipes**: [`.agents/skills/openadt-codescene/SKILL.md`](./.agents/skills/openadt-codescene/SKILL.md)
- **Rules Config**: [`.codescene/code-health-rules.json`](./.codescene/code-health-rules.json)
- **Quality Gates**: [`.codescene/custom-quality-gates.json`](./.codescene/custom-quality-gates.json)
- **Mental Models**: [`.agents/memory/mental-models/`](./.agents/memory/mental-models/)
- **Experience Logs**: [`.agents/memory/experience/`](./.agents/memory/experience/)
