---
name: openadt-product
description: OpenADT product layer — fetch, proxy, transport choice (SDK default), MCP stub. Use when implementing or debugging ADT requests.
---

# OpenADT Product Layer

## North star

OpenADT wraps the **SAP ADT SDK** for `openadt fetch` and `openadt proxy`. Config bootstrap is not the product.

## Transport decision tree

1. **`adt.transport = sdk`** (default when `runtime.adt_plugins_dir` is set) → `AdtSdkTransportClient`
2. **`http`** → `org.openadt.sap.adt.fallback.http` only when explicitly configured
3. **`rest-rfc`** → `org.openadt.sap.adt.fallback.jcorfc` only when explicitly configured

Do not reimplement ADT HTTP in the SDK path.

## Code map

| Area | Package / module |
| ---- | ---------------- |
| SDK client | `org.openadt.sap.adt.sdk` (`apps/openadt-sap-adt`) |
| Destinations | `org.openadt.sap.adt.destination` |
| Fetch glue | `org.openadt.product.fetch` |
| Proxy server | `org.openadt.product.proxy` (`apps/openadt-cli`) |
| Config | `org.openadt.config` |
| Bootstrap | `org.openadt.bootstrap` |

## MCP

`tools/sap-adt-mcp-launcher/` — orchestrates official SAP ADT MCP (`adt-lsc` pipe LSP → `adtLs/mcp/startMCPServer` → HTTP `/mcp`). OpenADT `serve --stdio` is an HTTP→stdio adapter only.

**Spec (merge gate):** [specs/mcp.md](../../../specs/mcp.md) — section *Official SAP ADT MCP server interface* for LSP + HTTP contracts. SDD: spec before launcher changes ([AGENTS.md](../../../AGENTS.md)).

## Verify

```bash
bun scripts/verify-spec-sync.ts
./mvnw -q verify
bun run openadt:test
```
