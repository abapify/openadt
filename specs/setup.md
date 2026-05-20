# Setup Analyzer Specification

## Overview

The `openadt setup` command runs a series of detectors to discover SAP systems configured in local tooling, then writes the results to the OpenADT config file.

## SetupAnalyzer

Orchestrates all detectors and aggregates results:
1. Runs SapGuiLandscapeDetector
2. Runs SapBusinessClientDetector
3. Runs EclipseAdtDetector
4. Runs SecureLoginDetector (produces warnings, not system profiles)

Returns a `SetupResult` with:
- `systems` — list of discovered SystemProfile objects
- `warnings` — list of warning messages

## Detectors

### SapGuiLandscapeDetector

Reads the SAP GUI landscape XML file (SAPUILandscape.xml).

Lookup paths:
- **Windows**: `%APPDATA%\SAP\Common\SAPUILandscape.xml`
- **macOS**: `~/Library/Application Support/SAP/Common/SAPUILandscape.xml`

For each `<System>` entry, extracts:
- `name` → alias, description
- `server` → jco.ashost
- `systemid` → system_id
- `sysno` → jco.sysnr

Sets `source = "sapgui"`.

### SapBusinessClientDetector

Checks if SAP Business Client is installed.

Lookup paths:
- **Windows**: `%APPDATA%\SAP\SAP Business Client`
- **Windows**: `%ProgramFiles%\SAP\SAP Business Client`

If found, adds a placeholder system profile with `source = "sap-business-client"`.

### EclipseAdtDetector

Reads Eclipse workspace ADT connection settings.

Lookup paths:
- `~/eclipse-workspace/.metadata/.plugins/org.eclipse.core.runtime/.settings/com.sap.adt.tools.core.prefs`

Parses connection data to extract system profiles. Sets `source = "eclipse-adt"`.

### SecureLoginDetector

Probes the SAP Secure Login Client local security hub at `https://127.0.0.1:34443`.

Does not produce system profiles. Adds a warning if not reachable:
> "SAP Secure Login Client not detected at https://127.0.0.1:34443"

Timeout: 2 seconds.
