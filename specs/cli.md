# CLI Specification

## Commands

### openadt

Root command. Shows usage when run without subcommands.

Options:

- `--help, -h` — Show help
- `--version, -V` — Show version

---

### openadt config

Show the effective merged configuration (paths, systems, runtime — no secrets).

```bash
openadt config
openadt config --config <path>
```

---

### openadt config bootstrap

Auto-detect SAP systems and runtime prerequisites and write config fragments. Does **not** build the SDK runtime jar.

```bash
openadt config bootstrap
openadt config bootstrap --check
openadt config bootstrap --config <path>
```

Options:

- `--check` — Show detected systems and validate without writing config
- `--config, -c <path>` — Config file path (default write target: `~/.openadt/config.toml`)

Detectors and outputs: same as legacy setup (see below).

---

### openadt config build

Build the full SAP SDK runtime jar into `~/.openadt/runtime/` for `fetch`/`proxy` (Windows; uses `adt_plugins_dir` from config).

```bash
openadt config build
openadt config build --force
openadt config build --config <path>
```

Options:

- `--force` — Rebuild even when the runtime jar already matches the installed OpenADT version
- `--config, -c <path>` — Config file path

---

### openadt config destinations create

Create or update a destination authentication profile in config.

```bash
openadt config destinations create \
  --alias DEV \
  --profile sso \
  --transport http \
  --auth browser-sso \
  --discovery-url https://dev-adt.example.com/sap/bc/adt \
  --client 100 \
  --language EN \
  --default-profile
```

Options:

- `--alias <name>` — Destination alias (required)
- `--profile <name>` — Profile name (required)
- `--transport <mode>` — ADT transport (`sdk`, `http`, `rest-rfc`)
- `--auth <kind>` — Authentication kind (e.g. `browser-sso`, `snc`)
- `--discovery-url <url>` — ADT discovery URL (required for HTTP/browser SSO profiles)
- `--client <client>` — SAP client (required)
- `--language <lang>` — SAP language (default: `EN`)
- `--description <text>` — Destination description
- `--system-id <sid>` — SAP system ID (defaults to alias)
- `--default-profile` — Set this profile as the destination default
- `--callback-port <port>` — Browser SSO callback port (`0` = random)
- `--jco-mshost`, `--jco-msserv`, `--jco-r3name`, `--jco-group` — Shared JCo message-server settings
- `--snc-partnername`, `--snc-qop` — SNC profile overrides
- `--config, -c <path>` — Config file path

Behavior:

- Non-interactive mode requires all mandatory flags; never prompts for or stores passwords or SSO tickets
- Interactive mode prompts for missing required values when `System.console()` is available
- Re-running for the same alias/profile updates that profile instead of duplicating tables
- Fragment-based entrypoints write to `destinations/manual.openadt.toml`; flat configs are updated in place

---

### openadt setup

Shorthand for **`config bootstrap` + `config build`**: detect, save config, then build the SDK runtime when `adt_plugins_dir` is present.

```bash
openadt setup
openadt setup --check
openadt setup --skip-build
openadt setup --config <path>
```

Options:

- `--check` — Detect and print only; no save, no build
- `--skip-build` — Save config without building the SDK runtime jar
- `--config, -c <path>` — Config file path

After `openadt setup`, run `openadt config` to inspect paths and SDK runtime status.

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
openadt proxy DEV --profile snc
openadt proxy DEV --listen 127.0.0.1:8080
openadt proxy DEV --local-auth basic
openadt proxy DEV --local-username openadt --local-password <password>
```

Default listen address: `127.0.0.1:8079` (or `proxy.listen` from config).

While the proxy is running, `openadt fetch` for the same system and profile reuses it automatically (warm SAP session, no cold JVM/SDK startup). Use `openadt fetch --direct` to bypass the local proxy.

Arguments:

- `SYSTEM` — System alias to proxy (optional; defaults to first configured system)

Options:

- `--listen <host:port>` — Bind address and port (default: `proxy.listen` from config, else `127.0.0.1:8079`)
- `--local-auth <type>` — Local auth type (`basic`)
- `--local-username <name>` — Local proxy username (default: `openadt`)
- `--local-password <password>` — Local proxy password (falls back to `OPENADT_PROXY_PASSWORD` env var)
- `--profile <name>` — Authentication profile (e.g. `snc`, `sso`; defaults to destination `default_profile` or legacy destination settings). When omitted, `OPENADT_PROFILE` is used if set.
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
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --pretty
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --profile sso --pretty
openadt fetch DEV /sap/bc/adt/core/discovery --profile snc --pretty
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --pretty --raw
openadt fetch DEV /sap/bc/adt/example --method POST --body @request.xml --header "Content-Type: application/xml"
openadt fetch --base-url https://abap.example.invalid --client 100 --language EN --path /sap/bc/adt/core/http/systeminformation --accept application/vnd.sap.adt.core.http.systeminformation.v1+json
```

Arguments:

