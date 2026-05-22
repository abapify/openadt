# Config File Specification

Config file is TOML format. Default locations:

- first existing: `./.openadt/config.toml`
- fallback: `~/.openadt/config.toml`
- `OPENADT_CONFIG` overrides both default locations
- `openadt setup` writes host-detected fragments under `~/.openadt/`
- devcontainer bootstrap writes container fragments under `.devcontainer/`

## Entrypoint shape

`config.toml` is an entrypoint that can merge fragment files:

```toml
version = 1

[merge]
strategy = "last-wins"
includes = [
  "destinations/*.openadt.toml",
  "local.openadt.toml"
]
```

Supported fragment areas:

- `destinations.*`
- `runtime`
- `secure_login`
- `proxy`

## Top-level fields

| Field            | Type    | Description                                                         |
| ---------------- | ------- | ------------------------------------------------------------------- |
| `version`        | integer | Config file version (current: 1)                                    |
| `merge.includes` | array   | Fragment include patterns, resolved relative to the entrypoint file |
| `merge.strategy` | string  | Merge strategy (`last-wins`)                                        |

## [runtime] section

| Field             | Type   | Description                                                                                                                                                                                               |
| ----------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `jco_jar`         | string | Absolute path to the SAP JCo Java archive. Setup stores a **canonical** path (`com.sap.conn.jco-<version>.jar`); Eclipse p2 names like `com.sap.conn.jco_3.1.13.jar` are copied via `JCoJarCanonicalizer` |
| `jco_native_dir`  | string | Directory containing JCo native libraries                                                                                                                                                                 |
| `sapcrypto`       | string | Absolute path to sapcrypto.dll / libsapcrypto.so                                                                                                                                                          |
| `adt_plugins_dir` | string | Eclipse/ADT plugin directory, typically `~/.p2/pool/plugins`                                                                                                                                              |

## [secure_login] section

| Field                    | Type   | Description                                                                                                                                  |
| ------------------------ | ------ | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `local_security_hub`     | string | URL of the SAP Secure Login Client hub (default: https://127.0.0.1:34443)                                                                    |
| `origin`                 | string | CORS Origin header for hub requests (must match the Secure Login Server JavaScript Web Client profile, e.g. `https://sls.example.com:50001`) |
| `referer`                | string | Referer header for hub requests                                                                                                              |
| `web_adapter_profile_id` | string | Secure Login Web Adapter profile UUID from the hub                                                                                           |
| `mysapsso2`              | string | Optional SAP logon ticket value for `transport = "http"` (prefer `OPENADT_MYSAPSSO2` env var in shells)                                      |

## [proxy] section

| Field      | Type   | Description                                |
| ---------- | ------ | ------------------------------------------ |
| `listen`   | string | Address to listen on (e.g. 127.0.0.1:8080) |
| `auth`     | string | Authentication mode: "basic" or "none"     |
| `username` | string | Username for basic auth                    |

## [destinations.<ALIAS>] section

Each destination profile:

| Field         | Type   | Description                                                 |
| ------------- | ------ | ----------------------------------------------------------- |
| `alias`       | string | Short name used to reference the system                     |
| `source`      | string | How the system was discovered (sapgui, eclipse-adt, manual) |
| `description` | string | Human-readable name                                         |
| `system_id`   | string | SAP system ID (SID, e.g. "PRD")                             |
| `client`      | string | SAP client number (e.g. "100")                              |
| `language`    | string | Logon language (e.g. "EN")                                  |
| `user`        | string | Default logon user                                          |

### [destinations.<ALIAS>.jco] subsection

| Field             | Type   | Description                        |
| ----------------- | ------ | ---------------------------------- |
| `ashost`          | string | Application server hostname        |
| `sysnr`           | string | System number (e.g. "00")          |
| `mshost`          | string | Message server hostname            |
| `msserv`          | string | Message server port/service        |
| `r3name`          | string | SAP system name for load balancing |
| `group`           | string | Logon group for load balancing     |
| `snc_mode`        | string | SNC mode (0=off, 1=on)             |
| `snc_qop`         | string | SNC quality of protection (1-9)    |
| `snc_partnername` | string | SNC partner name (p:CN=...)        |
| `snc_sso`         | string | Use SNC SSO (0=off, 1=on)          |

### [destinations.<ALIAS>.adt] subsection

| Field                 | Type   | Description                                                                                            |
| --------------------- | ------ | ------------------------------------------------------------------------------------------------------ |
| `transport`           | string | ADT transport (`sdk`, `rest-rfc`, `http`)                                                              |
| `ashost`              | string | ADT server hostname (if different from JCo)                                                            |
| `discovery_url`       | string | Logical frontend base URL for HTTP transport (e.g. `https://host:8001/sap/bc/adt` from `saprules.xml`) |
| `authentication_kind` | string | Authentication kind for ADT                                                                            |

## Example fragments

```toml
version = 1

[destinations.DEV]
alias = "DEV"
description = "Development System"
system_id = "DEV"
client = "100"
language = "EN"

[destinations.DEV.jco]
ashost = "devserver.example.com"
sysnr = "00"
snc_mode = "1"
snc_qop = "9"
snc_partnername = "p:CN=DEV, O=Example, C=DE"
snc_sso = "1"
```
