# openadt-config

TOML configuration loading, destination profiles, and shared CLI logging.

## Packages

| Package              | Role                                                                                                      |
| -------------------- | --------------------------------------------------------------------------------------------------------- |
| `org.openadt.config` | {@link org.openadt.config.ConfigLoader}, {@link org.openadt.config.SystemProfile}, destination resolution |

## Depends on

None (leaf config module).

## Spec

[specs/config.md](../../specs/config.md)

## Test

```bash
../../mvnw -q test -pl apps/openadt-config
```
