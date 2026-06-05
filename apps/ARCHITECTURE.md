# OpenADT apps architecture

## Maven reactor

| Module                                  | Artifact               | Contents                                                                                                 |
| --------------------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------- |
| [openadt-config](openadt-config/)       | `openadt-config`       | `org.openadt.config`                                                                                     |
| [openadt-sap-adt](openadt-sap-adt/)     | `openadt-sap-adt`      | `org.openadt.sap.adt.*`, `org.openadt.product.fetch`, shared `org.openadt.product.proxy` registry/client |
| [openadt-bootstrap](openadt-bootstrap/) | `openadt-bootstrap`    | `org.openadt.bootstrap` detectors                                                                        |
| [openadt-cli](openadt-cli/)             | `openadt` (shaded jar) | `org.openadt.cli`, `org.openadt.product.proxy` HTTP server                                               |

Dependency flow: `config` ← `sap-adt` ← `bootstrap` ← `cli`.

## Package → spec

| Package                               | Spec                                                                                       |
| ------------------------------------- | ------------------------------------------------------------------------------------------ |
| `org.openadt.cli`                     | [specs/cli.md](../specs/cli.md)                                                            |
| `org.openadt.sap.adt.sdk`             | [specs/cli.md](../specs/cli.md), [specs/proxy.md](../specs/proxy.md)                       |
| `org.openadt.sap.adt.services`        | [specs/sdk-capabilities.md](../specs/sdk-capabilities.md), [specs/cli.md](../specs/cli.md) |
| `org.openadt.sap.adt.destination`     | [specs/config.md](../specs/config.md)                                                      |
| `org.openadt.sap.adt.bootstrap`       | [specs/setup.md](../specs/setup.md)                                                        |
| `org.openadt.sap.adt.fallback.http`   | [specs/cli.md](../specs/cli.md) (`transport=http`)                                         |
| `org.openadt.sap.adt.fallback.jcorfc` | [specs/cli.md](../specs/cli.md) (`transport=rest-rfc`)                                     |
| `org.openadt.product.fetch`           | [specs/cli.md](../specs/cli.md)                                                            |
| `org.openadt.product.proxy`           | [specs/proxy.md](../specs/proxy.md)                                                        |
| `org.openadt.config`                  | [specs/config.md](../specs/config.md)                                                      |
| `org.openadt.bootstrap`               | [specs/setup.md](../specs/setup.md)                                                        |

## Layering

| Layer              | Packages                                                      | Rule                      |
| ------------------ | ------------------------------------------------------------- | ------------------------- |
| Product            | `product.fetch`, `product.proxy`, `cli`                       | Orchestration only        |
| SAP SDK            | `sap.adt.sdk`, `sap.adt.services`, `destination`, `bootstrap` | All `com.sap.adt.*` usage |
| Fallback           | `sap.adt.fallback.*`                                          | Only when `transport≠sdk` |
| Config / bootstrap | `config`, `bootstrap`                                         | No imports from `product` |

Each leaf package under `apps/*/src/main/java/org/openadt/` includes `package-info.java` (see `scripts/verify-package-docs.ts`).

## Nx

- [`@nx/maven`](https://nx.dev/docs/technologies/java/maven/introduction) is registered in `nx.json` (reactor analysis via `dev.nx.maven:nx-maven-plugin` in the root `pom.xml`).
- App modules are Nx projects with Maven lifecycle targets `compile`, `test`, and `package` (`nx:run-commands` + `mvnw`; `metadata.mavenProject` matches coordinates for tooling).
- `openadt-cli` `package` / `test` use `-Pdistribution`.
- `./dev-openadt` and `bun run openadt` → `nx run openadt-cli:run`: cached `compile` on all app modules (fresh `target/classes`), `ensure-dev-jar` runs `package` only when no `openadt-*.jar` exists yet.
- Root scripts: `bun run openadt:build` → `nx package openadt-cli`; `bun run openadt:test` → `nx test openadt-cli`.

## Vision

[specs/vision.md](../specs/vision.md)
