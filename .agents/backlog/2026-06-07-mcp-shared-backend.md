# MCP shared backend — auto-ensure + attach

source: [docs/plans/2026-06-07-mcp-shared-backend.md](../../docs/plans/2026-06-07-mcp-shared-backend.md)

## Todos

- [ ] spec-mcp-shared-backend — Write specs/mcp-shared-backend.md; update DESIGN.md, specs/README.md, AGENTS.md, cross-links in mcp.md and cli.md
- [ ] plan-backlog-sync — Copy plan to docs/plans/2026-06-07-mcp-shared-backend.md; align .agents/backlog/2026-06-07-mcp-shared-backend.md 1:1 with todos
- [ ] ensure-backend-module — Add ensure-backend.ts: lock, port auto-increment, healthy probe, detached spawn, ensureSharedBackend()
- [ ] main-refactor — Refactor main.ts: cmdServeSharedStdio, cmdServeStandalone, cmdBridge, cmdStop; routing and exit codes
- [ ] entry-config — Update mcp-stdio-entry.ts and config.ts: shared default, --standalone, remove ephemeral port for agent path
- [ ] endpoint-store — Extend endpoint-store.ts: findHealthyEndpoint, optional mode field on record
- [ ] java-cli-stop — Add McpStopCommand, --standalone on McpServeCommand, McpCommandSupport args; register in McpCommand
- [ ] tests — Add ensure-backend.test.ts, endpoint-store/config/main tests; test-mcp-stdio --standalone smoke
- [ ] docs-verify — Update docs/usage.md and launcher README; run full verify block and manual multi-agent smoke
