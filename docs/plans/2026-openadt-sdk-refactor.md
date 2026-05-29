# 2026 OpenADT SDK refactor (summary)

Single entry point for the multi-module refactor and SDK CLI work (local build, May 2026).

## What was built

### Maven reactor

Root [pom.xml](../../pom.xml) (`1.1.2`) aggregates:

| Module              | Package roots                                        |
| ------------------- | ---------------------------------------------------- |
| `openadt-config`    | `org.openadt.config`                                 |
| `openadt-sap-adt`   | `org.openadt.sap.adt.*`, `org.openadt.product.fetch` |
| `openadt-bootstrap` | `org.openadt.bootstrap`                              |
| `openadt-cli`       | `org.openadt.cli`, `org.openadt.product.proxy`       |

See [apps/ARCHITECTURE.md](../../apps/ARCHITECTURE.md).

### Product commands (unchanged role)

- `openadt fetch` / `openadt proxy` — arbitrary ADT URIs via SDK transport (default) or HTTP/RFC fallback
- `openadt setup` / `openadt config bootstrap|build` — detectors + SDK runtime jar

### SDK diagnostics (new)

- `openadt adt discover|logon|logon-status` — typed SAP APIs via `org.openadt.sap.adt.services`
- MCP bridge ([tools/mcp-bridge](../../tools/mcp-bridge/)): `adt_discover`, `adt_logon`

### Docs and guardrails

- [specs/vision.md](../../specs/vision.md), [specs/sdk-capabilities.md](../../specs/sdk-capabilities.md)
- `package-info.java` + `scripts/verify-package-docs.ts`
- Skills: `openadt-product`, `openadt-sap-sdk-apis`, `openadt-sdd`

### Research (gitignored)

- `tmp/sap-sdk-research/` — apidoc index and pattern notes (see files there)

## What remains

| Item                | Notes                                                                                                    |
| ------------------- | -------------------------------------------------------------------------------------------------------- |
| Publish PR          | Large local diff; run verify checklist before merge                                                      |
| Live SAP validation | `adt discover/logon` with real `~/.openadt/config.toml` + SDK classpath                                  |
| UCDetector          | `-Pdeadcode` scaffold; plugin not on Maven Central — use manual review / optional depclean               |
| Phase 2 SDK         | `IRestResource`, `tools.core`, RIS search — [specs/sdk-capabilities.md](../../specs/sdk-capabilities.md) |
| MCP                 | Optional `adt_logon_status`, in-process services                                                         |

## Verify before merge

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -f pom.xml -Pdistribution
bun run openadt:test
```

## Related specs

- [specs/cli.md](../../specs/cli.md) — all commands
- [specs/config.md](../../specs/config.md) — TOML
- [docs/ROADMAP.md](../ROADMAP.md) — links to vision and phase 2
