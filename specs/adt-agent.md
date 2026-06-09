# OpenADT agent foundation — CLI subcommands and `openadt-mcp-agent` MCP server for LSP operation coverage

## Purpose & scope

OpenADT exposes the operations an autonomous agent needs to drive ABAP development end-to-end — lock/unlock, toggle version, ATC, format, references, diagnostics, quick search, transport, application run, hover, symbols, coverage, and repository metadata — by wrapping the same `com.sap.adt.*` SDK the LSP extensions wrap. This surface **complements** the SAP MCP server (which already covers activation, ABAP Unit, and object creation); it does not replace it. The verbs in this spec are the **18 catalog items from [`specs/lsp-operations-catalog.md`](lsp-operations-catalog.md) that are not already in the SAP MCP** (the other 6 are explicitly re-expressed below as "do not reimplement — use the SAP MCP `abap_*` tool").

## Surface

Two surfaces, both backed by the same Java service in `org.openadt.sap.adt.agent.*`:

| Surface | Form                                            | Mount point                                           | Port default | Endpoint store                               | Auth                                                  |
| ------- | ----------------------------------------------- | ----------------------------------------------------- | ------------ | -------------------------------------------- | ----------------------------------------------------- |
| CLI     | `openadt adt <verb>` picocli subcommands        | Local shell, one-shot invocation                      | n/a          | n/a                                          | n/a (uses SDK logon session)                          |
| MCP     | `openadt-mcp-agent serve` HTTP MCP server (Bun) | `POST http://localhost:2237/mcp` (`--port` overrides) | `2237`       | `~/.openadt/mcp-agent/endpoints/<port>.json` | `Authorization: Bearer <token>` (generated if absent) |

The CLI surface is **always available** when the `openadt` Java jar is on PATH. The MCP surface is an **optional second server** packaged separately; it does not modify, fork, or replace the SAP MCP server described in [`specs/mcp.md`](mcp.md).

## Configuration

