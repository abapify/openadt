---
name: openadt-sdd
description: Spec-driven development for OpenADT — spec, test, code, verify-spec-sync, docs.
---

# OpenADT SDD workflow

## Order

1. **Spec** — [DESIGN.md](../../DESIGN.md) → update `specs/*.md` (and [vision.md](../../specs/vision.md) if product scope changes).
2. **Test** — failing test first for behavior changes (config, proxy, detectors, transport).
3. **Code** — implement in the package from [apps/ARCHITECTURE.md](../../apps/ARCHITECTURE.md).
4. **Package docs** — new leaf package: add `package-info.java`, a row in the module `README.md`, and (if needed) `apps/ARCHITECTURE.md` Package → spec table; `bun scripts/verify-package-docs.ts` must pass.
5. **Docs** — README/AGENTS/skills only when user-facing story changes.
6. **Verify**:

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
mvnw -q verify -Pdistribution
bun run openadt:test
```

## Refactors

- Package moves: move-only commits when possible; keep tests green each step.
- Do not change behavior while renaming packages unless the spec requires it.

## PR checklist

See [CONTRIBUTING.md](../../CONTRIBUTING.md) and [.github/pull_request_template.md](../../.github/pull_request_template.md).

## Detector sync

`SetupAnalyzer` wired detectors must match `specs/setup.md` and `specs/cli.md` — enforced by `verify-spec-sync.ts`.
