---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

When the user invokes **`/act`** or **`@copilot /act`**, read **[`.github/skills/act/SKILL.md`](../skills/act/SKILL.md)** (Copilot) or **`.agents/skills/act/SKILL.md`** before changing code.

## Copilot coding agent

- **No Playwright** for `/act`.
- **MCP only:** `pull_request_review_write` → `resolve_thread` for resolve.
- If resolve blocked (no `PRRT_` ids): still do CI/code fixes; list open thread URLs in summary.
- `bunx nx format:write` on touched `tools/**/*.ts` before commit.

## Default `/act` (everything on)

CI → review fixes → suggestions → resolve pass (MCP) → summary. Do not stop early when resolve is hard.
