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

| Field                      | Type   | Description                                                                                                                                                                                               |
| -------------------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `jco_jar`                  | string | Absolute path to the SAP JCo Java archive. Setup stores a **canonical** path (`com.sap.conn.jco-<version>.jar`); Eclipse p2 names like `com.sap.conn.jco_3.1.13.jar` are copied via `JCoJarCanonicalizer` |
| `jco_native_dir`           | string | Directory containing JCo native libraries                                                                                                                                                                 |
| `sapcrypto`                | string | Absolute path to sapcrypto.dll, libsapcrypto.so, or libsapcrypto.dylib                                                                                                                                    |
| `adt_plugins_dir`          | string | Eclipse/ADT plugin directory, typically `~/.p2/pool/plugins`                                                                                                                                              |
| `http_ca_cert`             | string | Optional **global fallback** CA certificate path (PEM) for HTTP transport TLS. Prefer per-destination `destinations.*.adt.http_ca_cert` or `profiles.*.http_ca_cert` when landscapes differ.              |
| `http_truststore`          | string | Optional truststore path (JKS/PKCS12) used by HTTP ADT transport TLS trust                                                                                                                                |
| `http_truststore_password` | string | Optional truststore password for `http_truststore`                                                                                                                                                        |
| `http_callback_port`       | string | Optional localhost callback port for browser reentrance-ticket flow (`0` or unset = random local port)                                                                                                    |

## [secure_login] section

| Field                    | Type   | Description                                                                                                                                  |
| ------------------------ | ------ | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `local_security_hub`     | string | URL of the SAP Secure Login Client hub (default: https://127.0.0.1:34443)                                                                    |
| `origin`                 | string | CORS Origin header for hub requests (must match the Secure Login Server JavaScript Web Client profile, e.g. `https://sls.example.com:50001`) |
| `referer`                | string | Referer header for hub requests                                                                                                              |
| `web_adapter_profile_id` | string | Secure Login Web Adapter profile UUID from the hub                                                                                           |

Environment (hub login polling after MFA browser):

| Variable                         | Purpose                                                                                                          |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `OPENADT_HUB_LOGIN_WAIT_SECONDS` | Max seconds to poll hub status after `/slc3/api/login` (default `180` when hub browser monitor is on, else `30`) |
| `OPENADT_HUB_LOGIN_POLL_MS`      | Poll interval in ms (default `500`, minimum `100`)                                                               |
| `OPENADT_HUB_BROWSER`            | `0`/`false` disables hub `browserMonitor` on login (default: enabled)                                            |
| `mysapsso2`                      | string                                                                                                           | Optional SAP logon ticket value for `transport = "http"` (prefer `OPENADT_MYSAPSSO2` env var in shells) |

## [proxy] section

| Field      | Type   | Description                                |
| ---------- | ------ | ------------------------------------------ |
| `listen`   | string | Address to listen on (e.g. 127.0.0.1:8080) |
| `auth`     | string | Authentication mode: "basic" or "none"     |
| `username` | string | Username for basic auth                    |

## [destinations.<ALIAS>] section

Each destination profile:

| Field             | Type   | Description                                                 |
| ----------------- | ------ | ----------------------------------------------------------- |
| `alias`           | string | Short name used to reference the system                     |
| `source`          | string | How the system was discovered (sapgui, eclipse-adt, manual) |
| `description`     | string | Human-readable name                                         |
| `system_id`       | string | SAP system ID (SID, e.g. "PRD")                             |
| `client`          | string | SAP client number (e.g. "100")                              |
| `language`        | string | Logon language (e.g. "EN")                                  |
| `user`            | string | Default logon user                                          |
| `default_profile` | string | Profile name used when `fetch`/`proxy` omit `--profile`     |

Legacy destinations without `default_profile` or `profiles.*` keep working: OpenADT uses destination-level `[destinations.<ALIAS>.jco]` and `[destinations.<ALIAS>.adt]` as today.

### [destinations.<ALIAS>.profiles.<PROFILE>] subsection

Named authentication profiles overlay destination defaults. Shared target details (client, JCo message-server settings, and so on) live on the destination; each profile selects transport and auth behavior.

| Field                      | Type   | Description                                                                         |
| -------------------------- | ------ | ----------------------------------------------------------------------------------- |
| `transport`                | string | ADT transport for this profile (`sdk`, `rest-rfc`, `http`)                          |
| `authentication_kind`      | string | Authentication kind for this profile (e.g. `browser-sso`, `snc`)                    |
| `discovery_url`            | string | Logical frontend base URL for HTTP transport in this profile                        |
| `http_ca_cert`             | string | CA certificate chain (PEM) for this profile's HTTP frontend (overrides `[runtime]`) |
| `http_truststore`          | string | Truststore for this profile's HTTP frontend                                         |
| `http_truststore_password` | string | Truststore password                                                                 |
| `callback_port`            | string | Browser SSO callback port for this profile (`0` = random local port)                |

Optional nested subsections:

- `[destinations.<ALIAS>.profiles.<PROFILE>.jco]` — JCo/SNC overrides for this profile
- `[destinations.<ALIAS>.profiles.<PROFILE>.adt]` — ADT overrides for this profile

When `--profile` is omitted and `default_profile` is set, OpenADT resolves that profile. When both are omitted, legacy destination-level `jco`/`adt` settings apply unchanged.

Manual destinations created with `openadt config destinations create` are written to `destinations/manual.openadt.toml` when the entrypoint uses merge includes.

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

| Field                      | Type   | Description                                                                                                                |
| -------------------------- | ------ | -------------------------------------------------------------------------------------------------------------------------- |
| `transport`                | string | ADT transport (`sdk`, `rest-rfc`, `http`)                                                                                  |
| `ashost`                   | string | ADT server hostname (if different from JCo)                                                                                |
| `discovery_url`            | string | Logical frontend base URL for HTTP transport (e.g. `https://host:8001/sap/bc/adt` from `saprules.xml`)                     |
| `sso_landing_url`          | string | Optional corporate/IdP entry URL (your Okta app URL — not bare frontend `/`); also on `profiles.<PROFILE>.sso_landing_url` |
| `http_ca_cert`             | string | CA certificate chain (PEM) for this destination's HTTP frontend TLS (overrides `[runtime] http_ca_cert`)                   |
| `http_truststore`          | string | Optional truststore (JKS/PKCS12) for this destination's HTTP frontend                                                      |
| `http_truststore_password` | string | Truststore password                                                                                                        |
| `authentication_kind`      | string | Authentication kind for ADT                                                                                                |

## Example fragments

```toml
version = 1

[destinations.DEV]
alias = "DEV"
description = "Development System"
system_id = "DEV"
client = "100"
language = "EN"
default_profile = "snc"

[destinations.DEV.jco]
mshost = "dev-ms.example.com"
msserv = "3600"
r3name = "DEV"
group = "PUBLIC"

[destinations.DEV.adt]
discovery_url = "https://dev-adt.example.com/sap/bc/adt"

[destinations.DEV.profiles.snc]
transport = "sdk"
authentication_kind = "snc"

[destinations.DEV.profiles.snc.jco]
snc_mode = "1"
snc_qop = "9"
snc_partnername = "p:CN=SAPServiceDEV"
snc_sso = "1"

[destinations.DEV.profiles.sso]
transport = "http"
authentication_kind = "browser-sso"
discovery_url = "https://dev-adt.example.com/sap/bc/adt"
callback_port = "0"
```

Legacy single-profile example (still supported):

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
snc_partnername = "p:CN=SAPServiceDEV"
snc_sso = "1"
```
