# CLI Specification

## Commands

### openadt
Root command. Shows usage when run without subcommands.

Options:
- `--help, -h` — Show help
- `--version, -V` — Show version

---

### openadt setup
Auto-detect SAP systems from local tooling and write to config.

```bash
openadt setup
openadt setup --check
openadt setup --config <path>
```

Options:
- `--check` — Show detected systems and validate without writing config
- `--config, -c <path>` — Config file path (default: platform-specific)

Detectors (in order):
1. `SapGuiLandscapeDetector` — reads SAPUILandscape.xml
2. `SapBusinessClientDetector` — checks for SAP Business Client installation
3. `EclipseAdtDetector` — reads Eclipse ADT workspace connections
4. `SecureLoginDetector` — checks SAP Secure Login Client and probes `https://127.0.0.1:34443`

Outputs:
- List of detected systems with alias and source
- Warnings if components not found (e.g., SLC not running)

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
- Requires JCo jar configured in `config.runtime.jco_jar`
- Strips SAP authentication headers before forwarding (see `specs/proxy.md`)
- Enforces local Basic auth when `--local-auth basic` is set or `proxy.auth = "basic"` in config
- Local proxy credentials are NOT SAP credentials

---

### openadt fetch \<SYSTEM\> \<URL-OR-PATH\>
Fetch a single ADT resource via RFC.

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
- `--config, -c <path>` — Config file path

Behavior:
- Default method is `GET`
- `--body @file` reads request bytes from a file
- `--output <file>` writes response body bytes
- `--include` prints status and headers before body
- `--fail` exits nonzero for HTTP status >= 400
- `--json` pretty-prints JSON responses
- `--raw` writes only response body bytes
- Output is binary-safe