- `SYSTEM` — System alias
- `URL-OR-PATH` — ADT path or full URL; if full URL is supplied, only path and query are used

Options:

- `--method, -X <method>` — HTTP method (default: `GET`)
- `--header, -H "Name: Value"` — Add request header (repeatable)
- `--accept, -A <type>` — Set `Accept` header (repeatable; discovery defaults to `application/atomsvc+xml`)
- `--body, -d <text|@file>` — Request body text or `@file` to read from file
- `--output, -o <file>` — Write response body to file
- `--include, -i` — Include response status line and headers in output
- `--fail, -f` — Exit nonzero for HTTP status >= 400
- `--pretty` — Pretty-print JSON or XML response body
- `--raw` — Body only on stdout; no proxy/tip messages on stderr (for scripting; combine with `--pretty`)
- `--direct` — Call SAP via SDK/JCo even when a local `openadt proxy` is running
- `--config, -c <path>` — Config file path (default load order: `./.openadt/config.toml`, then `~/.openadt/config.toml`)
- `--base-url <url>` — Direct HTTP ADT mode (browser SSO reentrance-ticket flow, no system alias required)
- `--path <adt-path>` — ADT path in direct HTTP ADT mode
- `--client <client>` — SAP client in direct HTTP ADT mode
- `--language <lang>` — SAP language in direct HTTP ADT mode (default: `EN`)
- `--ca-cert <path>` — CA certificate for explicit HTTPS trust in HTTP transport mode
- `--truststore <path>` — Truststore file for explicit HTTPS trust in HTTP transport mode
- `--truststore-password <secret>` — Truststore password for explicit HTTPS trust in HTTP transport mode
- `--callback-port <port>` — Callback bind port for browser SSO flow (`0` picks a random local port)
- `--profile <name>` — Authentication profile (e.g. `snc`, `sso`; cannot be combined with `--base-url`)
- `--no-cache` — For HTTP SSO on this fetch only: do not read or write `~/.openadt/cache/http-sso/` (forces browser ticket flow when no `OPENADT_MYSAPSSO2`). Ignored for SDK/JCo transport. Does not apply when fetch reuses a running `openadt proxy` (use `--direct` to bypass the proxy)

Behavior:

- When `openadt proxy <SYSTEM>` is running for the same profile, `fetch` reuses it over loopback (fast path)
- Without a running proxy, `fetch` starts a cold SDK/JCo session (slow first call)
- `--body @file` reads request bytes from a file
- `--output <file>` writes response body bytes
- `--include` prints status and headers before body
- `--fail` exits nonzero for HTTP status >= 400
- `--pretty` auto-formats JSON and XML for human reading
- `--raw` writes only the response body to stdout with no proxy/tip messages on stderr
- `--pretty --raw` is the usual scripting combo (formatted body, clean stdout)
- Successful fetch prints only the response body on stdout by default; SDK/JCo/SNC, HTTP SSO, proxy, and destination diagnostics go to stderr only when `OPENADT_VERBOSE=true` (or use `--raw` to suppress proxy/tip stderr from fetch itself)
- Output is binary-safe
- `OPENADT_CONFIG` overrides default config lookup

SDK transport (default when `adt_plugins_dir` is configured; `adt.transport = "sdk"` or unset):

- Uses SAP ADT Java SDK (`com.sap.adt.*`) over JCo + SNC
- Prepares runtime once per process: native JCo/SNC, headless `com.sap.conn.jco.eclipse`, ADT communication activator, Secure Login Web Adapter hub
- Resolves destination in order: Eclipse workspace `.destination.properties` for the system SID, else `[destinations.<alias>]` from config
- `fetch` and `proxy` share `AdtSdkTransportClient` — identical logon and request path
- Some ADT resources need specific `Accept` headers; use `--accept` or `-H "Accept: ..."` (discovery paths get `application/atomsvc+xml` by default)
- Set `OPENADT_VERBOSE=true` for HTTP SSO, SDK/JCo/SNC, and destination diagnostics on stderr (off by default; fetch body stays on stdout)

HTTP transport (`adt.transport = "http"`):

- Does not use JCo or the ADT SDK
- Requires `destinations.<alias>.adt.discovery_url` (logical frontend from `saprules.xml`)
- Accepts SAP logon tickets from `OPENADT_MYSAPSSO2`, `secure_login.mysapsso2`, or `OPENADT_COOKIE_FILE`
- If no ticket is available, OpenADT runs browser reentrance-ticket SSO (see below)
- After browser SSO, OpenADT warms the SAP session (`GET …/sap/bc/adt/discovery`) and caches **`Set-Cookie` values** (e.g. `SAP_SESSIONID_*`) together with the reentrance ticket under `~/.openadt/cache/http-sso/` (user home only), plus resolved ADT API base. A second `fetch` should reuse ticket + session cookies without another callback. For many requests in one session, `openadt proxy <SYSTEM> --profile=sso` remains the fastest path. Use `fetch --no-cache` or `OPENADT_HTTP_SSO_NO_CACHE=1` to skip disk cache for one fetch or all processes
- `openadt fetch` reuses a running `openadt proxy` for the same alias/profile when present, so HTTP SSO (and extra browser tabs) run once per proxy process, not on every fetch

