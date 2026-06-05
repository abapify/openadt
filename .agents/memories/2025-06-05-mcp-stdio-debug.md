# SAP MCP Debug Session - Checkpoint 3
Date: 2025-06-05

## Modified Files

### 1. tools/sap-adt-mcp-launcher/src/stdio-proxy.ts
- Changed bridge.run() to be async (non-blocking)
- Fixed failPending reference in then() block
- Allows stdio to queue requests while SAP logon proceeds

### 2. tools/sap-adt-mcp-launcher/src/main.ts  
- Always sync logon BEFORE MCP start (line ~383)
- Changed: ensureLoggedOnIds: destinationIds (not conditional on cfg.stdio)
- This bypasses SAP MCP's 30s request timeout

### 3. scripts/test-mcp-stdio.mjs
- Added --import-from=adtls flag (line ~34)
- Increased timeout from 120s to 300s (5 minutes)
- Critical for destination import from ~/.adtls/

## Critical Findings from Decompiled SAP MCP

### ADTMCPServer.java:132
```java
.requestTimeout(Duration.ofSeconds(30L))
```
SAP MCP server has HARD CODED 30-second request timeout!

### Server Capabilities
- resources: true
- prompts: true  
- tools: true
- logging: true

## Root Cause Identified

PROBLEM: SAP MCP server times out requests after 30 seconds, but SSO logon takes 1-2 minutes.

SOLUTION: Ensure SAP logon completes SYNCHRONOUSLY before starting MCP server.

## Working Configuration

```bash
# Must use --import-from=adtls for destination import
./dev-openadt mcp serve --stdio --import-from=adtls

# Or via bun directly
bun tools/sap-adt-mcp-launcher/src/main.ts serve --stdio --import-from=adtls

# Test script
bun run test:mcp:stdio
```

## Current Status

✅ stdio bridge works correctly (async, non-blocking)
✅ Synchronous logon before MCP start implemented
✅ Test script updated with proper flags
⚠️ SSO window doesn't appear in terminal mode - workarounds:
   1. Pre-logon via VS Code ADT (creates SSO ticket)
   2. Switch destination to basic auth (user/password)

## Next Steps

1. Test with active SSO ticket (logon via VS Code ADT first)
2. Or switch to basic auth in ~/.adtls/destinations.json
3. Document Devin CLI integration in docs/devin-mcp-setup.md
4. Consider making --import-from=adtls default for stdio mode?

## User Configuration (Redacted)

- Destination: <DESTINATION_ID> (client <CLIENT>, user <USER>)
- SSO enabled but ticket expired (no .pse files in SECUDIR)
- Secure Login service running but needs GUI interaction

## Test Command for Devin

```bash
devin mcp add sap-adt -- bun tools/sap-adt-mcp-launcher/src/main.ts serve --stdio --import-from=adtls
devin -p "What ABAP tools are available?"
```

---
*Note: This session log is anonymized for public sharing. Real system IDs, usernames, and paths have been replaced with placeholders.*
