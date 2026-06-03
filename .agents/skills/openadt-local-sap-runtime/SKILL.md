---
name: openadt-local-sap-runtime
description: Use when working on OpenADT setup, fetch, proxy, or live SAP validation on Windows, Linux, or macOS — with or without the full JCo/SNC/Secure Login stack.
---

# OpenADT Local SAP Runtime

Knowledge base for **fetch** and **proxy** via the **SAP ADT SDK** (default). Bootstrap/setup only writes `~/.openadt/config.toml`; runtime commands do not rescan the machine. HTTP ticket and localhost proxy auth are valid when SDK transport is unavailable.

**Platforms:** Windows (`sapjco3.dll`), Linux (`libsapjco3.so`), macOS (`libsapjco3.dylib`). JCo + `sapcrypto` + Secure Login are **optional** unless the destination uses SNC SSO or SDK transport.

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

Transport values: `sdk` (default), `rest-rfc` (legacy RFC bridge), `http` (MYSAPSSO2 + `base_url`).

## Destination profiles (multi-auth per alias)

Config supports **one alias, multiple auth profiles** under `destinations.<ALIAS>.profiles.<NAME>`:

| Field | Purpose |
|-------|---------|
| `default_profile` | Used when `--profile` is omitted |
| `profiles.<name>.transport` | `sdk` or `http` |
| `profiles.<name>.authentication_kind` | e.g. `sso` (SNC/JCo) or `browser-sso` (HTTP ticket) |
| `profiles.<name>.base_url` | SAP frontend origin for browser SSO and ADT (`/sap/bc/adt` appended for API calls) |
| `profiles.<name>.callback_port` | Local callback port (`0` = random) |

CLI: `openadt fetch DEV /sap/bc/adt/... --profile=sso`

Resolver: `DestinationProfileResolver` merges profile fields into an effective `SystemProfile` without mutating loaded config.

Manual profile writes go to `~/.openadt/destinations/manual.openadt.toml` via `openadt config destinations create`.

**Installed vs dev build:** Scoop releases ship a **released** JAR. Config with `default_profile` / `profiles` requires a build that includes destination profiles (PR branch or post-release). Symptom on old JAR:

```text
Unrecognized field "default_profile" (class org.openadt.config.SystemProfile)
```

Fix: rebuild from repo and replace the installed `openadt.jar`, or wait for the next release and `scoop update openadt`.

## HTTP browser SSO transport (`transport=http`, `browser-sso`)

Use when ADT is exposed on an **HTTPS ICF frontend** with corporate SAML/Okta, without JCo/SNC.

```text
fetch / proxy (--profile=sso)
    → HttpAdtTransportClient.sendOnce (target ADT URL)
         1) disk cache (~/.openadt/cache/http-sso/) → MYSAPSSO2 + session cookies
         2) on 401 or cache miss → AdtHttpCookieProvider.resolveMysapsso2()
              OPENADT_MYSAPSSO2 / secure_login.mysapsso2 / OPENADT_COOKIE_FILE
              else AdtHttpReentranceTicketFlow.acquireTicket():
                   start localhost /adt/redirect server
                   step 1 (no cookies): base_url or OPENADT_HTTP_BASE_URL (Okta) — no redirect-url
                   step 2: …/reentranceticket?redirect-url=http://localhost:…/adt/redirect?state=…
                   (SAP/Okta redirects back with reentrance-ticket=…)
                   cache ticket + server-side discovery warmup cookies
         3) retry target fetch
    → Cookie: MYSAPSSO2=<ticket>
    → discoverAdtApiBase (well-known / virtualhost) + ADT request
```

There is **no** `localhost/adt/open` launch page. **Do not** open `/sap/bc/adt/discovery` in the browser.

Secure Login Web Adapter prepares **JCo/SNC** credentials; it does **not** replace `MYSAPSSO2` for direct HTTP unless you supply the ticket explicitly.

### base_url must be the SAP frontend origin

For landscapes where Okta/SAML is on the **site root** but deep ICF paths use SAP HTTP auth:

- **Wrong:** app-server host from `setup` detect (e.g. `sap-<sid>-app.example.com`) — reentranceticket may show HTTP Basic.
- **Right:** corporate **frontend** origin users hit in the browser (e.g. `https://<frontend>.example.com`). OpenADT appends `/sap/bc/adt` for ADT API calls.

Write the SSO profile manually when auto-detect picks the app server:

```bash
openadt config destinations create --alias DEV --profile sso \
  --transport http --auth browser-sso \
  --base-url https://<frontend>.example.com
```

