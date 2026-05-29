# openadt-bootstrap

Host-side detectors for SAP GUI, NWBC, Eclipse ADT, runtime paths, and Secure Login hub.

## Packages

| Package                 | Role                                                                        |
| ----------------------- | --------------------------------------------------------------------------- |
| `org.openadt.bootstrap` | {@link org.openadt.bootstrap.SetupAnalyzer} and landscape/runtime detectors |

## Depends on

`openadt-config`

## Spec

[specs/setup.md](../../specs/setup.md), [specs/cli.md](../../specs/cli.md) (`openadt setup`)

## Test

```bash
../../mvnw -q test -pl apps/openadt-bootstrap
```