- **No new keys for v1.** All destinations, profiles, and auth settings are read from `~/.openadt/config.toml` per [`specs/config.md`](config.md).
- **Transport:** `adt.transport = "sdk"` (or unset, with `runtime.adt_plugins_dir` configured) is **required**. `http` and `rest-rfc` are not supported for these verbs. The CLI rejects non-SDK transport with `SDK_TRANSPORT_REQUIRED` (see [Error codes](#error-codes) below and [`apps/openadt-cli/src/main/java/org/openadt/cli/AdtCommandSupport.java:45`](../apps/openadt-cli/src/main/java/org/openadt/cli/AdtCommandSupport.java)).
- **MCP server flags (`openadt-mcp-agent serve`):**

  | Flag               | Default                                 | Meaning                                                                             |
  | ------------------ | --------------------------------------- | ----------------------------------------------------------------------------------- |
  | `--port`           | `2237`                                  | HTTP MCP listen port. **Distinct from the SAP server's `2236` to avoid collision.** |
  | `--token`          | (generated, 16 random bytes, base64url) | Bearer token for HTTP MCP. Stored in the endpoint store; redacted in logs.          |
  | `--endpoint-store` | `~/.openadt/mcp-agent/endpoints/`       | Endpoint store directory. **Distinct from `~/.openadt/mcp/endpoints/`.**            |
  | `--import-from`    | `openadt`                               | Destinations import source — same options as the SAP server.                        |
  | `--logon-timeout`  | `300`                                   | Seconds for SDK logon.                                                              |
  | `--verbose`        | off                                     | Debug logging.                                                                      |

The MCP server **does not spawn `adt-lsc`**. It talks to the local Java product over a UNIX socket / named pipe (e.g. `~/.openadt/mcp-agent/bridge.sock`) — the `openadt adt bridge` subcommand opens that socket and proxies each call into the corresponding Java service. The MCP server stays a thin shell.

## Command reference

The 24 catalog rows below are the contract. Six are marked **Do not reimplement** (use the corresponding `abap_*` tool from the SAP MCP server instead); the remaining 18 are the verbs OpenADT implements.

| #   | Verb                         | Syntax                                                                   | CLI subcommand                   | MCP tool                              | Priority               | Status                                                               |
| --- | ---------------------------- | ------------------------------------------------------------------------ | -------------------------------- | ------------------------------------- | ---------------------- | -------------------------------------------------------------------- | --------- |
| 1   | `atc_get_variants`           | `openadt adt atc-get-variants [SYSTEM]`                                  | `adt atc-get-variants`           | `adt_atc_get_variants`                | High                   | Implement                                                            |
| 2   | `atc_run_check`              | `openadt adt atc-run-check [SYSTEM] --uri <uri> [--variant <v>]`         | `adt atc-run-check`              | `adt_atc_run_check`                   | High                   | Implement                                                            |
| 3   | `run_application`            | `openadt adt run-application [SYSTEM] --uri <uri>`                       | `adt run-application`            | `adt_run_application`                 | Medium                 | Implement                                                            |
| 4   | `get_creation_ui_model`      | `openadt adt creation-ui-model [SYSTEM] --type <t>`                      | `adt creation-ui-model`          | `adt_creation_ui_model`               | Low                    | Implement                                                            |
| 5   | `get_creation_side_effects`  | `openadt adt creation-side-effects [SYSTEM] --uri <uri>`                 | `adt creation-side-effects`      | `adt_creation_side_effects`           | Low                    | Implement                                                            |
| 6   | `lock_object`                | `openadt adt lock-object [SYSTEM] --uri <uri>`                           | `adt lock-object`                | `adt_lock_object`                     | High                   | Implement                                                            |
| 7   | `unlock_object`              | `openadt adt unlock-object [SYSTEM] --uri <uri>`                         | `adt unlock-object`              | `adt_unlock_object`                   | High                   | Implement                                                            |
| 8   | `get_lock_status`            | `openadt adt get-lock-status [SYSTEM] --uri <uri>`                       | `adt get-lock-status`            | `adt_get_lock_status`                 | Medium                 | Implement                                                            |
| 9   | `toggle_version`             | `openadt adt toggle-version [SYSTEM] --uri <uri>`                        | `adt toggle-version`             | `adt_toggle_version`                  | High                   | Implement                                                            |
| 10  | `format_code`                | `openadt adt format-code [SYSTEM] --uri <uri>`                           | `adt format-code`                | `adt_format_code`                     | High                   | Implement                                                            |
| 11  | `get_diagnostics`            | `openadt adt get-diagnostics [SYSTEM] --uri <uri>`                       | `adt get-diagnostics`            | `adt_get_diagnostics`                 | High                   | Implement                                                            |
| 12  | `find_references`            | `openadt adt find-references [SYSTEM] --uri <uri>`                       | `adt find-references`            | `adt_find_references`                 | High                   | Implement                                                            |
| 13  | `get_hover`                  | `openadt adt get-hover [SYSTEM] --uri <uri> --line N --col N`            | `adt get-hover`                  | `adt_get_hover`                       | Medium                 | Implement                                                            |
| 14  | `document_symbols`           | `openadt adt document-symbols [SYSTEM] --uri <uri>`                      | `adt document-symbols`           | `adt_document_symbols`                | Medium                 | Implement                                                            |
| 15  | `check_transport_lock`       | `openadt adt check-transport-lock [SYSTEM] --uri <uri>`                  | `adt check-transport-lock`       | `adt_check_transport_lock`            | High                   | Implement                                                            |
| 16  | `create_transport`           | `openadt adt create-transport [SYSTEM] --uri <uri> [--type w             | k]`                              | `adt create-transport`                | `adt_create_transport` | High                                                                 | Implement |
| 17  | `assign_transport`           | `openadt adt assign-transport [SYSTEM] --uri <uri> --number <n>`         | `adt assign-transport`           | `adt_assign_transport`                | High                   | Implement                                                            |
| 18  | `search_transports`          | `openadt adt search-transports [SYSTEM] [--user <u>] [--trfunction <f>]` | `adt search-transports`          | `adt_search_transports`               | Medium                 | Implement                                                            |
| 19  | `search_transports_advanced` | `openadt adt search-transports-advanced [SYSTEM] [--query <q>]`          | `adt search-transports-advanced` | `adt_search_transports_advanced`      | Medium                 | Implement                                                            |
| 20  | `quick_search`               | `openadt adt quick-search [SYSTEM] --term <t> [--max N]`                 | `adt quick-search`               | `adt_quick_search`                    | High                   | Implement                                                            |
| 21  | `get_inactive_objects`       | `openadt adt get-inactive-objects [SYSTEM] --request <id>`               | `adt get-inactive-objects`       | `adt_get_inactive_objects`            | Medium                 | Implement                                                            |
| 22  | `get_coverage`               | `openadt adt get-coverage [SYSTEM] --uri <uri>`                          | `adt get-coverage`               | `adt_get_coverage`                    | Medium                 | Implement                                                            |
| 23  | `load_statement_coverage`    | `openadt adt load-statement-coverage [SYSTEM] --uri <uri>`               | `adt load-statement-coverage`    | `adt_load_statement_coverage`         | Medium                 | Implement                                                            |
| 24  | `get_users`                  | `openadt adt get-users [SYSTEM]`                                         | `adt get-users`                  | `adt_get_users`                       | Low                    | Implement                                                            |
| 25  | `get_ls_uri`                 | `openadt adt get-ls-uri [SYSTEM] --uri <uri>`                            | `adt get-ls-uri`                 | `adt_get_ls_uri`                      | Low                    | Implement                                                            |
| 26  | `refresh_object`             | `openadt adt refresh-object [SYSTEM] --uri <uri>`                        | `adt refresh-object`             | `adt_refresh_object`                  | Low                    | Implement                                                            |
| 27  | `get_object_name`            | `openadt adt get-object-name [SYSTEM] --uri <uri>`                       | `adt get-object-name`            | `adt_get_object_name`                 | Low                    | Implement                                                            |
| 28  | `get_package_name`           | `openadt adt get-package-name [SYSTEM] --uri <uri>`                      | `adt get-package-name`           | `adt_get_package_name`                | Low                    | Implement                                                            |
| 29  | `get_folder_uri`             | `openadt adt get-folder-uri [SYSTEM] --uri <uri>`                        | `adt get-folder-uri`             | `adt_get_folder_uri`                  | Low                    | Implement                                                            |
| 30  | `get_external_links`         | `openadt adt get-external-links [SYSTEM] --uri <uri>`                    | `adt get-external-links`         | `adt_get_external_links`              | Low                    | Implement                                                            |
| —   | `activate`                   | n/a (already in SAP MCP)                                                 | n/a                              | `abap_activation` / `adt_activation`  | High                   | **Do not reimplement — use `abap_activation` (SAP MCP)**             |
| —   | `run_abap_unit`              | n/a (already in SAP MCP)                                                 | n/a                              | `abap_unit_run` / `adt_abap_unit_run` | High                   | **Do not reimplement — use `abap_unit_run` (SAP MCP)**               |
| —   | `list_creatable_objects`     | n/a (already in SAP MCP)                                                 | n/a                              | `adt_creatable_objects`               | —                      | **Do not reimplement — use `adt_creatable_objects` (SAP MCP)**       |
| —   | `get_creatable_object`       | n/a (already in SAP MCP)                                                 | n/a                              | `adt_creatable_single_object`         | —                      | **Do not reimplement — use `adt_creatable_single_object` (SAP MCP)** |
| —   | `validate_creatable`         | n/a (already in SAP MCP)                                                 | n/a                              | `adt_creatable_validation`            | —                      | **Do not reimplement — use `adt_creatable_validation` (SAP MCP)**    |
| —   | `create_object`              | n/a (already in SAP MCP)                                                 | n/a                              | `adt_creatable_creation`              | —                      | **Do not reimplement — use `adt_creatable_creation` (SAP MCP)**      |

**Per-row contract (all 30 implementable rows):**

- **Input:** all verbs take `--config <path>`, `[SYSTEM]`, and one or more verb-specific flags (`--uri`, `--term`, etc.). A complete flag table per verb is added in the task that implements that verb (T2..T20 per [`plans/lsp-agent-foundation.md`](../plans/lsp-agent-foundation.md)).
- **Output (`--json`):** the stable envelope

  ```json
  { "success": true,  "data": { ... } }
  { "success": false, "error": { "code": "NOT_FOUND", "message": "...", "destination": "DEV" } }
  ```

  Without `--json`, success writes a human-readable summary to stdout; errors are written to stderr with the exit code below.

- **Exit codes:** `0` on success; `1` on general error; `2` on usage error (missing required flag); `3` on `SDK_TRANSPORT_REQUIRED`; `4` on `LOCKED_BY_OTHER` / `NO_TRANSPORT`; `5` on `NOT_FOUND` / `INVALID_URI`; `6` on `THROTTLED` (retry with backoff); `7` on `INTERNAL`; `8` on `UNSUPPORTED_OBJECT_TYPE`.
- **Transport requirement:** `sdk` only. The CLI calls `AdtCommandSupport.requireSdkTransport` before dispatching (see [Tests](#tests)).
- **Source mapping:** the canonical priority and source notes for each verb live in [`specs/lsp-operations-catalog.md`](lsp-operations-catalog.md). This spec is the contract; the catalog is the historical source-of-truth being replaced.

## MCP tool reference

### Naming

- Tool name: `adt_<verb>` (snake_case, derived from the verb id).
- **Length budget:** every `adt_<verb>` name is **≤ 45 characters**, to satisfy the Claude + AWS Bedrock formula in [`specs/mcp.md:382-410`](mcp.md:382) (`len(serverKey) + len(toolName) ≤ 57`, with `mcp__ + __` overhead of 7). The longest name in the table above is `adt_search_transports_advanced` (30 chars) — well under the cap. The MCP layer shortens any over-cap name the same way the SAP server does (`tools/openadt-mcp-agent/src/tool-name-limit.ts`), with `OPENADT_MCP_AGENT_MAX_TOOL_NAME` overriding the cap (minimum 16).

### JSON schemas

Every tool has the same shape (a flat object that mixes destination, the verb-specific payload, and the optional envelope flag):

**Input schema (per tool):**

```json
{
  "type": "object",
  "required": ["destination"],
  "properties": {
    "destination": {
      "type": "string",
      "description": "Destination alias (matches `openadt adt <verb> [SYSTEM]`)."
    },
    "<verb-args>": {
      "type": "object",
      "description": "Verb-specific arguments (see per-tool table in the implementation task)."
    },
    "asJson": {
      "type": "boolean",
      "default": true,
      "description": "If false, MCP returns a one-line summary text content; the JSON envelope is still in the `structuredContent` field."
    }
  },
  "additionalProperties": false
}
```

**Output schema (per tool):**

```json
{
  "type": "object",
  "required": ["success"],
  "properties": {
    "success": { "type": "boolean" },
    "data": { "type": "object" },
    "error": {
      "type": "object",
      "required": ["code", "message", "destination"],
      "properties": {
        "code": {
          "type": "string",
          "enum": [
            "LOCKED_BY_OTHER",
            "NO_TRANSPORT",
            "NOT_FOUND",
            "SDK_TRANSPORT_REQUIRED",
            "INVALID_URI",
            "THROTTLED",
            "INTERNAL",
            "UNSUPPORTED_OBJECT_TYPE"
          ]
        },
        "message": { "type": "string" },
        "destination": { "type": "string" }
      }
    }
  },
  "additionalProperties": false
}
```

**Success example (illustrative, for `adt_quick_search`):**

```json
{
  "success": true,
  "data": {
    "destination": "DEV",
    "term": "Z*",
    "count": 12,
    "objects": [
      {
        "type": "CLAS/OC",
        "name": "ZCL_AGENT_DEMO",
        "package": "$TMP",
        "uri": "/sap/bc/adt/oo/classes/zcl_agent_demo"
      }
    ]
  }
}
```

**Error example (illustrative, for `adt_lock_object`):**

```json
{
  "success": false,
  "error": {
    "code": "LOCKED_BY_OTHER",
    "message": "Object is locked by user DEVELOPER (since 2026-06-09T14:22:01Z).",
    "destination": "DEV"
  }
}
```

MCP transport wraps the envelope in `structuredContent` and surfaces a short text summary in `content[0].text` (e.g. `quick_search: 12 hits in DEV`).

## Error codes

Closed enum, returned in `error.code` for both CLI (`--json`) and MCP tools. Documented here once, referenced from the per-verb tables.

| Code                      | Meaning                                                                      | Exit code (CLI) | Retryable | When                                                                   |
| ------------------------- | ---------------------------------------------------------------------------- | --------------- | --------- | ---------------------------------------------------------------------- |
| `LOCKED_BY_OTHER`         | Target object is locked by another user in this transport.                   | 4               | No        | `lock_object`, `unlock_object`                                         |
| `NO_TRANSPORT`            | Operation requires a transport assignment (e.g. saving without a transport). | 4               | No        | `create_transport`, `assign_transport`                                 |
| `NOT_FOUND`               | Object / URI / search target does not exist on the destination.              | 5               | No        | `quick_search`, `find_references`, `refresh_object`, URI-bearing verbs |
| `SDK_TRANSPORT_REQUIRED`  | `--transport` was set to `http` or `rest-rfc`; the verb needs SDK + JCo.     | 3               | No        | All verbs (gated by `AdtCommandSupport.requireSdkTransport`)           |
| `INVALID_URI`             | URI does not parse as an ADT URI (missing type / name / package).            | 5               | No        | Any verb that takes `--uri`                                            |
| `THROTTLED`               | Per-destination token bucket exhausted; back off and retry.                  | 6               | **Yes**   | `format_code`, `get_diagnostics`, `find_references` (per catalog note) |
| `INTERNAL`                | Unclassified SDK / transport / NPE / I-O failure.                            | 7               | Sometimes | Any verb                                                               |
| `UNSUPPORTED_OBJECT_TYPE` | The destination's ADT API does not implement the verb for this object type.  | 8               | No        | `format_code`, `get_diagnostics`, `find_references`, AFF-bearing verbs |

Implementations **must not** leak raw `com.sap.adt.*` exceptions or stack traces to the user. Map every SDK exception to one of the codes above (`AgentErrorMapper` helper, added in T1).

## Out of scope

Verbatim from the catalog's `N/A (LSP-only)` rows in [`specs/lsp-operations-catalog.md`](lsp-operations-catalog.md):

- **Code completion** — `AbapLsCodeCompletionService.completion()`, `resolveCompletion()`. LSP-only; stateful, editor-dependent.
- **Code lens** — `JsonLsCodeLensService.codeLens()`. LSP-only; provides in-editor actions.
- **Debugger** — `AdtLsDebuggerExtension.onBreakpointChangedRequest()`, `initializeDebugger()`. LSP-only; uses the Debug Adapter Protocol.
- **Document highlight** — `AbapLsDocumentHighlightService.documentHighlights()`. LSP-only; for the active editor.
- **Dirty-state notifications** — `AdtLsTextDocumentExtension.notifyDirtyState()`. LSP-only; document sync lives in LSP.
- **AFF adapters** — `BdefAffAdapter`, `ClasAffAdapter`, `IntfAffAdapter`, `DdlaAffAdapter`, `DdlsAffAdapter`, `SrvbAffAdapter`, `SrvdAffAdapter`. LSP-only; handle editor-side file-format conversion.

Additionally out of scope (not from the catalog, but implied by the SDD gate):

- The 6 verbs already in the SAP MCP server (`activate`, `run_abap_unit`, `list_creatable_objects`, `get_creatable_object`, `validate_creatable`, `create_object`). Marked "Do not reimplement" in the command table above.
- SDK services that are not in the p2 bundle today. Re-evaluate as the SDK bundle grows.

## Security

- **Endpoint store:** `~/.openadt/mcp-agent/endpoints/<port>.json`. File mode `0600` (owner read/write only). Removed on clean shutdown.
- **Bearer token:** generated 16-byte URL-safe base64 by default; written only to the endpoint store; redacted (`***`) in all logs and `--verbose` output. Override with `--token` for fixed dev tokens; never commit a real token.
- **No secrets in code, fixtures, or docs:** fictional only — `DEV`, `dev-ms.example.com`, fake UUIDs like `00000000-0000-0000-0000-000000000000`. No real SAP hosts, no JCo jars, no SSO tickets.
- **Localhost only:** the MCP server binds `127.0.0.1` only; the `Host` header is checked (DNS rebinding filter, same as the SAP server).
- **No live SAP calls in CI:** unit tests use a mock `ISystemSession` and assert the SDK was called with the right URI. Integration tests are tagged `@Tag("integration")` and require explicit opt-in.

## Tests

The unit-test contract each `AgentService` implementation must satisfy. The same contract applies to its CLI subcommand and MCP tool (one Java service drives both).

1. **Success path** — given a mock session that returns a fixed SDK response, calling the service returns `AgentResult(success=true, data=...)` and the JSON envelope round-trips.
2. **Error path** — given a mock session that throws `AdtException` (or one of the catalog-specific failure shapes), the service returns `AgentResult(success=false, error={code, message, destination})` with the **correct closed-enum code**. Raw `AdtException` must not leak.
3. **Throttling path** (verbs `format_code`, `get_diagnostics`, `find_references` per the catalog note) — burst N+1 calls within 1 s on the same destination; the (N+1)th call returns `THROTTLED` without invoking the SDK.
4. **`transport ≠ sdk` rejection** — both the CLI subcommand and the MCP tool must call `AdtCommandSupport.requireSdkTransport` (or the equivalent service-layer guard) and fail with `SDK_TRANSPORT_REQUIRED` when the destination's `adt.transport` is `http` or `rest-rfc`. See the precedent at [`apps/openadt-cli/src/main/java/org/openadt/cli/AdtCommandSupport.java:45`](../apps/openadt-cli/src/main/java/org/openadt/cli/AdtCommandSupport.java).
5. **JSON envelope** — the `success` envelope and the `error` envelope both round-trip through Jackson without loss of fields and in a stable field order (used by the MCP `structuredContent` shape).

MCP-layer tests (separate, in `tools/openadt-mcp-agent/`):

- `tools/list` returns exactly the registered verbs (no extras, no SAP tools).
- `tools/call` invokes the Java service via the bridge socket and returns the envelope.
- Bearer auth: missing or wrong token → `401`; correct token → `200`.
- Tool-name cap: a name longer than 45 characters in `tools/list` is shortened and an alias is registered for `tools/call`.

## Implementation plan pointer

See [`plans/lsp-agent-foundation.md`](../plans/lsp-agent-foundation.md) for the 19-task rollout (T0..T21). This spec is the contract; the plan is the human-facing process that delivers it.
