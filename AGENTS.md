# OpenADT Agent Guidelines

## Product north star

OpenADT is a **thin Java wrapper around the official SAP ADT SDK** for **`openadt fetch`** and **`openadt proxy`**. MCP is next ([specs/mcp.md](specs/mcp.md)). Bootstrap/setup only writes config; it is not the product.

Read [specs/vision.md](specs/vision.md) before broad changes.

## Package map

| Area                      | Package                           |
| ------------------------- | --------------------------------- |
| SDK client                | `org.openadt.sap.adt.sdk`         |
| Destinations              | `org.openadt.sap.adt.destination` |
| JCo / Secure Login        | `org.openadt.sap.adt.bootstrap`   |
| HTTP / REST-RFC fallback  | `org.openadt.sap.adt.fallback.*`  |
| Fetch glue                | `org.openadt.product.fetch`       |
| Proxy server (CLI module) | `org.openadt.product.proxy`       |
| Config                    | `org.openadt.config`              |
| Setup detectors           | `org.openadt.bootstrap`           |
| Picocli                   | `org.openadt.cli`                 |

Maven modules: [apps/ARCHITECTURE.md](apps/ARCHITECTURE.md).

## Rules

1. Read specs/ before broad implementation changes ([specs/README.md](specs/README.md)).
2. Prefer TDD for config, proxy auth, header redaction, detectors.
3. SAP/JCo integration tests: `@Tag("integration")`, skipped by default.
4. Never commit SAP JCo jars, sapcrypto, or Secure Login files. Do not download SAP ADT/JCo into CI, Docker images, or release zips — users supply licensed installs (`~/.p2`, SAP Support Portal archives).
5. **Never commit private SAP landscape data** — use fictional fixtures only: `DEV`, `DEVELOPER`, `dev-ms.example.com`, `DEV_100_developer_en`, `PUBLIC`, `p:CN=SAPServiceDEV`, fake UUIDs.
6. Redact secrets in logs.
7. Update specs/ when command behavior changes.
8. Scratch under `tmp/` (gitignored).
9. **Host OS owns JCo natives.** If `fetch`/`proxy` fails with `no sapjco3 in java.library.path`, compare `runtime.jco_native_dir` in `~/.openadt/local.openadt.toml` to the OS running Java (Windows: `sapjco3.dll`; Linux: `libsapjco3.so`). A path under `.devcontainer/dist/jco` from container bootstrap is wrong on a Windows host — run `openadt setup` (or `setup --check`) on that host before retrying. See `openadt-devcontainer-host-runtime`.

## PR checklist

- `bun scripts/verify-spec-sync.ts`
- `bun scripts/verify-package-docs.ts`
- `mvnw -q verify -Pdistribution` (repo root)
- `bun run openadt:test`
- Spec updates when behavior changes

## Specs

- [specs/vision.md](specs/vision.md) — product vision
- [specs/cli.md](specs/cli.md) — CLI
- [specs/config.md](specs/config.md) — config TOML
- [specs/proxy.md](specs/proxy.md) — proxy
- [specs/setup.md](specs/setup.md) — detectors
- [specs/mcp.md](specs/mcp.md) — MCP (draft)

## Skills

| Skill                               | Trigger                             |
| ----------------------------------- | ----------------------------------- |
| `act`                               | `/act` on a PR                      |
| `openadt-product`                   | fetch, proxy, MCP, transport choice |
| `openadt-sdd`                       | spec → test → code                  |
| `openadt-sap-sdk-apis`              | SAP SDK discover/logon/fetch APIs   |
| `openadt-local-sap-runtime`         | SDK/JCo/SNC validation              |
| `openadt-devcontainer-host-runtime` | devcontainer / WSL vs host          |

Index: [.agents/skills/README.md](.agents/skills/README.md). Copilot: [.github/copilot-instructions.md](.github/copilot-instructions.md).

## Cloud agents (`@codex /act`, `@claude /act`)

Follow [.agents/skills/act/SKILL.md](.agents/skills/act/SKILL.md): fix in product code, reply in each thread, then resolve. Do not edit PR title/description unless asked.
