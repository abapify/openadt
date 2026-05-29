# openadt-sap-adt

SAP ADT integration: SDK transport, destinations, JCo/Secure Login bootstrap, HTTP/REST-RFC fallbacks, fetch glue, and shared proxy registry.

## Packages

| Package                               | Role                                                                             |
| ------------------------------------- | -------------------------------------------------------------------------------- |
| `org.openadt.sap.adt.sdk`             | {@link org.openadt.sap.adt.sdk.AdtSdkTransportClient}, transport factory         |
| `org.openadt.sap.adt.services`        | Typed SDK discover/logon ({@link org.openadt.sap.adt.services.DiscoveryService}) |
| `org.openadt.sap.adt.destination`     | Eclipse and config destination resolution                                        |
| `org.openadt.sap.adt.bootstrap`       | JCo and Secure Login runtime preparation                                         |
| `org.openadt.sap.adt.fallback.http`   | HTTP/browser SSO transport                                                       |
| `org.openadt.sap.adt.fallback.jcorfc` | REST-via-RFC transport                                                           |
| `org.openadt.product.fetch`           | Fetch transport selection and response formatting                                |
| `org.openadt.product.proxy`           | Local proxy registry and HTTP client                                             |

## Depends on

`openadt-config`

## Specs

[specs/cli.md](../../specs/cli.md), [specs/proxy.md](../../specs/proxy.md), [specs/sdk-capabilities.md](../../specs/sdk-capabilities.md)

## Test

```bash
../../mvnw -q test -pl apps/openadt-sap-adt
```

SDK-dependent tests require Eclipse ADT plugins (`adt.plugins.dir`); CI uses `-Pdistribution` without them.
