# SDK service registry

Registered SAP ADT SDK operations exposed through one CLI path. Adding a service is **one line** in `SdkServiceRegistry` plus a small handler class.

## Registry

| Service id           | Handler                    | SAP API                                                                                      |
| -------------------- | -------------------------- | -------------------------------------------------------------------------------------------- |
| `discovery.document` | `DiscoveryDocumentHandler` | Discovery document GET on `RESOURCE_URI`                                                     |
| `transport.list`     | `TransportListHandler`     | `IAdtTransportService.findTransports(user, trfunction)` — default `trfunction=K` (workbench) |

Register in `SdkServiceRegistry`:

```java
register("my.service", "org.openadt.sap.adt.services.handlers.MyServiceHandler");
```

Handler implements `SdkServiceHandler`:

```java
public final class MyServiceHandler implements SdkServiceHandler {
    @Override
    public SdkServiceResult execute(SapAdtSessionContext context, SdkServiceArgs args) throws Exception {
        // call SDK, return SdkDocumentResult or SdkJsonResult
    }
}
```

Distribution builds exclude `handlers/**` and SAP-touching services; the registry compiles without SAP types and loads handlers at runtime when the full SDK classpath is present.

## CLI

Requires `auth login` (or explicit system alias) and SDK transport (`runtime.adt_plugins_dir`).

```bash
openadt discovery [--json] [--out <path>]
openadt transports list [--user USER] [--trfunction FUNC] [--json] [--out <path>]
```

Shared flags: `--out`, `--json`, `--format`, `--full`, `--param KEY=VALUE`.

## Results

- **Document** (`SdkDocumentResult`): Atom/XML body — JSON via `--json` or `.json` `--out` (same as `discovery`).
- **JSON** (`SdkJsonResult`): structured payload written with Jackson (`transport.list` → `{ user, trfunction, count, transports[] }`).

Reflection entry: `AdtSdkServiceGateway.invokeService` / `listSdkServices`.
