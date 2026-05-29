---
applyTo: "apps/**"
---

# Java layout (OpenADT)

OpenADT is an **SAP ADT SDK wrapper**. Put new code in the package that matches its role — see [apps/ARCHITECTURE.md](../../apps/ARCHITECTURE.md) and [specs/vision.md](../../specs/vision.md).

| Change type                       | Package                                                 |
| --------------------------------- | ------------------------------------------------------- |
| SDK session / `AdtSystemSession`  | `org.openadt.sap.adt.sdk`                               |
| Eclipse `.destination.properties` | `org.openadt.sap.adt.destination`                       |
| JCo natives, Secure Login hub     | `org.openadt.sap.adt.bootstrap`                         |
| HTTP or REST-RFC transport only   | `org.openadt.sap.adt.fallback.http` / `fallback.jcorfc` |
| `openadt fetch` glue              | `org.openadt.product.fetch`                             |
| Local proxy server                | `org.openadt.product.proxy`                             |
| TOML config models                | `org.openadt.config`                                    |
| `openadt setup` detectors         | `org.openadt.bootstrap`                                 |
| Picocli commands                  | `org.openadt.cli`                                       |

**Do not** add ADT HTTP client logic under `sap.adt.sdk` unless it is shared SDK bootstrap. **Do not** reimplement ADT protocol HTTP when SDK transport applies.

Tests mirror main packages under `src/test/java`. Prefer TDD for config, proxy auth, and detectors. Integration tests: `@Tag("integration")`, skipped by default.
