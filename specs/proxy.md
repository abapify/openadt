# Proxy Server Specification

## Overview

OpenADT runs a local HTTP proxy server that intercepts ADT requests from Eclipse/IDE clients and forwards them to the SAP backend through the configured ADT transport.

Default transport:

- ADT SDK destination/session stack (`transport = "sdk"`)

Fallback transports:

- RFC bridge via `SADT_REST_RFC_ENDPOINT` (`transport = "rest-rfc"`)
- Direct HTTP against the ICF/SAML frontend (`transport = "http"`) using `MYSAPSSO2` and `adt.base_url`

## Security Model

The proxy sits between the IDE client and SAP. It:

1. Authenticates the IDE client (optional Basic auth)
2. Strips all SAP authentication headers from incoming requests
3. Authenticates to SAP using the selected ADT transport runtime
4. Returns responses from SAP to the IDE client

## Headers Stripped from Incoming Requests

These headers are stripped before forwarding to SAP:

| Header                    | Reason                                     |
| ------------------------- | ------------------------------------------ |
| `Authorization`           | IDE basic/token auth must not reach SAP    |
| `X-SAP-LogonToken`        | SAP logon token — re-authenticated via JCo |
| `X-SAP-Reentrance-Ticket` | SAP reentrance ticket — not needed         |
| `SAP-SNC-Token`           | SNC token — handled by JCo                 |
| `Cookie`                  | Session cookies — not applicable           |
| `Set-Cookie`              | Response cookies — not forwarded           |

## Headers Preserved

These ADT-specific headers are passed through:

| Header                       | Reason                       |
| ---------------------------- | ---------------------------- |
| `Accept`                     | Content negotiation          |
| `Content-Type`               | Request body type            |
| `X-CSRF-Token`               | CSRF handshake (see below)    |
| `If-Match` / `If-None-Match` | ETags for optimistic locking |
| `Accept-Language`            | Language negotiation         |
| `SAP-Client`                 | SAP client selection         |

## Stateful ADT request sequences

An ADT client starts a stateful sequence by sending
`X-sap-adt-sessiontype: stateful`. For an SDK transport, the proxy creates one
opaque, HttpOnly, loopback-only OpenADT cookie and maps it to one SDK
`IStatefulSystemSession`. Requests that return that cookie use the same SDK
session until either:

- the ADT request carries `_action=UNLOCK`,
- the client explicitly requests `X-sap-adt-sessiontype: stateless`, or
- the proxy stops.

The proxy does not forward this cookie to SAP, nor does it forward SAP response
cookies. Non-stateful requests keep using the stateless SDK session path. This
preserves the backend lock context for `LOCK → PUT → UNLOCK` without exposing
SAP authentication/session material to the local client.

## CSRF handshake

SAP clients obtain a CSRF token by sending `GET` or `HEAD` with `X-CSRF-Token: fetch` and reading the `x-csrf-token` response header, then attaching it to writes. A client treats an absent, empty, or literal `Required` value as "no token issued" and aborts before it ever issues its write.

**The proxy answers the handshake itself, but only for the SDK transport.** A transport advertises this
through `AdtTransportClient.managesCsrfUpstream()`, which defaults to `false`; only
`AdtSdkTransportClient` returns `true`.

| Request | Behavior (SDK transport) |
| ------- | -------- |
| `HEAD` with `X-CSRF-Token: fetch` | answered locally: `200` plus an `x-csrf-token` header, no upstream call |
| `GET` with `X-CSRF-Token: fetch` | forwarded normally; `x-csrf-token` is set only when the response carries no usable one, so the body is preserved |
| anything else — including a write tagged `fetch` | untouched; no synthetic token is added |

A token counts as usable when it is present, non-blank, and not the literal `Required`. SAP sends
`Required` to mean "fetch a token", and a client reads that value as no token at all, so relaying it
verbatim fails the handshake exactly as an absent header would.

Two reasons the token is minted locally rather than relayed:

- The ADT SDK maintains its own authenticated session and handles CSRF upstream, so a token issued here is never validated against anything. A write carrying an arbitrary token already succeeds.
- Upstream answers `HEAD` with `400`, so relaying the handshake would fail. `GET` on the same path returns `200`, which places the status upstream rather than in the proxy; `AdtSdkTransportClient` logs the upstream status under `OPENADT_VERBOSE` so this stays checkable.

Neither reason holds for the `rest-rfc` and `http` transports: those forward the client's token to SAP,
so a locally minted one would be rejected on the first write. For them the handshake is passed through
untouched — including upstream's `HEAD` status — which is their pre-existing behavior.

Inbound `X-CSRF-Token` is still forwarded on writes (see Headers Preserved); the SDK ignores it.

**The proxy therefore provides no CSRF protection.** The controls are that the listener is loopback-bound and that access is gated by the proxy's own authentication mode below, not by CSRF. This is unchanged behavior — the token was never validated — but it is now stated.

## Bodyless responses

`com.sun.net.httpserver` requires a response length of `-1` when no body is sent. The proxy uses it for a `HEAD` reply, `204`, `304`, and any empty body; passing a length there (including `0`, which selects chunked encoding) announces framing the server cannot then satisfy.

`HEAD` on a proxied path still returns whatever SAP returns, which today is `400` for paths other than the CSRF handshake. Only the handshake is short-circuited; general upstream `HEAD` support is out of scope.

## Authentication Modes

### None (default)

No authentication required on the proxy. Suitable for local development.

### Basic

The proxy requires HTTP Basic authentication. Configure in config.toml:

```toml
[proxy]
auth = "basic"
username = "developer"
```

The password is not stored in config — it is prompted or read from a secrets manager.

## Transport Modes

### SDK (default)

OpenADT registers an ADT destination, ensures logon through `AdtLogonServiceFactory`, creates a stateless system session, and sends ADT HTTP-like requests through the SAP ADT SDK.

Implementation touchpoints:

- `AdtTransportFactory` — selects SDK when `runtime.adt_plugins_dir` is set and transport is not `http` or `rest-rfc`
- `SapSdkRuntime` — JCo natives, `JCoEclipseBootstrap`, `AdtCommunicationBootstrap`, `SecureLoginBootstrap`
- `SapDestinationResolver` — Eclipse `.destination.properties` by SID, else config-built destination
- `AdtSdkTransportClient` — shared by `openadt fetch` and `openadt proxy`

### RFC Bridge

The legacy fallback uses RFC function `SADT_REST_RFC_ENDPOINT` to forward HTTP requests to SAP ADT.

RFC request structure:

- `REQUEST.REQUEST_LINE.METHOD` — HTTP method
- `REQUEST.REQUEST_LINE.URI` — URL path
- `REQUEST.REQUEST_LINE.VERSION` — HTTP version
- `REQUEST.HEADER_FIELDS[]` — Table of NAME/VALUE pairs
- `REQUEST.MESSAGE_BODY` — Request body bytes

Response structure:

- `RESPONSE.STATUS_LINE.STATUS_CODE` — HTTP status code
- `RESPONSE.STATUS_LINE.REASON_PHRASE` — Reason phrase
- `RESPONSE.STATUS_LINE.VERSION` — HTTP version
- `RESPONSE.HEADER_FIELDS[]` — Table of NAME/VALUE pairs
- `RESPONSE.MESSAGE_BODY` — Response body bytes
