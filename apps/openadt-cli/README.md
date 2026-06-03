# openadt-cli

Picocli entrypoint and shaded distribution jar (`openadt`).

## Packages

| Package                     | Role                                                                                        |
| --------------------------- | ------------------------------------------------------------------------------------------- |
| `org.openadt.cli`           | {@link org.openadt.cli.OpenAdtCommand}, `fetch`, `proxy`, `auth`, `discovery`, `transports` |
| `org.openadt.product.proxy` | Local ADT proxy HTTP server (CLI module)                                                    |

## Depends on

`openadt-config`, `openadt-sap-adt`, `openadt-bootstrap`

## Test

```bash
../../mvnw -q test -pl apps/openadt-cli -Pdistribution
bun run openadt:test
```

## Run (SDK)

```bash
bun run openadt:test
# or scripts/openadt-sdk.ps1 fetch DEV /sap/bc/adt/discovery
```
