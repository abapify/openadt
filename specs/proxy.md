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
| `X-CSRF-Token`               | CSRF protection              |
| `If-Match` / `If-None-Match` | ETags for optimistic locking |
| `Accept-Language`            | Language negotiation         |
| `SAP-Client`                 | SAP client selection         |

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
