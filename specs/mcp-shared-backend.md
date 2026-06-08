# MCP shared backend (auto-ensure + attach)

OpenADT auto-ensures an MCP HTTP backend so multiple stdio agents attach without manual `mcp serve`. One `adt-lsc` per workspace serves all agents.

---

## Scope

- **Multi-agent stdio:** multiple agents share one `adt-lsc` + one HTTP MCP endpoint.
- **No manual `mcp serve`:** agents spawn `serve --stdio` and automatically attach to a healthy shared backend.
- **One `adt-lsc` per workspace:** avoids port conflicts and redundant SAP logon sessions.

---

## Modes

| Mode         | CLI                          | Description                                               |
| ------------ | ---------------------------- | --------------------------------------------------------- |
| `shared`     | `serve --stdio` (default)    | Ensure backend + attach stdio bridge; do NOT kill on exit |
| `standalone` | `serve --stdio --standalone` | Own `adt-lsc`, kill on exit (legacy monolithic path)      |
| `daemon`     | `serve` (HTTP-only)          | Detached HTTP backend for external HTTP MCP clients       |
| `bridge`     | `bridge --stdio`             | Attach-only to existing backend; fail if none healthy     |

---

## Ensure algorithm

1. **Find healthy endpoint** — scan endpoint store, probe HTTP, return one that responds.
2. **If none exists:**
   - Acquire **exclusive lock** (`~/.openadt/mcp/ensure-<port>.lock`).
   - Double-check after acquire (cold-start race).
   - **Spawn detached `serve`** (not `--stdio`): `detached: true`, `stdio: "ignore"`, `unref()`.
   - **Poll for healthy:** probe HTTP MCP every 500ms until ready or timeout (360s for SAP logon).
3. **Attach stdio bridge** — forward stdin/stdout to HTTP endpoint.
4. **Bridge shutdown** — stdin close / SIGTERM → exit bridge only; do **NOT** call `stopMcpServer` or kill `adt-lsc`.

---

## Healthy endpoint

Three-way probe:

1. `readEndpoint(port)` — file exists and parses.
2. `isProcessAlive(pid)` — process is running (ESRCH = dead).
3. `probeMcpHttp(port, token)` — HTTP POST returns 200 or 401 (Bearer valid).

---

## Port selection

| Source             | Port                                                                                                     |
| ------------------ | -------------------------------------------------------------------------------------------------------- |
| Default            | `2236`                                                                                                   |
| Override           | `--port` / `OPENADT_MCP_PORT`                                                                            |
| **Auto-increment** | If port busy (TCP bind fail or LSP `port-in-use` error): try N+1, then N+2, up to 65535, max 32 attempts |

---

## Attach resolution

- **Exactly one** healthy endpoint in store → attach to it (ignore preferred port).
- **More than one** → exit `5` + message: run `mcp list` to disambiguate.
- **None** → ensure algorithm (spawn new daemon).

---

## Daemon spawn

```typescript
const child = spawn(launcher, ["serve", "--port", String(port), ...], {
  detached: true,
  stdio: "ignore",
  cwd: process.cwd(),
  env: runtimeEnv,
});
// Unref the CHILD (not the parent) so the bridge process can exit without
// waiting for the daemon. `process.unref()` here would do nothing useful.
child.unref();
```

---

## Lock

| Item      | Value                                                 |
| --------- | ----------------------------------------------------- |
| File      | `~/.openadt/mcp/ensure-<port>.lock`                   |
| Create    | `fs.mkdirSync(dir, { recursive: true, mode: 0o700 })` |
| Exclusive | `open(path, flags: "wx")` — fail if exists            |
| Waiter    | Poll endpoint store every 500ms until lock released   |
| Timeout   | 360s (SAP logon)                                      |
| Cleanup   | `fs.rmSync(lockFile)` on success or timeout           |

---

## Bridge shutdown

When stdin closes or SIGTERM received:

1. Stop forwarding new stdin messages.
2. Drain in-flight HTTP forwards.
3. **Exit bridge only** — do NOT call `stopMcpServer`, do NOT kill `adt-lsc`.

---

## Backend shutdown

`openadt mcp stop [--port]` — calls `stopTrackedMcpServers({ onlyPort })`. Exit `0` if stopped or already dead.

Idle timeout is out of scope for MVP.

---

## Cold-start race

```
Thread A: lock acquire → succeeds
Thread B: lock acquire → fails → waits
Thread A: double-check (endpoint exists now?) → spawn if not
Thread A: write endpoint, release lock
Thread B: wake → find healthy endpoint → attach
```

---

## Failure modes

| Condition                 | Client-visible behavior                      | Exit code |
| ------------------------- | -------------------------------------------- | --------- |
| Extension missing         | JSON-RPC error; exit `1`                     | 1         |
| Logon timeout             | JSON-RPC error; stderr explains Secure Login | 1         |
| Port in use               | stderr message; exit `4`                     | 4         |
| Multiple active endpoints | stderr + mcp list message; exit `5`          | 5         |
| Ensure lock timeout       | stderr; exit `6`                             | 6         |
| Daemon spawn failed       | stderr; exit `7`                             | 7         |
| HTTP never ready          | JSON-RPC error on queued requests; exit `3`  | 3         |

---

## Agent config

```json
{
  "mcpServers": {
    "sap-adt": {
      "command": "bun",
      "args": ["run", "mcp:stdio"]
    }
  }
}
```

`mcp:stdio` entry switches to shared mode automatically. Do NOT set `--standalone` in agent config.

---

## Security

- Bearer tokens in endpoint store mode `0600`.
- Redact `Authorization` in debug logs.
- Tests: fictional fixtures (`DEV`, `dev-ms.example.com`, fake UUIDs).

---

## CLI surface

| Command / flag                   | Behavior                                                        |
| -------------------------------- | --------------------------------------------------------------- |
| `mcp serve --stdio`              | Shared (ensure + bridge) — **new default**                      |
| `mcp serve --stdio --standalone` | Current monolithic path (owns `adt-lsc`, kills on exit)         |
| `mcp serve`                      | HTTP-only daemon (unchanged; used as detached child)            |
| `mcp stop [--port]`              | `stopTrackedMcpServers({ onlyPort })`; exit `0` if stopped/dead |
| `mcp bridge --stdio [--port]`    | Attach-only; fail if no healthy backend (tests / low-level)     |

---

## Implementation map

| Component         | Path                                                                         |
| ----------------- | ---------------------------------------------------------------------------- |
| CLI entry         | `apps/openadt-cli` → `McpServeCommand` → Bun launcher                        |
| Launcher          | `tools/sap-adt-mcp-launcher/src/main.ts`                                     |
| **Ensure module** | `tools/sap-adt-mcp-launcher/src/ensure-backend.ts` (new)                     |
| Stdio bridge      | `tools/sap-adt-mcp-launcher/src/stdio-proxy.ts`                              |
| Stdio agent entry | `scripts/mcp-stdio.ts` → `tools/sap-adt-mcp-launcher/src/mcp-stdio-entry.ts` |
| Endpoint store    | `tools/sap-adt-mcp-launcher/src/endpoint-store.ts`                           |
| Config parsing    | `tools/sap-adt-mcp-launcher/src/config.ts`                                   |

---

## Out of scope (MVP)

- Idle auto-shutdown of backend.
- HTTP `url` in agent config (alternative to stdio bridge).
- Bridge process refcount.
- `--attach` alias (use `bridge` + ensure).
