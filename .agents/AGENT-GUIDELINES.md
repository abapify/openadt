# OpenADT Agent Guidelines

**All agents (Claude Code, subagents, external tools) must follow these principles.**

See also: [`docs/codescene.md`](../docs/codescene.md) and [`.agents/skills/openadt-codescene/SKILL.md`](./skills/openadt-codescene/SKILL.md).

## CodeScene Health Contract (Non-Negotiable)

All code **must** pass the CodeScene delta gate before merging:

```bash
cs delta origin/main HEAD --error-on-warnings
```

**Quality gates by directory:**

| Path | Gate | Fail On |
|------|------|---------|
| `tools/**/src/**/*.ts` | `clean_code_collective` | warnings or health < 10.0 |
| `scripts/**` | `clean_code_collective` | warnings or health < 10.0 |
| `apps/**/src/**` | `pay_down_tech_debt` | decline or low health in new code |
| `**/generated/**` | `bare_minimum` | lightweight review only |

## Thresholds & Refactoring Rules

| Metric | Threshold | Refactor Pattern |
|--------|-----------|------------------|
| Function cyclomatic complexity | ≤ 9 | Extract Method if ≥ 9 |
| File mean CC | ≤ 4 | Guard clauses + extract predicates |
| Function arguments | ≤ 4 | Parameter Object if > 4 |
| Primitive argument ratio (file) | ≤ 30% | Domain types instead of raw strings |
| String argument ratio (file) | ≤ 39% | Domain types + Parameter Objects |
| Function lines of code | ≤ 70 | Extract Method |
| File lines of code | ≤ 1000 | Extract helpers |
| Nesting depth | ≤ 4 | Guard clauses |

## Design Principles

1. **Design to 10.0 on first push** — Never inherit low-health code into a small PR. Simplify or extract from the start.

2. **Use domain types, not primitives:**
   - ✓ `type Config = { host: string; port: number }`; use `fn(cfg: Config)`
   - ✗ `fn(host: string, port: number)` — counts as 2 primitives per call

3. **Parameter Objects for related arguments:**
   ```typescript
   // Before
   function packArchive(stageDir: string, stageDirName: string, archivePath: string, ext: string): void

   // After
   type ArchivePackingOptions = { stageDir: string; stageDirName: string; archivePath: string; ext: string };
   function packArchive(opts: ArchivePackingOptions): void
   ```

4. **Guard clauses over nesting:**
   ```typescript
   // Bad: 4 levels
   if (x) { if (y) { if (z) { work() } } }

   // Good: guard clauses
   if (!x) return; if (!y) return; if (!z) return; work()
   ```

5. **Extract predicates for complex conditionals:**
   ```typescript
   // Before (2 branches in one expression → threshold)
   if (a && b && c && d && e) { ... }

   // After
   const isReady = a && b && c && d && e;
   if (isReady) { ... }
   ```

## Agent Behavior

- **Before writing code in `tools/**/src` or `scripts/**`:** Read `.agents/skills/openadt-codescene/SKILL.md` for refactoring recipes.
- **After 3 pushes with CodeScene findings:** Stop and report to user. Do not iterate unilaterally.
- **Domain-inherent strings** (env var names, file paths, platform names) **are NOT Primitive Obsession.** Do not wrap them in Parameter Objects; suppress the warning in the CodeScene UI if needed.
- **Test locally:** `bash scripts/ci-codescene-delta.sh origin/main HEAD` before claiming code is ready.

## Verification

Every agent **must** run this before considering work done:

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

**The CodeScene delta gate is the final arbiter** — it is the source of truth for whether code passes.

## References

- **Single source of truth:** [`docs/codescene.md`](../docs/codescene.md)
- **Refactoring recipes & stop conditions:** [`.agents/skills/openadt-codescene/SKILL.md`](./skills/openadt-codescene/SKILL.md)
- **Rules (locked):** [`.codescene/code-health-rules.json`](../.codescene/code-health-rules.json)
- **Quality gates (locked):** [`.codescene/custom-quality-gates.json`](../.codescene/custom-quality-gates.json)
