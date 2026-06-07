# OpenADT — context

`openadt fetch`, `openadt proxy`, MCP launcher for SAP ADT. Read [`AGENTS.md`](AGENTS.md) for the agent contract (SDD gate, Code Health ceilings, verify block, orchestrator doctrine). Key paths:

- Specs — [`specs/`](specs/) (vision, CLI, config, proxy, MCP, SDK)
- Skills — [`.agents/skills/`](.agents/skills/) (act, sdd, codescene, product, etc.)
- Code Health contract — [`eslint.config.mjs`](eslint.config.mjs) (scoped tripwire) + CodeScene delta gate on every PR
- Verify block — `bunx eslint scripts/ .agents/skills/ --max-warnings 0` then `bun scripts/verify-spec-sync.ts` then `bun scripts/verify-package-docs.ts` then `./mvnw -q verify -Pdistribution` then `bun run openadt:test`

This file is a cross-tool-compat alias for [`AGENTS.md`](AGENTS.md). If your tool only loads `CONTEXT.md`, treat this as the entry point and follow the link above.
