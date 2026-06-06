# SAP MCP stdio + Cursor agent CLI — 2026-06-05

## Modified Files

- `tools/sap-adt-mcp-launcher/src/mcp-framing.ts` — `McpStdioDecoder` / `McpStdioEncoder`; auto-detect **Content-Length** (IDE) vs **NDJSON** (agent CLI); byte-safe framing kept
- `tools/sap-adt-mcp-launcher/src/stdio-proxy.ts` — stream bridge, `flush()`, transport sync on first stdin chunk
- `tools/sap-adt-mcp-launcher/src/mcp-stdio-entry.ts` — explicit stdin/stdout pipe (not `inherit`); ephemeral HTTP port per spawn; merges `[runtime]` from `~/.openadt/local.openadt.toml`
- `tools/sap-adt-mcp-launcher/src/main.ts` — `failStdioAndExit` awaits bridge flush; removed dead `runBackgroundLogon` call/body
- `tools/sap-adt-mcp-launcher/src/process.ts` — `windowsTaskkillPath()` (full `%SystemRoot%\System32\taskkill.exe`, not PATH)
- `tools/sap-adt-mcp-launcher/src/process.test.ts` — taskkill path test
- `specs/mcp.md` — dual transport, agent config, ephemeral port, minimal-PATH notes
- `.cursor/mcp.json` — repo-local: `${env:USERPROFILE}\.bun\bin\bun.exe` + relative `mcp-stdio-entry.ts`

## Key Findings

1. **False “hang” in verify scripts** — `Content-Length` must count **UTF-8 bytes** (`Buffer.byteLength`), not JS string length; non-ASCII tool descriptions broke parsers.
2. **Stdout backpressure** — large `tools/list` frames need `McpFrameEncoder` + `flush()` before exit.
3. **Agent `-32001` timeout / `-32000` Connection closed** — Cursor **agent CLI** sends **NDJSON** (`{"method":"initialize",...}`), not `Content-Length` frames. Bridge queued forever until ~60s cancel. Spy log: stdin `7b226d6574686f6422…` = raw JSON.
4. **Agent minimal PATH** — `taskkill` ENOENT at startup (`stopTrackedMcpServers`); fixed via full path. Do **not** rely on `cmd.exe` in agent PATH either.
5. **Port 2236 collisions** — stale `serve` orphans block next spawn; entry now picks ephemeral port (`OPENADT_MCP_PORT` override).
6. **SAP logon (separate from MCP bridge)** — under minimal env: `sncgss64.dll` / `SNCERR_INIT`; full env sometimes `Destination <DESTINATION_ID> does not exist`. Secure Login lib on test machine lacked `sncgss64.dll` (only `sapcrypto.dll` in config).

## Root Cause (symptoms vs reality)

| Symptom                             | Actual cause                                                       |
| ----------------------------------- | ------------------------------------------------------------------ |
| Verify script stuck on `tools/list` | Byte framing bug in test client                                    |
| Agent timeout ~65s                  | NDJSON stdin not parsed by Content-Length decoder                  |
| Agent Connection closed ~12s        | Process exit after logon error before flush (fixed) or port-in-use |
| `taskkill ENOENT`                   | Agent CLI minimal PATH                                             |
| Logon failure                       | Local SAP SNC/JCo runtime, not MCP protocol                        |

## Current Status

- ✅ 60/60 launcher unit tests
- ✅ `agent mcp list-tools sap-adt` with adtls → **14 tools** (~17s) after SECUDIR + createProjectAndLogon fixes
- ✅ Agent NDJSON transport, minimal PATH bootstrap, ephemeral port
- ✅ Manual Content-Length smoke still works for IDE path

## Root cause of latest logon failures (2026-06-05 evening)

1. **Wrong SECUDIR** — `buildAdtLscSpawnRuntime` picked `~/.openadt/sec` (HTTP CA PEM only) → SNC looked for `sncgss64.dll`. Fix: prefer `%APPDATA%\\SAP\\Common`, skip `.openadt/sec`, set `SNC_LIB=sapcrypto` path.
2. **JCo race** — `createProject` + `ensureLoggedOn` were separate loops; retry in `createProjectAndLogon` was dead code. Fix: call `createProjectAndLogon` for logon IDs.

## Next Steps

1. ~~Install/repair SAP Secure Login~~ — resolved by SECUDIR fix on test host
2. Run `agent mcp list-tools sap-adt` from repo root after pull
3. Optional: commit + PR

## Commands / Config

```bash
# Unit tests
cd tools/sap-adt-mcp-launcher && bun test

# IDE / manual stdio smoke (Content-Length client)
bun tools/sap-adt-mcp-launcher/src/test-mcp-stdio.ts

# Agent — enable once after mcp.json change
agent mcp enable sap-adt
agent mcp list-tools sap-adt

# Skip SAP logon (MCP layer only)
# Add to entry args: --import-from=none

# Repo-local .cursor/mcp.json (Windows)
# command: ${env:USERPROFILE}\.bun\bin\bun.exe
# args: tools/sap-adt-mcp-launcher/src/mcp-stdio-entry.ts
```

## Architecture note

```
Agent (NDJSON) ──► mcp-stdio-entry (pipe + runtime env)
                        └── main.ts serve --stdio
                              ├── bridge: NDJSON ↔ HTTP MCP
                              └── adt-lsc → localhost:<ephemeral>/mcp
```

IDE MCP uses same entry; first stdin byte `{` vs `Content-Length` selects reply format.

---

_Note: This session log is anonymized for public sharing._
