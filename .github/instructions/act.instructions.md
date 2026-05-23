---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

Read **[`.agents/skills/act/SKILL.md`](../../.agents/skills/act/SKILL.md)** before changing code.

- Full `/act` = CI → review fixes → resolve pass → summary.
- **Resolve:** `bash .agents/skills/act/resolve-open-threads.sh OWNER REPO NUMBER` when `gh` is available.
- **`bunx nx format:write`** on touched `tools/**/*.ts` before commit.
- **Copilot SWE only:** [`.github/copilot-instructions.md`](../copilot-instructions.md) (MCP, firewall).
