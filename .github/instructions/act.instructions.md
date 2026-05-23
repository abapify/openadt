---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

Read **[`.agents/skills/act/SKILL.md`](../../.agents/skills/act/SKILL.md)** before changing code.

- **`/act` ≠ resolve-only.** Fix review feedback in `apps/`, `tools/`, etc., reply in each thread, **then** run the resolve script.
- Resolve script last; never use it as a substitute for code changes.
- **`bunx nx format:write`** on touched `tools/**/*.ts` before commit.
- **Copilot SWE only:** [`.github/copilot-instructions.md`](../copilot-instructions.md) (MCP, firewall).
