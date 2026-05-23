---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

Read **[`.github/skills/act/SKILL.md`](../skills/act/SKILL.md)** before changing code.

## Copilot: resolve needs OPENADT_GH_PR_TOKEN

`GITHUB_TOKEN` cannot resolve threads. Agents secret **OPENADT_GH_PR_TOKEN** (PAT: Pull requests Read+Write). Then:

```bash
bash .github/skills/act/resolve-open-threads.sh abapify openadt <PR>
```

## Default `/act`

All steps on. Do not skip resolve because MCP is read-only — use gh.
