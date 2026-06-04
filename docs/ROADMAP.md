# OpenADT roadmap

High-level direction. Detailed behavior lives in [specs/](../specs/).

## North star

[specs/vision.md](../specs/vision.md) — thin Java wrapper around the official SAP ADT SDK for `fetch` and `proxy`; MCP next.

## Architecture

[apps/ARCHITECTURE.md](../apps/ARCHITECTURE.md) — Maven modules and package map.

## SDK capabilities

[specs/sdk-capabilities.md](../specs/sdk-capabilities.md) — which SAP APIs OpenADT uses today and phase 2 candidates.

Research notes (gitignored): `tmp/sap-sdk-research/`.

## Shipped (2026 refactor)

- Multi-module Maven reactor (`openadt-config`, `openadt-sap-adt`, `openadt-bootstrap`, `openadt-cli`)
- `openadt adt discover|logon|logon-status`
- MCP bridge tools for discover/logon
- Config bootstrap/build split; package docs guardrails

Summary: [docs/plans/2026-openadt-sdk-refactor.md](plans/2026-openadt-sdk-refactor.md).

## Next

| Theme                               | Spec / doc                                                          |
| ----------------------------------- | ------------------------------------------------------------------- |
| Merge and release `v1.1.x`          | [packaging/README.md](../packaging/README.md)                       |
| Live SAP validation of ADT commands | [docs/usage.md](usage.md) · [docs/contributing.md](contributing.md) |
| Typed SDK tools (RIS, object refs)  | [specs/sdk-capabilities.md](../specs/sdk-capabilities.md)           |
| MCP expansion                       | [specs/mcp.md](../specs/mcp.md)                                     |
| Dead code hygiene                   | `mvn -Pdeadcode` (when plugin available), optional depclean         |

## Explicitly later

- Replacing SDK transport with HTTP for convenience
- Publishing SAP/decompiled artifacts
- UCDetector as required CI gate before allowlist tuning
