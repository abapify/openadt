# CLI Specification

## Commands

### openadt
Root command. Shows usage when run without subcommands.

Options:
- `--help, -h` — Show help
- `--version, -V` — Show version

### openadt setup
Auto-detect SAP systems from local tooling and write to config.

Options:
- `--config, -c <path>` — Config file path (default: platform-specific)
- `--dry-run` — Show detected systems without saving to config

Detectors (in order):
1. SapGuiLandscapeDetector — reads SAPUILandscape.xml
2. SapBusinessClientDetector — checks for SAP Business Client installation
3. EclipseAdtDetector — reads Eclipse ADT workspace connections
4. SecureLoginDetector — probes https://127.0.0.1:34443

Outputs:
- List of detected systems with alias and source
- Warnings if components not found (e.g., SLC not running)

### openadt proxy [SYSTEM_ALIAS]
Start the local ADT proxy server.

Arguments:
- `SYSTEM_ALIAS` — System alias to proxy (optional; defaults to first configured system)

Options:
- `--port, -p <port>` — Port to listen on (default: 8080)
- `--config, -c <path>` — Config file path

Behavior:
- Requires JCo jar configured in config.runtime.jco_jar
- Strips authentication headers before forwarding (see proxy.md)
- Optionally enforces Basic auth if config.proxy.auth = "basic"

### openadt fetch <PATH> [SYSTEM_ALIAS]
Fetch a single ADT resource via RFC.

Arguments:
- `PATH` — ADT URL path, e.g. /sap/bc/adt/programs/programs/MY_PROG
- `SYSTEM_ALIAS` — System alias (optional)

Options:
- `--method, -X <method>` — HTTP method (default: GET)
- `--header, -H <name:value>` — Add request header (repeatable)
- `--config, -c <path>` — Config file path

Output:
- HTTP response status line
- Response headers
- Response body
