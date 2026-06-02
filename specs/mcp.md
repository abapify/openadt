# MCP Bridge (draft)

Experimental stdio MCP server wrapping `openadt fetch` for agent hosts (Cursor, Claude, Copilot).

## Tools

| Tool           | Args                 | Behavior                                                                 |
| -------------- | -------------------- | ------------------------------------------------------------------------ |
| `adt_fetch`    | `path`, `system?`    | Runs `openadt fetch [system] <path>` (uses session context when omitted) |
| `adt_discover` | `system?`, `format?` | Runs `openadt discovery [system] --format <format>`                      |
| `adt_logon`    | `system`, `format?`  | Runs `openadt auth login <system> --format <format>` (sets session)      |

Future: `adt_proxy_status` when proxy introspection is stable.

## Security

- No logging of credentials, cookies, tickets, or authorization headers.
- Tests and docs use fictional fixtures only (`DEV`, `dev-ms.example.com`).

## Implementation

See `tools/mcp-bridge/` — Bun stdio JSON-RPC stub delegating to the CLI.
