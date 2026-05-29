# SAP ADT SDK capabilities (OpenADT)

Public API names from SAP ADT SDK 3.58.x (`com.sap.adt.core.apidoc`). OpenADT uses these for typed diagnostics; arbitrary ADT URIs stay on `openadt fetch` / `openadt proxy`.

## Session and destination

| API                         | Role                                          | OpenADT                                                 |
| --------------------------- | --------------------------------------------- | ------------------------------------------------------- |
| `IDestinationData`          | Destination id, user, system configuration    | Built in `AdtSdkTransportClient` or loaded from Eclipse |
| `AdtDestinationDataFactory` | Writable destination + auth token             | Config / Eclipse resolution                             |
| `SapDestinationResolver`    | Eclipse `.destination.properties` then config | `fetch`, `proxy`, `adt`                                 |

## Logon

| API                      | Methods                                                             | OpenADT                                                            |
| ------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `IAdtLogonService`       | `isLoggedOn(destinationId)`, `ensureLoggedOn(data, token, monitor)` | `openadt adt logon`, `openadt adt logon-status` via `LogonService` |
| `AdtLogonServiceFactory` | `createLogonService()`                                              | Same                                                               |

## Discovery

| API                             | Methods                                                                    | OpenADT                                            |
| ------------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------- |
| `IAdtDiscovery`                 | `getStatus(monitor)`, `getCollectionMember(collection, category, monitor)` | `openadt adt discover` via `DiscoveryService`      |
| `AdtDiscoveryFactory`           | `createDiscovery(destinationId, RESOURCE_URI)`                             | Uses `RESOURCE_URI` constant                       |
| `IAdtDiscoveryCollectionMember` | `getUri()`, `getAcceptedContentTypes()`, …                                 | Optional `--collection` / `--category` on discover |

## Low-level HTTP (existing product path)

| API                                        | Role                                    | OpenADT                                 |
| ------------------------------------------ | --------------------------------------- | --------------------------------------- |
| `IStatelessSystemSession`                  | `sendRequest(monitor, IRequest)`        | `AdtSdkTransportClient` for fetch/proxy |
| `AdtSystemSessionFactory`                  | `createStatelessSession(destinationId)` | Same                                    |
| `IRestResource` / `AdtRestResourceFactory` | Typed REST resources                    | **Phase 2** — not used in v1 CLI        |

## Phase 2 (not in v1)

- `com.sap.adt.tools.core.*` facades (project explorer, object APIs) — see gitignored `tmp/sap-sdk-research/tools-core-surface.md`
- `com.sap.adt.ris.search` — repository search
- Transport / CTS typed services (`com.sap.adt.transport`)
- MCP tools beyond discover/logon

Research index (local apidoc 3.58.2, gitignored): `tmp/sap-sdk-research/apidoc-index.md`, `communication-patterns.md`.

## Classpath

Eclipse ADT plugins under `runtime.adt_plugins_dir`, including at minimum:

- `com.sap.adt.communication_*`
- `com.sap.adt.compatibility_*` (discovery)
- `com.sap.adt.destinations_*` / `destinations.model_*`
- JCo + Eclipse runtime bundles (see `openadt config build`)

Distribution JAR loads SDK implementations via reflection (`AdtSdkServiceGateway`) when SAP bundles are on the classpath.
