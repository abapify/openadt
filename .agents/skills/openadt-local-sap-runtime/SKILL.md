---
name: openadt-local-sap-runtime
description: Setup, fetch, proxy, JCo/SNC/HTTP SSO on Windows, Linux, or macOS.
---

# Local SAP runtime

**Default path:** `AdtTransportFactory` → `SapSdkRuntime` → `SapDestinationResolver` → `AdtSdkTransportClient` when `runtime.adt_plugins_dir` is set.

**Platforms:** Windows `sapjco3.dll`, Linux `libsapjco3.so`, macOS `libsapjco3.dylib`. Run Java on the OS that owns those files.

## Destination

| Source | Id |
| --- | --- |
| Eclipse `.destination.properties` | e.g. `DEV_100_developer_en` |
| Config only | alias e.g. `DEV` |

Prefer Eclipse file when present — better SNC field parity.

## JCo jar name

Eclipse p2 `com.sap.conn.jco_3.1.13.jar` → required `com.sap.conn.jco-3.1.13.jar` (`JCoJarCanonicalizer`). Classpath: **one** core JCo, canonical jar **before** `jco.eclipse`.

## HTTP SSO (`transport=http`, `browser-sso`)

- `base_url` = corporate **frontend origin**, not app-server host from detect.
- Callback: `http://localhost:<port>/adt/redirect` — not `127.0.0.1` (SADT_RESOURCE 034).
- Do not open `/sap/bc/adt/discovery` in the browser.
- TLS: `runtime.http_ca_cert` or `OPENADT_HTTP_CA_CERT`.
- Ticket reuse: `OPENADT_MYSAPSSO2`.

Spec: [specs/config.md](../../../specs/config.md), [specs/cli.md](../../../specs/cli.md).

## Failure modes

| Symptom | Fix |
| --- | --- |
| `no sapjco3 in java.library.path` | `jco_native_dir` must match host OS; `openadt setup` on host |
| `SessionReferenceProvider` CNFE | Canonical core JCo first on classpath |
| `Illegal JCo archive` | Re-run bootstrap / canonicalizer |
| `Unrecognized field "default_profile"` | Rebuild/replace jar (profiles need current release) |
| HTTP 406 systeminformation | ADT Accept header / `--json` |
| `Invalid redirect URL` | Use `localhost` in redirect-url |
| `ERR_CONNECTION_REFUSED` on callback | Fresh fetch; fixed `--callback-port`; keep terminal open |
| `PKIX path building failed` | `http_ca_cert` PEM |
| Secure Login `Configuration []` | Use portal (`ssoURL`) profile, not hub UUID |

## Validate (local machine only — never paste output to repo)

```bash
openadt config
openadt auth login DEV
openadt fetch DEV /sap/bc/adt/core/http/systeminformation --json
```

`OPENADT_VERBOSE=true` for classpath/logon. Dev JAR: `scripts/openadt-sdk.ps1` (Windows).

WSL/devcontainer split: [openadt-devcontainer-host-runtime](../openadt-devcontainer-host-runtime/SKILL.md).
