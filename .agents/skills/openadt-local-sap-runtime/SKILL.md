---
name: openadt-local-sap-runtime
description: Use when working on OpenADT setup, fetch, proxy, or live SAP validation against a real local SAP GUI, NWBC, JCo, SNC, and Secure Login installation.
---

# OpenADT Local SAP Runtime

Knowledge base for making **fetch** and **proxy** work like Eclipse ADT on a real Windows (or WSL-detected) SAP stack.

## MVP CLI surface

Production commands only:

- `openadt setup` — detect landscape, runtime, Secure Login; write config fragments
- `openadt fetch <SYSTEM> <path>` — single ADT request
- `openadt proxy <SYSTEM>` — local HTTP proxy for IDE clients

Both fetch and proxy use **`AdtTransportFactory` → `AdtSdkTransportClient`** when `runtime.adt_plugins_dir` is set (default transport `sdk`).

Do not reintroduce `openadt sdk probe/debug` into the main CLI; use `OPENADT_VERBOSE=true` and JCo trace env vars for diagnosis.

## Architecture (what actually works)

```text
fetch / proxy
    → AdtTransportFactory
    → SapSdkRuntime.prepare()
         JCoRuntimeBootstrap (natives + sapcrypto)
         AdtCommunicationBootstrap → JCoEclipseBootstrap (headless jco.eclipse)
         SecureLoginBootstrap (hub Web Adapter LOGGED_IN)
    → SapDestinationResolver
         1) EclipseDestinationLocator: .metadata/.../semantic/.cache/<id>/.destination.properties
         2) else build from config [destinations.<alias>]
    → AdtLogonService.ensureLoggedOn
    → AdtSystemSession.sendRequest (JCo-backed, not HTTP+MYSAPSSO2)
```

Eclipse ADT in the IDE uses the same SDK path (JCo destination registry), not `transport=http` with cookies.

## Destination resolution

| Source | When | Destination id |
|--------|------|------------------|
| Eclipse | Workspace has `.destination.properties` matching system SID | e.g. `DEV_100_developer_en` |
| Config | No Eclipse file | alias from config e.g. `DEV` |

**Critical:** Using only config alias without Eclipse file often breaks SSO/SNC field parity. Prefer Eclipse file when the user has ADT in Eclipse.

## Never commit private landscape data (mandatory)

When editing this repo — tests, specs, skills, README, examples:

- **Do not** copy SIDs, usernames, logon groups, hostnames, SNC DNs, Secure Login UUIDs, or discovery URLs from the developer machine, live CLI output, or `~/.openadt/config.toml`.
- **Use only** fictional fixtures: `DEV`, `DEVELOPER`, `PUBLIC`, `dev-ms.example.com`, `sap-dev-app.example.com`, `DEV_100_developer_en`, `p:CN=SAPServiceDEV`, fake UUIDs like `11111111-1111-1111-1111-111111111111`.
- **Delete** tests that read `user.home` workspace or registry for real system names; keep those behind `@Tag("integration")` without asserting org-specific values.
- Live validation uses the user's **private** config locally — never paste results into the repo.

`EclipseDestinationLoader` must clone writable destination data correctly (`getWritable()` + copy fields). `SNCType` code `9` maps to `SNC_HIGHEST_AVAILABLE` via `getByCode(9)`, not `valueOf("9")`.

## JCo jar naming (non-negotiable)

SAP JCo rejects Eclipse p2 file names:

| Wrong (p2) | Required on disk |
|------------|------------------|
| `com.sap.conn.jco_3.1.13.jar` | `com.sap.conn.jco-3.1.13.jar` |

- `JCoJarCanonicalizer` copies/renames into `%TEMP%/openadt-jco-lib/`
- `RuntimeDetector` stores canonical path in config
- **`scripts/openadt-sdk.ps1`**: put **canonical core JCo jar before** all other SAP jars; `jco.eclipse` activator needs `com.sap.conn.jco.ext.SessionReferenceProvider` from core JCo

Classpath must include exactly **one** core JCo version; exclude duplicate `com.sap.conn.jco_3.1.*` from the non-core slot.

Also need `com.sap.conn.jco.eclipse_*.jar` and ADT bundles from `~/.p2/pool/plugins` or `target/sap-lib`.

## Headless SDK outside Eclipse

