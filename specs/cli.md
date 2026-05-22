# CLI Specification

## Commands

### openadt

Root command. Shows usage when run without subcommands.

Options:

- `--help, -h` — Show help
- `--version, -V` — Show version

---

### openadt setup

Auto-detect SAP systems and runtime prerequisites from local tooling and write them to config.

```bash
openadt setup
openadt setup --check
openadt setup --config <path>
```

Options:

- `--check` — Show detected systems and validate without writing config
- `--config, -c <path>` — Config file path (default write target: `~/.openadt/config.toml`)

Detectors (in order):

1. `SapGuiLandscapeDetector` — reads SAP GUI landscape XML files, including load-balanced `Messageserver` / `Service` entries
2. `NwbcSystemDetector` — reads SAP Business Client recent connections to enrich system defaults such as client
3. `SapBusinessClientDetector` — checks for SAP Business Client installation
4. `EclipseAdtDetector` — reads Eclipse ADT workspace connections
5. `SapRulesDetector` — reads `saprules.xml` to enrich systems with observed ADT hosts
6. `RuntimeDetector` — finds `sapjco3.jar`, JCo native libraries, and `sapcrypto`
7. `SecureLoginDetector` — probes `https://127.0.0.1:34443` when available

Platform behavior:

- Supported host OS families: Windows, Linux, macOS
- On macOS, SAP GUI landscape XML is read from `~/Library/Application Support/SAP/Common/`
- On WSL, Windows-side SAP tooling is detected from `/mnt/c/Users/...` and `/mnt/c/Program Files/...`
- Runtime detection looks for `sapjco3.dll`, `libsapjco3.so`, or `libsapjco3.dylib` and matching `sapcrypto` libraries
- Detected JCo, `sapcrypto`, and Secure Login hub settings are optional prerequisites; destinations without SNC or transports that do not need JCo may omit them
- SAP GUI load-balanced entries are converted into JCo message-server settings (`mshost`, `msserv`, `r3name`, `group`)
- NWBC recents can fill missing `client` values for detected systems
- `saprules.xml` can fill missing `adt.ashost` values from previously used local ADT URLs

Outputs:

- List of detected systems with alias and source
- Detected runtime paths when found
- Detected Secure Login hub when reachable
- Writes fragment-based host config:
  - `config.toml`
  - `destinations/detected.openadt.toml`
  - `local.openadt.toml`

Devcontainer note:

- devcontainer bootstrap writes:
  - `.devcontainer/openadt-config.toml`
  - `.devcontainer/runtime.openadt.toml`
  - `.openadt/destinations/generated-*.openadt.toml`
- the primary devcontainer bootstrap path is host-side Bun/TypeScript tooling, not platform-specific shell scripts

---

### openadt proxy \<SYSTEM\>

Start the local ADT proxy server for a system.

```bash
openadt proxy DEV
openadt proxy DEV --listen 127.0.0.1:8080
openadt proxy DEV --local-auth basic
openadt proxy DEV --local-username openadt --local-password <password>
```

Arguments:

- `SYSTEM` — System alias to proxy (optional; defaults to first configured system)

Options:

- `--listen <host:port>` — Bind address and port (default: `127.0.0.1:0`, OS assigns port)
- `--local-auth <type>` — Local auth type (`basic`)
- `--local-username <name>` — Local proxy username (default: `openadt`)
- `--local-password <password>` — Local proxy password (falls back to `OPENADT_PROXY_PASSWORD` env var)
- `--config, -c <path>` — Config file path

Behavior:

- Binds to `127.0.0.1` by default (loopback-only)
- Uses the same transport stack as `openadt fetch` via `AdtTransportFactory` (see `specs/proxy.md`)
- Default transport is ADT SDK + JCo when `config.runtime.adt_plugins_dir` is set
- Fallback `rest-rfc` requires `config.runtime.jco_jar`
- Strips SAP authentication headers before forwarding (see `specs/proxy.md`)
- Enforces local Basic auth when `--local-auth basic` is set or `proxy.auth = "basic"` in config
- Local proxy credentials are NOT SAP credentials

---

### openadt fetch \<SYSTEM\> \<URL-OR-PATH\>

Fetch a single ADT resource via the configured ADT transport.

```bash
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json
openadt fetch DEV /sap/bc/adt/discovery --header "Accept: application/atomsvc+xml"
openadt fetch DEV /sap/bc/adt/example --method POST --body @request.xml --header "Content-Type: application/xml"
```

Arguments:

- `SYSTEM` — System alias
- `URL-OR-PATH` — ADT path or full URL; if full URL is supplied, only path and query are used

Options:

- `--method, -X <method>` — HTTP method (default: `GET`)
- `--header, -H "Name: Value"` — Add request header (repeatable)
- `--body, -d <text|@file>` — Request body text or `@file` to read from file
- `--output, -o <file>` — Write response body to file
- `--include, -i` — Include response status line and headers in output
- `--fail, -f` — Exit nonzero for HTTP status >= 400
- `--json` — Pretty-print JSON response body
- `--raw` — Write only response body bytes (binary-safe)
- `--config, -c <path>` — Config file path (default load order: `./.openadt/config.toml`, then `~/.openadt/config.toml`)

Behavior:

- Default method is `GET`
- `--body @file` reads request bytes from a file
- `--output <file>` writes response body bytes
- `--include` prints status and headers before body
- `--fail` exits nonzero for HTTP status >= 400
- `--json` pretty-prints JSON responses
- `--raw` writes only response body bytes
- Output is binary-safe
- `OPENADT_CONFIG` overrides default config lookup

SDK transport (default when `adt_plugins_dir` is configured; `adt.transport = "sdk"` or unset):

- Uses SAP ADT Java SDK (`com.sap.adt.*`) over JCo + SNC
- Prepares runtime once per process: native JCo/SNC, headless `com.sap.conn.jco.eclipse`, ADT communication activator, Secure Login Web Adapter hub
- Resolves destination in order: Eclipse workspace `.destination.properties` for the system SID, else `[destinations.<alias>]` from config
- `fetch` and `proxy` share `AdtSdkTransportClient` — identical logon and request path
- Some ADT resources need specific `Accept` headers (e.g. `systeminformation`); override with `-H`
- Set `OPENADT_VERBOSE=true` for stderr diagnostics (no secrets)

HTTP transport (`adt.transport = "http"`):

- Does not use JCo or the ADT SDK
- Requires `destinations.<alias>.adt.discovery_url` (logical frontend from `saprules.xml`)
- Requires a SAP logon ticket via `OPENADT_MYSAPSSO2`, `secure_login.mysapsso2`, or `OPENADT_COOKIE_FILE`
- Uses the Secure Login hub only to verify Web Adapter login when `secure_login.origin` and `secure_login.web_adapter_profile_id` are configured
- Resolves the ADT API base via `/.well-known/sap-adt-info` or `/sap/public/bc/icf/virtualhost`

Local SDK dev runner (not required in production installs):

- `scripts/openadt-sdk.ps1` — builds classpath from `apps/openadt-cli/target/sap-lib` with canonical JCo jar name and core JCo before `jco.eclipse`
