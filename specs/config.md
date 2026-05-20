# Config File Specification

Config file is TOML format. Default locations:
- **Windows**: `%APPDATA%\OpenADT\config.toml`
- **Unix/macOS**: `~/.config/openadt/config.toml`

## Top-level fields

| Field | Type | Description |
|-------|------|-------------|
| `version` | integer | Config file version (current: 1) |

## [runtime] section

| Field | Type | Description |
|-------|------|-------------|
| `jco_jar` | string | Absolute path to sapjco3.jar |
| `jco_native_dir` | string | Directory containing JCo native libraries |
| `sapcrypto` | string | Absolute path to sapcrypto.dll / libsapcrypto.so |

## [secure_login] section

| Field | Type | Description |
|-------|------|-------------|
| `local_security_hub` | string | URL of the SAP Secure Login Client hub (default: https://127.0.0.1:34443) |
| `origin` | string | Origin header to use for SLC requests |
| `referer` | string | Referer header to use for SLC requests |

## [proxy] section

| Field | Type | Description |
|-------|------|-------------|
| `listen` | string | Address to listen on (e.g. 127.0.0.1:8080) |
| `auth` | string | Authentication mode: "basic" or "none" |
| `username` | string | Username for basic auth |

## [[systems]] section (array)

Each system profile:

| Field | Type | Description |
|-------|------|-------------|
| `alias` | string | Short name used to reference the system |
| `source` | string | How the system was discovered (sapgui, eclipse-adt, manual) |
| `description` | string | Human-readable name |
| `system_id` | string | SAP system ID (SID, e.g. "PRD") |
| `client` | string | SAP client number (e.g. "100") |
| `language` | string | Logon language (e.g. "EN") |
| `user` | string | Default logon user |

### [[systems.jco]] subsection

| Field | Type | Description |
|-------|------|-------------|
| `ashost` | string | Application server hostname |
| `sysnr` | string | System number (e.g. "00") |
| `mshost` | string | Message server hostname |
| `msserv` | string | Message server port/service |
| `r3name` | string | SAP system name for load balancing |
| `group` | string | Logon group for load balancing |
| `snc_mode` | string | SNC mode (0=off, 1=on) |
| `snc_qop` | string | SNC quality of protection (1-9) |
| `snc_partnername` | string | SNC partner name (p:CN=...) |
| `snc_sso` | string | Use SNC SSO (0=off, 1=on) |

### [[systems.adt]] subsection

| Field | Type | Description |
|-------|------|-------------|
| `transport` | string | Transport protocol (http/https) |
| `ashost` | string | ADT server hostname (if different from JCo) |
| `authentication_kind` | string | Authentication kind for ADT |

## Example

```toml
version = 1

[runtime]
jco_jar = "/opt/sap/jco/sapjco3.jar"
jco_native_dir = "/opt/sap/jco/"

[secure_login]
local_security_hub = "https://127.0.0.1:34443"

[proxy]
listen = "127.0.0.1:8080"
auth = "basic"
username = "developer"

[[systems]]
alias = "DEV"
description = "Development System"
system_id = "DEV"
client = "100"
language = "EN"

[systems.jco]
ashost = "devserver.example.com"
sysnr = "00"
snc_mode = "1"
snc_qop = "9"
snc_partnername = "p:CN=DEV, O=Example, C=DE"
snc_sso = "1"
```
