# OpenADT — Codacy AI Reviewer instructions

## Purpose

OpenADT is a CLI and local proxy for SAP ADT development. It can obtain ADT sessions via JCo/SNC or browser SSO (`--profile sso`) using a localhost callback and optional loopback Secure Login hub TLS pinning.

## Architecture

- `apps/openadt-cli/` — Java CLI (Maven): core SAP/HTTP flows, proxy, setup.
- `apps/openadt-proxy/` — .NET proxy (separate solution).
- `specs/` — behavior contracts; change specs when CLI behavior changes.

## Security review context (read before flagging SSRF / local bind issues)

### Loopback-only SSRF false positives (Opengrep / Semgrep)

These patterns are **intentional** and must not be treated as exploitable SSRF:

1. **SSO callback HTTP server** (`AdtHttpReentranceTicketFlow`)
   - Browser SSO opens SAP, then redirects to `http://127.0.0.1:<port>/adt/redirect` (or `localhost` / `::1`).
   - Configured callback **host** is validated with `InetAddress.getByName` and **must** resolve to a loopback address before use.
   - The server **binds** only to `InetAddress.getLoopbackAddress()` (not arbitrary resolved names).
   - Query parsing tolerates SAP frontends that append a second `?` before `reentrance-ticket` / cache-busters.

2. **Secure Login hub TLS probe** (`LoopbackHubTlsProbe`)
   - One-shot probe to capture the self-signed cert from `https://127.0.0.1:<port>` (or loopback) for KeyStore pinning in `SecureLoginHubClient`.
   - Connects only to `InetAddress.getLoopbackAddress()`; invalid ports are rejected.
   - Trust-all logic is isolated to the probe; production clients use a pinned KeyStore.

3. **Tests**
   - Unit/integration tests often start `com.sun.net.httpserver.HttpServer` on `127.0.0.1` or ephemeral loopback ports to simulate callbacks or hub stubs.
   - Example: `AdtHttpReentranceCallbackIntegrationTest`, `SecureLoginHubClientTest`, `LocalProxyRegistryTest`.

### What to still flag

- Any outbound connection where host/port/scheme come from **unvalidated user or remote input** (not loopback).
- Trust-all `TrustManager` / disabled TLS verification **outside** `LoopbackHubTlsProbe`.
- Logging or committing credentials, tickets, cookies, or real landscape identifiers.

## Fixtures (fictional only)

Use and expect fictional values in docs/tests: SID `DEV`, client `100`, user `DEVELOPER`, host `dev-ms.example.com`, SNC `p:CN=SAPServiceDEV`. Do not infer or request real customer hostnames, SIDs, or secrets from the reviewer or repo.

## Testing

- Java: `./mvnw.cmd -q test` under `apps/openadt-cli`.
- SAP/JCo integration tests are `@Tag("integration")` and skipped by default.

## PR expectations

- No real SAP landscape data in commits.
- SSO/callback changes should include unit or integration coverage under `apps/openadt-cli/src/test/java/`.
- Prefer fixing loopback validation in code over blanket file suppressions when a finding is clearly a false positive.
