# Copilot — OpenADT

Read [AGENTS.md](../AGENTS.md), [DESIGN.md](../DESIGN.md), [REVIEW.md](../REVIEW.md). SDK-first `fetch`/`proxy` — no ADT HTTP reimplementation unless `adt.transport=http`.

**`/act`:** [.agents/skills/act/SKILL.md](../.agents/skills/act/SKILL.md) only. Do not edit PR title/body unless asked. Fix code → reply per thread → then `resolve-open-threads.sh`.

**Never commit:** real landscape data, SAP jars, `~/.openadt/`.

```bash
bun scripts/verify-spec-sync.ts
./mvnw -q verify -Pdistribution
bun run openadt:test
```

Skills index: [.agents/skills/README.md](../.agents/skills/README.md). Path rules: [instructions/](instructions/).
