---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

When the user invokes **`/act`** or **`@copilot /act`**, read **[`.github/skills/act/SKILL.md`](../skills/act/SKILL.md)** (Copilot) or **`.agents/skills/act/SKILL.md`** before changing code.

## Copilot coding agent

- **Never** `gh api graphql`, `gh api`, or `gh pr view --json` (403 — firewall).
- **Always** `github-mcp-server` for PR/CI/review; **resolve** via `pull_request_review_write` → `resolve_thread`, or Playwright **Resolve conversation**.
- Run `bunx nx format:write` on touched `tools/**/*.ts` before commit.

## Queue

- P0 CI → P1 blocking review → P2 nits → P3 suggestions
- Reply in thread, then **Resolve conversation** for every handled thread
- Do not claim merge-ready while unresolved review threads remain
