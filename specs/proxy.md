# Proxy Server Specification

## Overview

OpenADT runs a local HTTP proxy server that intercepts ADT requests from Eclipse/IDE clients and forwards them to the SAP backend via RFC (SADT_REST_RFC_ENDPOINT), using JCo for authentication (SNC/SSO or username+password).

## Security Model

The proxy sits between the IDE client and SAP. It:
1. Authenticates the IDE client (optional Basic auth)
2. Strips all SAP authentication headers from incoming requests
3. Authenticates to SAP using JCo/SNC (no passwords in transit)
4. Returns responses from SAP to the IDE client

## Headers Stripped from Incoming Requests

These headers are stripped before forwarding to SAP via RFC:

| Header | Reason |
|--------|--------|
| `Authorization` | IDE basic/token auth must not reach SAP |
| `X-SAP-LogonToken` | SAP logon token — re-authenticated via JCo |
| `X-SAP-Reentrance-Ticket` | SAP reentrance ticket — not needed |
| `SAP-SNC-Token` | SNC token — handled by JCo |
| `Cookie` | Session cookies — not applicable |
| `Set-Cookie` | Response cookies — not forwarded |

## Headers Preserved

These ADT-specific headers are passed through:

| Header | Reason |
|--------|--------|
| `Accept` | Content negotiation |
| `Content-Type` | Request body type |
| `X-CSRF-Token` | CSRF protection |
| `If-Match` / `If-None-Match` | ETags for optimistic locking |
| `Accept-Language` | Language negotiation |
| `SAP-Client` | SAP client selection |

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

## RFC Function

The proxy uses RFC function `SADT_REST_RFC_ENDPOINT` to forward HTTP requests to SAP ADT.

Request structure:
- `REQUEST.METHOD` — HTTP method
- `REQUEST.PATH` — URL path
- `REQUEST.BODY` — Request body bytes
- `HEADERS` — Table of NAME/VALUE pairs

Response structure:
- `RESPONSE.STATUS_CODE` — HTTP status code
- `RESPONSE.REASON` — Reason phrase
- `RESPONSE.BODY` — Response body bytes
- `HEADERS` — Table of NAME/VALUE pairs