### Reentrance-ticket redirect URL rules

SAP validates `redirect-url` on `/sap/bc/adt/core/http/reentranceticket`:

| redirect-url host | Result |
|-------------------|--------|
| `http://localhost:<port>/adt/redirect` | Accepted (Eclipse ADT pattern) |
| `http://127.0.0.1:<port>/adt/redirect` | Often **SADT_RESOURCE 034 — Invalid redirect URL** |

Callback server binds to the same host as `redirect-url` (default `localhost`).

Override: `OPENADT_HTTP_CALLBACK_HOST` or `runtime.http_callback_host`.

### SSO browser flow

On cache miss or HTTP 401:

1. Start localhost callback (`/adt/redirect`).
2. **Step 1** (cold browser): `base_url` origin or `OPENADT_HTTP_BASE_URL` (Okta SAML app URL) — sign in only, no `redirect-url`. **Step 2**: `…/reentranceticket?redirect-url=localhost…`. Without step 1, reentrance-ticket often shows HTTP Basic. Press Enter between steps when interactive.
3. Complete IdP/Okta in the browser; SAP redirects to localhost with `reentrance-ticket=…`.
4. Cache ticket; server-side `GET /sap/bc/adt/discovery` for session cookies (never in the browser).

**`base_url`** is the SAP frontend **origin** only (not `/sap/bc/adt` in the browser). Override with `OPENADT_HTTP_BASE_URL` when the IdP entry URL differs.

Legacy opt-in `OPENADT_HTTP_SSO_OPEN_BRIDGE=1` may open an extra `/sap/bc/adt` tab — off by default and not part of the normal flow.

### Callback timing and `ERR_CONNECTION_REFUSED`

The localhost callback exists **only while `fetch`/`proxy` is waiting** for the ticket.

| Symptom | Cause | Fix |
|---------|-------|-----|
| `ERR_CONNECTION_REFUSED` on `localhost:<port>/adt/redirect` | Port from an **earlier run**; process exited | Start fresh fetch; use only the callback URL from **this** terminal |
| Redirect after terminal already finished | Timeout or user too slow | Increase `OPENADT_HTTP_CALLBACK_TIMEOUT_MINUTES`; use `--callback-port` fixed port |
| Ticket visible in browser URL but refused | Same — stale port | Copy `reentrance-ticket=` value → `OPENADT_MYSAPSSO2` and retry |

Active callback registry (while waiting): `~/.openadt/runtime/sso-callback.json` (`callbackUrl`, `port`, `pid`).

After ticket received, callback stays up **30s** (grace) so late redirects still get 200. The success HTML page tries `window.close()`; if the browser blocked popups or used `Desktop.browse`, close the tab manually.

**Recommendation:** fixed callback port for repeat use: `--callback-port 63363` or `callback_port = "63363"` in profile.

### Ticket recovery without browser

If the browser already shows `reentrance-ticket=...` in the address bar:

```powershell
$env:OPENADT_MYSAPSSO2 = "<ticket-from-url>"
openadt fetch DEV /sap/bc/adt/... --profile=sso
```

(URL-decode `%3d` → `=` at end of ticket.)

### HTTP TLS (corporate CA)

Java default trust store may not include internal CAs. PowerShell/`Invoke-WebRequest` can work while Java fails with `PKIX path building failed`.

Fix:

```toml
# ~/.openadt/local.openadt.toml
[runtime]
http_ca_cert = "C:\\path\\to\\frontend.pem"
```

Or env: `OPENADT_HTTP_CA_CERT`. Export server cert from a successful TLS handshake on the host (never commit the PEM to the repo).

### HTTP SSO env vars

| Variable | Purpose |
|----------|---------|
| `OPENADT_MYSAPSSO2` | Skip browser; use ticket directly |
| `OPENADT_HTTP_CA_CERT` | PEM/DER CA for HTTPS ADT |
| `OPENADT_HTTP_CALLBACK_PORT` | Callback bind port (`0` = random) |
| `OPENADT_HTTP_CALLBACK_HOST` | Callback hostname in redirect-url (default `localhost`) |
| `OPENADT_HTTP_CALLBACK_TIMEOUT_MINUTES` | Wait for browser redirect (default 5) |
| `OPENADT_HTTP_SSO_NON_INTERACTIVE` | Skip Enter prompts |
| `OPENADT_HTTP_SSO_BRIDGE_WAIT_SECONDS` | Delay before reentranceticket when no console (default 15); `0` also skips opening the bridge tab |
| `OPENADT_HTTP_SSO_SKIP_BRIDGE` | Do not open bridge tab (warm ADT session in browser) |
| `OPENADT_HTTP_SSO_SKIP_LANDING` | Skip `base_url` landing on `/sap/bc/` frontends |
| `OPENADT_HTTP_BASE_URL` | Override `destinations.*.adt.base_url` for browser landing |

