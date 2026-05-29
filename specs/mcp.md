# MCP Bridge (draft)

Experimental stdio MCP server wrapping `openadt fetch` for agent hosts (Cursor, Claude, Copilot).

## Tools

| Tool           | Args                 | Behavior                                                |
| -------------- | -------------------- | ------------------------------------------------------- |
| `adt_fetch`    | `system`, `path`     | Runs `openadt fetch <system> <path>` and returns stdout |
| `adt_discover` | `system`, `profile?` | Runs `openadt adt discover <system> --json`             |
| `adt_logon`    | `system`, `profile?` | Runs `openadt adt logon <system> --json`                |

Future: `adt_proxy_status` when proxy introspection is stable.

## Security

- No logging of credentials, cookies, tickets, or authorization headers.
- Tests and docs use fictional fixtures only (`DEV`, `dev-ms.example.com`).

## Implementation

See `tools/mcp-bridge/` — Bun stdio JSON-RPC stub delegating to the CLI.
