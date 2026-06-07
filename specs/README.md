# Specifications (SDD)

Behavior contracts for OpenADT. **SDD enforcement:** [DESIGN.md](../DESIGN.md). **Change specs before or with code** when CLI, proxy, config, or setup behavior changes.

## Index

| Spec                                       | Scope                                                         |
| ------------------------------------------ | ------------------------------------------------------------- |
| [vision.md](vision.md)                     | Product north star, package map                               |
| [cli.md](cli.md)                           | Commands: `fetch`, `proxy`, `adt`, `config`, `setup`          |
| [sdk-capabilities.md](sdk-capabilities.md) | SAP ADT SDK APIs used by OpenADT                              |
| [sdk-services.md](sdk-services.md)         | Registered SDK service registry and handlers                  |
| [config.md](config.md)                     | `~/.openadt/config.toml` schema                               |
| [proxy.md](proxy.md)                       | Local HTTP proxy, auth, redaction                             |
| [setup.md](setup.md)                       | Detectors and bootstrap output                                |
| [mcp.md](mcp.md)                           | SAP ADT MCP launcher + official server interface (LSP + HTTP) |
| [mcp-shared-backend.md](mcp-shared-backend.md) | MCP shared backend (auto-ensure + attach)                     |
| [packaging.md](packaging.md)               | Releases, Scoop, Homebrew                                     |

## Workflow

1. Read relevant spec(s) and [vision.md](vision.md).
2. Add or update tests (TDD for behavior changes).
3. Implement in the package from [apps/ARCHITECTURE.md](../apps/ARCHITECTURE.md).
4. Run `bun scripts/verify-spec-sync.ts`, `bun scripts/verify-package-docs.ts`, `mvnw -q verify -Pdistribution`, `bun run openadt:test`.

## CI

`scripts/verify-spec-sync.ts` fails when wired detectors diverge from [setup.md](setup.md) / [cli.md](cli.md).