### HTTP transport implementation notes

- `HttpAdtTransportClient` **caches** `MYSAPSSO2` for one client instance — discovery lookups must not re-trigger browser SSO three times per request.
- `AdtHttpReentranceTicketFlow.buildReentranceTicketUrl()` — query params: `sap-client`, `sap-language`, `redirect-url`, `_`.
- Callback path: `/adt/redirect`, query param `reentrance-ticket`.

## Validation commands (agent runs these)

```powershell
cd apps/openadt-cli && ./mvnw test
./mvnw package -DskipTests
$env:OPENADT_VERBOSE = "true"
# HTTP SSO (use user's alias/profile from ~/.openadt — never hardcode in repo):
# $env:OPENADT_HTTP_CA_CERT = "<user-local-pem>"
# java -jar apps/openadt-cli/target/openadt-*.jar fetch DEV /sap/bc/adt/core/http/systeminformation --profile=sso -A application/vnd.sap.adt.core.http.systeminformation.v1+json
# SDK/SNC:
# scripts/openadt-sdk.ps1 fetch <ALIAS> /sap/bc/adt/core/http/systeminformation --json -f
```

When validating HTTP SSO, **run fetch yourself** on the user's machine — do not only instruct the user to retry. Verify: config parses, callback URL uses `localhost`, TLS with `http_ca_cert`, and discovery/systeminformation returns 200.

Replace Scoop-installed JAR during dev:

```powershell
Copy-Item apps/openadt-cli/target/openadt-*.jar $env:USERPROFILE/scoop/apps/openadt/<version>/openadt.jar -Force
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
| `Unrecognized field "default_profile"` | Installed JAR older than destination-profiles feature | Rebuild/replace `openadt.jar` or `scoop update` after release |
| `Invalid redirect URL` (SADT_RESOURCE 034) | `redirect-url` uses `127.0.0.1` | Use `localhost` (default) |
| `ERR_CONNECTION_REFUSED` on callback | Stale port / fetch exited | Fresh run; `--callback-port`; keep terminal open |
| `PKIX path building failed` | Corporate CA not in Java trust | `runtime.http_ca_cert` / `OPENADT_HTTP_CA_CERT` |
| HTTP Basic on `/sap/bc/adt/discovery` in browser | OpenADT or user opened Atom discovery URL | Never open discovery in browser; use `base_url` landing then reentranceticket; server-side warmup only |
| Three browser SSO popups | Old bug: discovery re-acquired ticket | Fixed: `HttpAdtTransportClient` caches cookie |
| Secure Login `LOGGED_OUT` with default profile | `default_profile=snc` needs hub | Use `--profile=sso` for HTTP or log in Web Adapter |
| `no sapjco3 in java.library.path` | `runtime.jco_native_dir` points at the wrong OS (e.g. `.devcontainer/dist/jco` with `libsapjco3.so` while Java runs on Windows) | Run `openadt setup --check` on the **host**; confirm the directory contains `sapjco3.dll` (Windows), `libsapjco3.so` (Linux), or `libsapjco3.dylib` (macOS); save with `openadt setup` |

**Agent triage before blaming SAP logon:** on `UnsatisfiedLinkError` / missing `sapjco3`, inspect `jco_native_dir` first — do not loop on fetch retries with a stale devcontainer path.

## Repository layout (post-monorepo)

- CLI: `apps/openadt-cli/src/main/java/org/openadt/`
- Core transport: `core/AdtSdkTransportClient.java`, `SapSdkRuntime.java`, `SapDestinationResolver.java`
- Setup detectors: `setup/*Detector.java`, `EclipseDestinationLocator.java`
- Specs: `specs/cli.md`, `specs/proxy.md`, `specs/config.md`, `specs/setup.md`
- Dev runner: `scripts/openadt-sdk.ps1`

Never commit: JCo jars, sapcrypto, Secure Login binaries, private hostnames, cookies, `tmp/` scratch outside gitignore.

## WSL note

Detectors can read `/mnt/c/Users/...` SAP GUI/NWBC data, but **JCo/SNC must run on the OS that owns the native DLLs**. See `openadt-devcontainer-host-runtime` skill.
