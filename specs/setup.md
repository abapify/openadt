# Setup Analyzer Specification

## Overview

The `openadt setup` command runs a series of detectors to discover SAP systems and runtime prerequisites configured in local tooling, then writes the results as config fragments.

Default host output:

- `~/.openadt/config.toml`
- `~/.openadt/destinations/detected.openadt.toml`
- `~/.openadt/local.openadt.toml`

## SetupAnalyzer

Orchestrates all detectors and aggregates results:

1. Runs SapGuiLandscapeDetector
2. Runs NwbcSystemDetector
3. Runs SapBusinessClientDetector
4. Runs EclipseAdtDetector
5. Runs SapRulesDetector
6. Runs RuntimeDetector
7. Runs SecureLoginDetector

Returns a `SetupResult` with:

- `systems` — list of discovered SystemProfile objects
- `runtime` — detected runtime paths for JCo and sapcrypto
- `secureLogin` — detected Secure Login hub settings when reachable
- `warnings` — list of warning messages

## Detectors

### SapGuiLandscapeDetector

Reads SAP GUI landscape XML files, including:

- `SAPUILandscape.xml`
- cached landscapes from `LogonServerConfigCache/*.xml`

Lookup paths:

- **Windows**: `%APPDATA%\SAP\Common\SAPUILandscape.xml`
- **macOS**: `~/Library/Application Support/SAP/Common/SAPUILandscape.xml`
- **WSL**: `/mnt/c/Users/<user>/AppData/Roaming/SAP/Common/SAPUILandscape.xml`
- **WSL**: `/mnt/c/Users/<user>/AppData/Roaming/SAP/LogonServerConfigCache/*.xml`

For classic `<System>` entries, extracts:

- `name` → alias, description
- `server` → jco.ashost
- `systemid` → system_id
- `sysno` → jco.sysnr

For load-balanced `<Service type="SAPGUI">` entries linked to `<Messageserver>`, extracts:

- `systemid` → alias, system_id, jco.r3name
- `Messageserver.host` → jco.mshost
- `Messageserver.port` → jco.msserv
- `Service.server` → jco.group
- `Service.sncname` → jco.snc_partnername, enables SNC mode and SNC SSO
- `Service.sncop` → jco.snc_qop

Sets `source = "sapgui"`.

### NwbcSystemDetector

Reads SAP Business Client recent connection files to enrich SAP GUI systems with defaults that are not always present in the SAP GUI landscape.

Lookup paths:

- **Windows**: `%APPDATA%\SAP\NWBC\Recents\*.recents`
- **WSL**: `/mnt/c/Users/<user>/AppData/Roaming/SAP/NWBC/Recents/*.recents`

Extracts:

- `url ... ~sysid=<SID>` → system_id
- `client` → client
- `connection` → description fallback

Sets `source = "sap-business-client"`.

### SapBusinessClientDetector

Checks if SAP Business Client is installed.

Lookup paths:

- **Windows**: `%APPDATA%\SAP\SAP Business Client`
- **Windows**: `%ProgramFiles%\SAP\SAP Business Client`
- **WSL**: `/mnt/c/Users/<user>/AppData/Roaming/SAP/NWBC`
- **WSL**: `/mnt/c/Program Files/SAP/NWBC800`

This detector is installation-only and does not add placeholder system profiles.

### EclipseAdtDetector

Reads SAP ADT destinations persisted by Eclipse in the workspace semantic cache. Each ADT project directory holds a `.destination.properties` file written by ADT on logon.

Lookup paths (`<workspace>/.metadata/.plugins/org.eclipse.core.resources.semantic/.cache/<id>/.destination.properties`):

- **All platforms**: `~/workspace`, `~/eclipse-workspace`
- **All platforms**: `~/eclipse/workspace` (Eclipse Installer / Oomph default)
- **Windows**: `%USERPROFILE%\eclipse-workspace`, `%USERPROFILE%\Documents\workspace`, `%USERPROFILE%\Documents\eclipse-workspace`
- **WSL**: the same paths under `/mnt/c/Users/<user>/`

Extracts:

- `id` → alias
- `systemId` → system_id and `jco.r3name`
- `client` → client
- `user` → user
- `language` → language
- `messageServer` → `jco.mshost`
- `messageServerService` → `jco.msserv`
- `group` → `jco.group`
- `partnerName` → `jco.snc_partnername`
- `SNCType` → `jco.snc_qop`
- `SSOEnabled` → `jco.snc_sso` (`0` stays `0`, anything else is `1`)

