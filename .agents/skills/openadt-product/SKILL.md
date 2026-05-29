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

## MCP (experimental)

`tools/mcp-bridge/` — stdio tool `adt_fetch` → `openadt fetch`. Spec: `specs/mcp.md`.

## Verify

```bash
bun scripts/verify-spec-sync.ts
./mvnw -q verify
bun run openadt:test
```
