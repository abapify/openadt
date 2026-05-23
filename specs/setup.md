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
4. Runs SapRulesDetector
5. Runs RuntimeDetector
6. Runs SecureLoginDetector

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

### RuntimeDetector

Detects optional runtime prerequisites for ADT SDK and RFC calls. Missing JCo or `sapcrypto` does not fail setup; destinations can still be written for manual or HTTP-based auth.

Lookup paths:

- JCo jars from user Eclipse / p2 plugin pools
- JCo native libraries: `sapjco3.dll`, `libsapjco3.so`, or `libsapjco3.dylib`
- CryptoLib: `sapcrypto.dll`, `libsapcrypto.so`, `libsapcrypto.dylib`, or SAP Secure Login install paths
- staged devcontainer runtime under `./.devcontainer/dist/` as fallback

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
- `files.name` entries that contain `/sap/bc/adt` → `adt.ashost` and `adt.discovery_url`

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
