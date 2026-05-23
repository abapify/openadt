---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**"
---

# PR follow-up (`/act`)

When the user invokes **`/act`** or **`@copilot /act`**, read **[`.github/skills/act/SKILL.md`](../skills/act/SKILL.md)** (Copilot) or **`.agents/skills/act/SKILL.md`** before changing code.

## Copilot coding agent

- **Never** `gh api graphql`, `gh api`, or `gh pr view --json` (403 — firewall).
- **Always** `github-mcp-server` for PR/CI/review; **resolve** via `pull_request_review_write` → `resolve_thread`, or Playwright **Resolve conversation**.
- Run `bunx nx format:write` on touched `tools/**/*.ts` before commit.

## Default `/act` (everything on)

CI → review fixes → suggestions → **mandatory resolve pass** (all open threads, including outdated) → summary with **open 0**.

Do not skip resolve. Do not use optional flags unless the user literally typed them.