#### Browser reentrance-ticket SSO

OpenADT cannot read cookies from the user's browser. The CLI only obtains a logon ticket when SAP redirects the browser to the localhost callback with `reentrance-ticket=...` in the query string. That redirect requires an **ADT ICF browser session** (SAP session cookies in the browser profile) on the frontend host before `/sap/bc/adt/core/http/reentranceticket` will issue a ticket.

Typical flow:

0. **Optional landing** — only when `sso_landing_url` / `OPENADT_HTTP_SSO_LANDING_URL` is set to your **IdP/Okta app URL**. Do **not** use bare frontend `/` (often opens Fiori `/fiori#Shell-home` without ADT ICF cookies). Skip with `OPENADT_HTTP_SSO_SKIP_LANDING`.
1. **Reentrance-ticket (default SSO)** — localhost `/adt/open` opens a popup to `/sap/bc/adt/core/http/reentranceticket?redirect-url=http://localhost:…/adt/redirect`. **Do not** open `/sap/bc/adt/discovery` in the browser (Atom/ICF HTTP Basic). **IdP/SAML redirects** happen on the reentrance-ticket chain (or via optional `sso_landing_url`), then SAP redirects back with `reentrance-ticket=…`.
2. **Optional ADT bridge tab** — only when `OPENADT_HTTP_SSO_OPEN_BRIDGE=1`: may open bare `/sap/bc/adt` (never `/sap/bc/adt/discovery`) before reentrance. Off by default.
3. **Server-side warmup** — after the ticket is received, the CLI may `GET /sap/bc/adt/discovery` with `MYSAPSSO2` via Java HTTP client only (not a browser tab) to cache session cookies.

Environment (optional):

| Variable                                                    | Purpose                                                                                                                                    |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `OPENADT_HTTP_SSO_NON_INTERACTIVE`                          | Skip Enter prompts (`true`/`1`/`yes`)                                                                                                      |
| `OPENADT_HTTP_SSO_BRIDGE_WAIT_SECONDS`                      | Seconds to wait after bridge before reentrance when non-interactive (default `15`; `0` = warm session — also skips opening the bridge tab) |
| `OPENADT_HTTP_SSO_OPEN_BRIDGE`                              | Open optional bare `/sap/bc/adt` bridge tab before reentrance (never `/sap/bc/adt/discovery`)                                              |
| `OPENADT_HTTP_SSO_SKIP_BRIDGE`                              | Force bridge tab off even when `OPENADT_HTTP_SSO_OPEN_BRIDGE=1`                                                                            |
| `OPENADT_HTTP_SSO_SKIP_LANDING`                             | Skip optional `sso_landing_url`                                                                                                            |
| `OPENADT_HTTP_SSO_LANDING_URL`                              | Override landing URL (else `destinations.*.adt.sso_landing_url`)                                                                           |
| `OPENADT_HTTP_CALLBACK_HOST` / `OPENADT_HTTP_CALLBACK_PORT` | Loopback callback bind (`localhost` required for SAP redirect validation)                                                                  |
| `OPENADT_HTTP_CALLBACK_TIMEOUT_MINUTES`                     | Max wait for redirect (default `5`)                                                                                                        |
| `OPENADT_HTTP_SSO_NO_CACHE`                                 | Disable HTTP SSO disk cache read/write for the whole process (`1`/`true`; same effect as `fetch --no-cache` per fetch)                     |
| `OPENADT_VERBOSE`                                           | `true` — SDK/JCo/SNC bootstrap, HTTP SSO cache/cookie, proxy tip, SSO step URLs (default off)                                              |

To avoid browser SSO entirely: set `OPENADT_MYSAPSSO2` or `OPENADT_COOKIE_FILE`, or keep `openadt proxy` running after the first successful SSO so subsequent `fetch` calls reuse the proxy's in-memory ticket.

- Uses the Secure Login hub only to verify Web Adapter login when `secure_login.origin` and `secure_login.web_adapter_profile_id` are configured
- Resolves the ADT API base via `/.well-known/sap-adt-info` or `/sap/public/bc/icf/virtualhost`
- Supports explicit TLS trust override via runtime config (`runtime.http_ca_cert`, `runtime.http_truststore`, `runtime.http_truststore_password`) or env (`OPENADT_HTTP_CA_CERT`, `OPENADT_HTTP_TRUSTSTORE`, `OPENADT_HTTP_TRUSTSTORE_PASSWORD`)

Local SDK dev runner (not required in production installs):

- `scripts/openadt-sdk.ps1` — builds classpath from `apps/openadt-cli/target/sap-lib` with canonical JCo jar name and core JCo before `jco.eclipse`
