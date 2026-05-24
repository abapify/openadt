---
applyTo: ".github/**,specs/**,apps/**,.agents/**,tools/**,packaging/**"
---

# PR follow-up (`/act`)

Read **[`.agents/skills/act/SKILL.md`](../../.agents/skills/act/SKILL.md)** before changing code.

These are **path-specific repository instructions** for GitHub Copilot ([docs](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions#creating-path-specific-custom-instructions)). They apply together with [`.github/copilot-instructions.md`](../copilot-instructions.md).

## PR metadata

- **Never** change the pull request **title** or **description** unless the user explicitly asks.
- **Never** use the PR body as an agent task list or “open_threads=0” status board.

## Review threads (mandatory)

- **`/act` ≠ resolve-only.** Fix review feedback in `apps/`, `tools/`, `specs/`, etc.
- **Every thread** needs a substantive **in-thread reply** (commit SHA or reason to decline) **before** resolve.
- Resolve script is **P4, last** — never the first step.
- One top-level PR comment does **not** substitute for per-thread replies.

```bash
bash .agents/skills/act/resolve-open-threads.sh abapify openadt <PR_NUMBER>
```

## Hygiene

- **`bunx nx format:write`** on touched `tools/**/*.ts` before commit.
- **Copilot SWE only:** [`.github/copilot-instructions.md`](../copilot-instructions.md) (MCP, firewall).
