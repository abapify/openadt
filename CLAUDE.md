# Claude Code Guidelines

**All agents must follow the same rules. See [AGENTS.md](./AGENTS.md) — this is the single source of truth.**

Claude Code-specific configuration only:

## Environment

```bash
export CS_ACCESS_TOKEN="<PAT from https://codescene.io/users/me/pat>"
```

## Pre-Verification Checklist

Before pushing, run the same verify block as documented in [AGENTS.md](./AGENTS.md#verify-before-pr):

```bash
bunx eslint scripts/ .agents/skills/ --max-warnings 0
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -Pdistribution
bun run openadt:test
```

**Read [AGENTS.md](./AGENTS.md) for the full contract** — CodeScene health, SDD gate, orchestrator rules, and design principles that apply to all agents.
