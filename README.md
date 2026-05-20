# OpenADT

OpenADT is an open-source Java CLI that bridges SAP ABAP Development Tools (ADT) access for systems that authenticate through SAP JCo + SNC + SAP Secure Login Client.

OpenADT is not a full ADT client. It is a **local credential bridge** with a minimal CLI that lets ADT-aware tools work with SAP systems that use JCo/SNC authentication.

## Quick Start

```bash
# Detect local SAP systems and write config
openadt setup

# Start a local HTTP proxy for a system (proxies ADT over RFC)
openadt proxy DEV --listen 127.0.0.1:8080

# Fetch a single ADT resource
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json
```

## How It Works

```
tool / curl / ADT-aware client
  -> localhost OpenADT proxy or openadt fetch
  -> SAP JCo
  -> SNC via sapcrypto
  -> SAP Secure Login Client
  -> ABAP RFC (SADT_REST_RFC_ENDPOINT)
  -> /sap/bc/adt/... resource
```

ADT requests are forwarded via the `SADT_REST_RFC_ENDPOINT` RFC function over JCo/SNC. This allows existing ADT-aware tools — including those that only support Basic auth — to connect through a local URL while OpenADT handles SAP authentication internally.

## CLI Reference

```
openadt setup [--check] [--config <path>]
openadt proxy <system> [--listen <host:port>] [--local-auth basic] [--local-password <pwd>]
openadt fetch <system> <url-or-path> [--method GET] [--header "Name: Value"] [--json] [--include] [--fail] [--body @file] [--output <file>]
```

See [`specs/cli.md`](specs/cli.md) for the full CLI contract.

## Config

OpenADT uses TOML configuration. Default path:

- **Windows**: `%APPDATA%\OpenADT\config.toml`
- **Linux / macOS**: `~/.config/openadt/config.toml`

Example `config.toml`:

```toml
version = 1

[runtime]
jco_jar = "C:\\Users\\user\\.p2\\pool\\plugins\\com.sap.conn.jco_3.1.13.jar"
jco_native_dir = "C:\\Users\\user\\AppData\\Local\\OpenADT\\jco-native"
sapcrypto = "C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib\\sapcrypto.dll"

[proxy]
listen = "127.0.0.1:0"
auth = "basic"
username = "openadt"

[[systems]]
alias = "DEV"
system_id = "DEV"
client = "200"
language = "EN"
user = "DEVELOPER"

[systems.jco]
mshost = "abap-dev-ms.example.com"
msserv = "3600"
r3name = "DEV"
group = "PUBLIC"
snc_mode = "1"
snc_qop = "9"
snc_partnername = "p:CN=SAPServiceDEV"
snc_sso = "1"

[systems.adt]
transport = "rest-rfc"
authentication_kind = "sso"
```

See [`specs/config.md`](specs/config.md) for the full config reference.

## Building

Requires JDK 17 or 21, Maven 3.x.

```bash
mvn package
java -jar target/openadt-1.0.0-SNAPSHOT.jar setup
```

## Prerequisites (not included)

OpenADT requires the following SAP components, which must be obtained separately:

- **SAP JCo** (`com.sap.conn.jco` jar and native library)
- **sapcrypto** native library (`sapcrypto.dll` / `libsapcrypto.so`)
- **SAP Secure Login Client** (optional; required for SNC SSO)

Configure paths in `config.toml` after running `openadt setup`.

## Contributing

See [`AGENTS.md`](AGENTS.md) for agent and contributor guidelines.

## Disclaimer

> This project does not include or redistribute SAP software.
> Users must obtain SAP JCo, SAP ADT, SAP Secure Login Client, and related native libraries from SAP or their organization under applicable SAP license terms.
> SAP is a trademark of SAP SE. This project is not affiliated with or endorsed by SAP SE.

## License

[Apache License 2.0](LICENSE)
