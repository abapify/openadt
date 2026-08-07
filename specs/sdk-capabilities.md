# SAP ADT SDK capabilities (OpenADT)

Public API names from SAP ADT SDK 3.58.x (`com.sap.adt.core.apidoc`). OpenADT uses these for typed diagnostics; arbitrary ADT URIs stay on `openadt fetch` / `openadt proxy`.

## Session and destination

| API                         | Role                                          | OpenADT                                                 |
| --------------------------- | --------------------------------------------- | ------------------------------------------------------- |
| `IDestinationData`          | Destination id, user, system configuration    | Built in `AdtSdkTransportClient` or loaded from Eclipse |
| `AdtDestinationDataFactory` | Writable destination + auth token             | Config / Eclipse resolution                             |
| `SapDestinationResolver`    | Eclipse `.destination.properties` then config | `fetch`, `proxy`, `adt`                                 |

## Logon

| API                      | Methods                                                             | OpenADT                                                        |
| ------------------------ | ------------------------------------------------------------------- | -------------------------------------------------------------- |
| `IAdtLogonService`       | `isLoggedOn(destinationId)`, `ensureLoggedOn(data, token, monitor)` | `openadt auth login`, `openadt auth status` via `LogonService` |
| `AdtLogonServiceFactory` | `createLogonService()`                                              | Same                                                           |

## Discovery

| API                             | Methods                                                                    | OpenADT                                                                       |
| ------------------------------- | -------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `IAdtDiscovery`                 | `getStatus(monitor)`, `getCollectionMember(collection, category, monitor)` | `DiscoveryService.discover`; `fetchDiscoveryDocument` calls `getStatus` first |
| `AdtDiscoveryFactory`           | `createDiscovery(destinationId, RESOURCE_URI)`                             | `SdkDiscoveryAccess`; document GET uses `RESOURCE_URI` path via session       |
| `IStatelessSystemSession`       | `sendRequest(monitor, request)`                                            | `SdkAdtDocumentFetcher` — discovery document bytes (same as `fetch`)          |
| `IAdtDiscoveryCollectionMember` | `getUri()`, `getAcceptedContentTypes()`, …                                 | Optional `--collection` / `--category` on discover                            |

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
- EMF (`org.eclipse.emf.common_*`, `ecore_*`, `ecore.xmi_*`) — the discovery document is parsed with EMF

Bundles are resolved from the plugin pool by symbolic prefix, newest version wins; the staged set is written to `~/.openadt/runtime/sap-lib`. The JCo archive keeps its original file name (`com.sap.conn.jco-<version>.jar`) because JCo refuses to initialize from a renamed archive.

Do not build this classpath from every bundle in the pool: a pool commonly holds several versions of the same ADT bundle, and mixing them breaks logon.

## Headless bootstrap

The SDK is written for Eclipse and reaches for OSGi services that do not exist on a plain classpath. `SapSdkRuntime.prepare` therefore calls `AdtCommunicationBootstrap.prepare`, which runs, in order:

1. `EclipseRegistryBootstrap` — installs a standalone extension registry
2. `JCoEclipseBootstrap` — initializes the JCo Eclipse bridge
3. the `com.sap.adt.communication` activator itself

### Extension registry

`AdtLogonService.findExtensions()` is effectively:

```java
Platform.getExtensionRegistry()
    .getExtensionPoint("com.sap.adt.destinations.logonListeners")
    .getExtensions()
```

Outside Eclipse `getExtensionRegistry()` returns `null`, so logon fails with a `NullPointerException`. An empty registry does not help either — `getExtensionPoint` returns `null` for an unknown point and the same chain fails one link later. The extension points must genuinely be declared.

`EclipseRegistryBootstrap` installs a registry via `RegistryFactory` and declares the extension **points** of each SAP bundle on the classpath, read from its `plugin.xml` and deduplicated by bundle symbolic name.

The `<extension>` contributions are **deliberately dropped**. Registering them activates collaborators that only work inside a full Eclipse — notably `LoggingCommunicationListener`, which reads a preference and so reaches `ConfigurationScope.getLocation()`, requiring an OSGi configuration `Location` service that does not exist here. Declaring points alone is what the null-unsafe lookups need: they resolve to a point with zero extensions and logon proceeds.

Distribution JAR loads SDK implementations via reflection (`AdtSdkServiceGateway`) when SAP bundles are on the classpath.
