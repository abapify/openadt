---
name: openadt-codescene
description: Refactor TypeScript or Java to clear a CodeScene delta finding. Load before any `cs delta` failure, before extracting a method, or before adding a new test under the delta gate.
---

# OpenADT — CodeScene refactor skill

You are about to refactor code to satisfy the CodeScene delta gate
(`.github/workflows/codescene-delta.yml`). Read this skill before touching
any file.

## 0. Read first

- [`docs/codescene.md`](../../../docs/codescene.md) — the contract.
- [`.codescene/code-health-rules.json`](../../../.codescene/code-health-rules.json) — locked thresholds.
- [`.codescene/custom-quality-gates.json`](../../../.codescene/custom-quality-gates.json) — gate profile per area.

## 1. Run the gate first (against the right base)

```bash
# Discover the real base.
gh pr view N --json baseRefName -q .baseRefName
# Bit-for-bit CI:
bash scripts/ci-codescene-delta.sh origin/<baseRefName> HEAD
# Or for local-only speed:
bunx eslint . --max-warnings 0
```

Local `cs delta` is a smell-check, not evidence. The gate is
`gh pr checks N` on the current SHA.

## 2. Identify the smell

| CodeScene finding | What it means | Recipe |
|---|---|---|
| `Complex Method` | CC ≥ 9 | Extract Method; each helper ≤ 5 CC. |
| `Complex Conditional` | ≥ 2 `&&`/`\|\|`/`?:` in one expression | Predicate Extraction. |
| `Bumpy Road Ahead` | ≥ 2 nested bumps at depth ≥ 2 | Extract Method per bump. |
| `Large Method` | LoC > 70 | Extract Method. |
| `Excess Number of Function Arguments` | > 4 args | Parameter Object. |
| `Primitive Obsession` (file-level) | > 30% string/number args in a file | Domain types; not type gymnastics. |
| `String Heavy Function Arguments` | (advisory) | Usually a domain trait; do not chase. |
| `Code Duplication` | ≥ 10 LoC @ ≥ 75% similarity | `test.each`, shared helper, or extracted pure function. |
| `Large Assertion Blocks` | > 3 per test suite | `test.each` or table-driven assertions. |

## 3. The four refactoring recipes

### Extract Method (Complex Method, Bumpy Road, Large Method)

```ts
// before: 200-line function with parse → validate → dispatch → log
// after: parsePlan() / validatePlan() / dispatchPlan() / logPlan()
```

### Predicate Extraction (Complex Conditional)

```ts
// before
if (a && b && c && d && e) { ... }
// after
const isReady = a && b && c && d && e;
if (isReady) { ... }
```

### Parameter Object (Excess Arguments)

```ts
// before
fn(host, port, user, pwd, ssl, timeout);
// after
type ConnOptions = { host: string; port: number; user: string; pwd: string; ssl: boolean; timeout: number };
fn(opts: ConnOptions);
```

### Guard Clauses (Deep Nesting / Bumpy Road)

```ts
// before
if (plan) {
  if (plan.todos.length > 0) {
    if (hasOpenIssue(plan)) {
      if (canCreate(plan)) { /* happy path */ }
    }
  }
}
// after
if (!plan) return;
if (plan.todos.length === 0) return;
if (hasOpenIssue(plan)) return;
if (!canCreate(plan)) return;
/* happy path */
```

## 4. After the refactor

1. Re-run the verify block (the eight commands in
   [`docs/codescene.md`](../../../docs/codescene.md#pre-verification)).
2. Report the new CC, nesting depth, and bump count for the touched function.
3. Push to `origin/<headRefName>` (not `origin/pr-NN`).
4. Re-check `gh pr checks N` on the new SHA — the gate is the source of truth.

## 5. Stop conditions

- After 3 pushes on the same branch per `/act` cycle → stop and report.
- If the smell is `String Heavy Function Arguments` and the strings are
  domain-inherent (env var names, port numbers, paths), do not chase the
  metric — reply in the thread and ask the user to suppress in the CodeScene
  UI.
- If a test file triggers duplication, rewrite as `test.each` from the start —
  the delta gate applies to the PR diff, not just pre-PR file health.