Sets `jco.snc_mode = "1"` and `source = "eclipse-adt"`.

Destination parsing is shared with `fetch`/`proxy` via `EclipseDestinationLocator` so setup and runtime resolve the same destinations.

### RuntimeDetector

Detects optional runtime prerequisites for ADT SDK and RFC calls. Missing JCo or `sapcrypto` does not fail setup; destinations can still be written for manual or HTTP-based auth.

Lookup paths:

- JCo jars from user Eclipse / p2 plugin pools — `~/.p2/pool/plugins` on **all platforms** (Windows, macOS, Linux, and WSL Windows homes)
- JCo native libraries: `sapjco3.dll`, `libsapjco3.so`, or `libsapjco3.dylib`, searched under `~/.p2` and the platform Eclipse install roots
- CryptoLib: `sapcrypto.dll`, `libsapcrypto.so`, `libsapcrypto.dylib`, or SAP Secure Login install paths
- **macOS** Secure Login Client: `/Applications/Secure Login Client.app/Contents/MacOS/lib/libsapcrypto.dylib` and the same path under `~/Applications`
- staged devcontainer runtime under `./.devcontainer/dist/` as fallback (platform-specific: Linux `.so` there will not load under Windows host Java)

#### JCo native extraction

On macOS and Linux the JCo native library is not present as a loose file — Eclipse ships it inside a platform p2 bundle (for example `com.sap.conn.jco.macosx.aarch64_<version>.jar` containing `lib/libsapjco3.dylib`). A filesystem search alone therefore cannot find it.

When no loose native is found, the detector selects the p2 bundle matching the running `os.name` / `os.arch`, extracts the native into `~/.openadt/runtime/jco-native/`, and points `runtime.jco_native_dir` at that directory. Bundle prefixes:

| Platform | Bundle prefix |
| -------- | ------------- |
| macOS arm64 | `com.sap.conn.jco.macosx.aarch64_` |
| macOS x86_64 | `com.sap.conn.jco.macosx.x86_64_` |
| Linux x86_64 | `com.sap.conn.jco.linux.x86_64_` |
| Windows x86_64 | `com.sap.conn.jco.win32.x86_64_` |

A loose native found on disk always wins over extraction. Extraction is idempotent and re-runs only when the bundle is newer than the extracted copy.

After devcontainer bootstrap, run `openadt setup` again on the host OS before `fetch`/`proxy` if `runtime.jco_native_dir` still points at `.devcontainer/dist/jco` without the matching native for that host (`sapjco3.dll` vs `libsapjco3.so`).

Fills:

- `runtime.jco_jar`
- `runtime.jco_native_dir`
- `runtime.sapcrypto`
- `runtime.adt_plugins_dir`

### SapRulesDetector

Reads SAP GUI `saprules.xml` to enrich detected systems with ADT hostnames observed from successful local ADT usage.

Lookup paths:

- **Windows**: `%APPDATA%\SAP\Common\saprules.xml`
- **WSL**: `/mnt/c/Users/<user>/AppData/Roaming/SAP/Common/saprules.xml`

Extracts:

- `context.system` → system_id
- `context.client` → client
- `files.name` entries that contain `/sap/bc/adt` → `adt.ashost` and `adt.base_url` (frontend origin)

Sets `source = "saprules"`.

### SecureLoginDetector

Probes the SAP Secure Login Client local security hub at `https://127.0.0.1:34443` when installed.

Optional. Does not produce system profiles. Returns `secureLogin.local_security_hub` when reachable.

Timeout: 2 seconds.

## Default Enrichment

After merging detector results, `SetupAnalyzer` applies conservative defaults for SNC SSO profiles:

- `alias = system_id` when alias is missing
- `user = <current OS username in uppercase>` when missing
- `language = "EN"` when missing
- `jco.sticky = "1"` and `jco.deny_initial_password = "1"` when SNC SSO is enabled
- `adt.transport = "sdk"` when `runtime.adt_plugins_dir` is detected
- `adt.transport = "rest-rfc"` when `runtime.adt_plugins_dir` is missing and `runtime.jco_jar` is detected
- `adt.transport = "sdk"` when neither runtime prerequisite is detected yet
- `adt.authentication_kind = "sso"` when SNC SSO is enabled

`openadt setup` / `config bootstrap` continue to write legacy destination-level `jco` and `adt` tables (no `profiles.*` yet). Use `openadt config destinations create` to add named profiles such as `sso` for HTTP browser SSO alongside detected SNC destinations.