`JCoEclipseBootstrap` reflectively starts `com.sap.mw.jco3.eclipse.internal.Activator`, registers JCo `Environment` providers, sets `successfullJCORegistration=true` so `JCoDestinationRegistry` activates.

`AdtCommunicationBootstrap` starts `com.sap.adt.communication.internal.Activator`.

Stub `org.eclipse.core.net.proxy.IProxyService` may be required for class loading (minimal no-op implementation).

## Secure Login Web Adapter

Two registry profiles are common:

- **Hub** (`enrollURL0`) — enrollment
- **Portal** (`ssoURL`) — MFA browser / SSO portal

For JCo SNC SSO, `MfaUrlResolver` / portal flows must use the profile tied to **`ssoURL`**, not the hub profile. Wrong profile → `Configuration [] not found in [SecureLoginServer/SecureLoginWebClientSettings]`.

`SecureLoginBootstrap.prepareForJco`:

- Ensures hub Web Adapter `LOGGED_IN` before `ensureLoggedOn`
- Does **not** open browser on every fetch when already `LOGGED_IN`
- Hub may open browser for MFA when `OPENADT_HUB_BROWSER_MONITOR` is enabled

Browser is for Secure Login MFA, not for ADT discovery URL.

## ADT HTTP details

- Default `Accept` in `AdtSdkTransportClient` covers feeds/XML; **`/sap/bc/adt/core/http/systeminformation`** needs `application/vnd.sap.adt.core.http.systeminformation.v1+json` or HTTP 406
- Live acceptance test: `GET /sap/bc/adt/core/http/systeminformation` → 200 JSON with `systemID`, `userName`, `client`

## Config / runtime fields

See `specs/config.md`. Important runtime keys:

- `runtime.adt_plugins_dir` — Eclipse p2 plugins dir (enables SDK transport)
- `runtime.jco_jar` — canonicalized JCo jar path
- `runtime.jco_native_dir`, `runtime.sapcrypto`
- `secure_login.local_security_hub`, `secure_login.web_adapter_profile_id`, `secure_login.origin`

Transport values: `sdk` (default), `rest-rfc` (legacy RFC bridge), `http` (MYSAPSSO2 + discovery_url only).

## Validation commands (agent runs these)

```powershell
cd apps/openadt-cli && mvn test
mvn package -DskipTests
$env:OPENADT_VERBOSE = "true"
# Use the user's real alias from ~/.openadt/config.toml — never hardcode in repo files:
# scripts/openadt-sdk.ps1 fetch <ALIAS> /sap/bc/adt/core/http/systeminformation --json -f
```

Use the **host OS Java** that matches installed `sapjco3.dll` / `sapcrypto.dll`.

## Known failure modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| `SessionReferenceProvider` CNFE | Core JCo not on classpath before jco.eclipse | Reorder classpath; canonical JCo first |
| Illegal JCo archive | Wrong jar file name | `JCoJarCanonicalizer` |
| `jco.eclipse not initialized` | Missing `JCoEclipseBootstrap` | Call via `SapSdkRuntime` |
| JCo destination registry inactive | Eclipse flag false | Set `successfullJCORegistration` after manual register |
| `Configuration []` on SLS portal | Wrong Secure Login profile UUID | Use ssoURL portal profile |
| HTTP 406 systeminformation | Wrong Accept | Built-in default or `-H` |
| `ensureLoggedOn` timeout | Often classpath/plugin init, not “needs browser” | Verbose logs, fix JCo/SDK init first |
| fetch works, proxy fails | Should not happen if both use factory | Check same config path and system alias |

## Repository layout (post-monorepo)

- CLI: `apps/openadt-cli/src/main/java/org/openadt/`
- Core transport: `core/AdtSdkTransportClient.java`, `SapSdkRuntime.java`, `SapDestinationResolver.java`
- Setup detectors: `setup/*Detector.java`, `EclipseDestinationLocator.java`
- Specs: `specs/cli.md`, `specs/proxy.md`, `specs/config.md`, `specs/setup.md`
- Dev runner: `scripts/openadt-sdk.ps1`

Never commit: JCo jars, sapcrypto, Secure Login binaries, private hostnames, cookies, `tmp/` scratch outside gitignore.

## WSL note

Detectors can read `/mnt/c/Users/...` SAP GUI/NWBC data, but **JCo/SNC must run on the OS that owns the native DLLs**. See `openadt-devcontainer-host-runtime` skill.
