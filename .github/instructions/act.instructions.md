---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

Read **[`.github/skills/act/SKILL.md`](../skills/act/SKILL.md)** before changing code.

## Copilot: resolve with gh CLI

Read-only MCP cannot resolve threads. After P0–P3:

```bash
bash .github/skills/act/resolve-open-threads.sh abapify openadt <PR>
```

No Playwright. `bunx nx format:write` on touched `tools/**/*.ts` before commit.

## Default `/act`

All steps on. Do not skip resolve because MCP is read-only — use gh.
