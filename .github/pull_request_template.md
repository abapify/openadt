## Summary

<!-- What changed and why (SDK-first product context if relevant) -->

## Spec / docs

- [ ] Read [specs/vision.md](../specs/vision.md) if behavior or product positioning changed
- [ ] Updated specs under `specs/` when CLI, proxy, config, or setup behavior changed
- [ ] No real SAP landscape data (SIDs, hosts, credentials) in diff

## Verification

- [ ] `bun scripts/verify-spec-sync.ts`
- [ ] `bun scripts/verify-package-docs.ts`
- [ ] `mvnw -q verify -Pdistribution` (from repo root or affected modules)
- [ ] `bun run openadt:test` (or documented skip reason)

## Package map (if Java moved)

- [ ] New code is under the package from [apps/ARCHITECTURE.md](../apps/ARCHITECTURE.md)
- [ ] `package-info.java` updated for new leaf packages
