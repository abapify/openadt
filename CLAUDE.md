# OpenADT Claude Code Guidelines

**For universal guidelines applying to all agents (Claude Code, subagents, external tools), see [`.agents/AGENT-GUIDELINES.md`](./.agents/AGENT-GUIDELINES.md).** This document covers Claude Code-specific configuration and is kept minimal to avoid duplication.

## Quick Reference

All agents must follow these non-negotiable rules:

1. **CodeScene gate:** `cs delta origin/main HEAD --error-on-warnings` must pass
2. **Design-to-10.0:** Never inherit low-health code; simplify from first push
3. **Parameter Objects:** Use for > 4 args or > 30% primitives in file
4. **Guard clauses:** Use instead of nesting; target CC ≤ 9, depth ≤ 4
5. **Stop at 3 pushes:** Report findings; don't iterate unilaterally on CodeScene failures

## Claude Code Configuration

### Environment

```bash
export CS_ACCESS_TOKEN="<PAT from https://codescene.io/users/me/pat>"
```

### Pre-Verification

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

## Documentation Map

All agents should use these (no duplication):

- **Universal agent guidelines:** [`.agents/AGENT-GUIDELINES.md`](./.agents/AGENT-GUIDELINES.md) — refactoring rules, thresholds, design principles
- **Centralized memory:** [`.agents/memory/project/codescene-contract.md`](./.agents/memory/project/codescene-contract.md) — for agents to inherit context
- **Refactoring skill:** [`.agents/skills/openadt-codescene/SKILL.md`](./.agents/skills/openadt-codescene/SKILL.md) — recipes and stop conditions
- **Source of truth:** [`docs/codescene.md`](./docs/codescene.md) — official contract
- **Locked rules:** [`.codescene/code-health-rules.json`](./.codescene/code-health-rules.json)
- **Quality gates:** [`.codescene/custom-quality-gates.json`](./.codescene/custom-quality-gates.json)
