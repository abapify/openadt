# Plan: read ABAP objects in OpenADT MCP (emulate `adt-vscode.openAbapObject`)

**STATUS: IN PROGRESS (2026-06-07).** Core + standalone + shared (Option A) implemented and tested. Option B (full front / read for HTTP clients) is the TODO below.

## Context

Our MCP proxies the official SAP ADT MCP tools (`adt-lsc`). None of them can **read an
object's source**, although VS Code can (`adt-vscode.openAbapObject`). Goal: let the agent
read ABAP objects by name, **reusing `adt-lsc`'s LSP methods** (explicit requirement: do not
duplicate the ADT client via a bare `openadt fetch`). In short — emulate VS Code without VS Code.

Decisive fact: the user's sibling project **`arc-1-lsp`** already did this on top of the same
`adt-ls` and **live-verified it against a4h**. Its docs are ground truth:
`C:\Users\pplenkov\Documents\GitHub\arc-1-lsp\docs\adt-ls-reference.md`,
`docs\research\adt-ls-capability-map.md`; the working wrappers are `src\adt-ls\repository.ts`.
We reuse them, adapted to OpenADT's transport.

## Verified by-name read flow (= `openAbapObject`)

Same chain everywhere (param names are verified — do not get them wrong):

```
adtLs/repository/quickSearch { destination, pattern, maxResults, types[] }
    → references[]: AdtObjectReference { name, type, uri /* = ADT path */, description }
adtLs/repository/getLsUri    { destination, adtUri: <ADT path from quickSearch> }
    → { uri: <repotree/AFF URI> }
adtLs/fileSystem/readFile    { uri: <repotree/AFF URI> }
    → { content }
```

Critical details (from `adt-ls-reference.md` §1, verified):

- Search field is **`pattern`**, not `query`. Resolve key is **`adtUri`**, not `uri`.
- `readFile` accepts **only** the canonical repotree/AFF URI (single slash,
  `abap:/repotree-v1/<DEST>/…/<obj>.clas.abap`). An ADT-path URI (`abap://…/sap/bc/adt/…`) → `{}`.
  **Never hand-build the URI** (percent-encoding trap `(`→`%28`); only use what `getLsUri` returns).
- **Type boundary (hard adt-ls limit = VS Code's own behaviour):** `getLsUri` resolves all types,
  but `readFile` returns **source** only for modern clean-core/RAP types: CLAS, INTF, DDLS, DCLS,
  SRVB, DDLX, BDEF, SRVD, DRAS, CDS types. Classic types (PROG, TABL, FUGR/FUNC, DOMA, DTEL, MSAG, …)
  return a `.jsonc` placeholder ("not supported in ADT in VS Code… use Eclipse"). We surface that
  as-is (`isUnsupportedPlaceholder`); we never work around it.
- A logged-on destination is required — the daemon already does `createProject + ensureLoggedOn` at start.

Decompiled interfaces in our repo confirm this: `tmp/sap-adt-mcp-decompiled/.../com.sap.adt.ls_*`
(`IAdtLsRepositoryExtension`, `IAdtLsFileSystemExtension`, `ReadFileParams/Response`, `GetLsUriParams`,
`QuickSearchParams`, `AdtObjectReference`).

## Robustness semantics (per the user's guidance)

- Everything is async, no synchronous blocking.
- **Pre-warm**: after logon the daemon runs one cheap `quickSearch` per destination to warm the RIS index.
- **Never surface an empty result**: an empty `quickSearch` / empty source masks a cold RIS index or a
  dead SAP session as "not found". Instead retry with backoff until a deadline, then **fail with a
  timeout error** rather than return `[]`/`{}`.

## Progress

- ✅ `types.ts`: LSP method constants + DTOs (`AdtObjectReference`, `QuickSearchResult`).
- ✅ `read-object.ts`: wrappers `quickSearch`/`getLsUri`/`readFile`, `isUnsupportedPlaceholder`,
  `ReadObjectBackend` + `LspReadBackend` + `HttpReadBackend`, `pickReference`, `retryUntilNonEmpty`
  (never-empty → timeout), `prewarm`, MCP tool defs + `handleReadToolCall`.
- ✅ `stdio-proxy.ts`: merge read tools into `tools/list` + answer their `tools/call` locally
  (same mechanism as `prompts/get`); `bridge.setReadBackend(...)`.
- ✅ `read-server.ts`: small `Bun.serve` (Bearer, 127.0.0.1) with `POST /read-object` and `POST /search`
  over `LspReadBackend` — Option A.
- ✅ `main.ts`:
  - standalone (`serve --stdio --standalone`) — wire `LspReadBackend(session.connection)` straight into the bridge;
  - daemon (`serve` without `--stdio`) — start the aux endpoint, record `auxUrl/auxToken` in the endpoint
    store, stop it on shutdown; prewarm per destination;
  - shared (`serve --stdio`, default) — the bridge reads `auxUrl/auxToken` from the store and sets a `HttpReadBackend`.
- ✅ `endpoint-store.ts`: `auxUrl/auxToken` fields.
- ✅ Tests: `read-object.test.ts` (13), `read-server.test.ts` (3, round-trip + auth). `tsc --noEmit` clean.
  The suite's 3 pre-existing failures (`ensure-backend` × 2, an old stdio test) are unrelated to this feature.

## Tool scope (v1)

- `adt_read_object` — `{ destination, objectName, objectType? }` → source (primary; emulates
  `openAbapObject`; classic types return adt-ls's honest placeholder).
- `adt_search_objects` — `{ destination, pattern, types?, maxResults? }` → `AdtObjectReference[]`
  (nearly free — it is the resolve step; covers "find an object by pattern").

Types are covered automatically via `quickSearch.types`; no manual URL map. The "modern → source /
classic → placeholder" boundary is inherited from adt-ls (and from VS Code itself).

## Architecture (Option A — implemented)

```
standalone:  Agent ─stdio─► [openadt] {bridge + LSP + adt-lsc}      read handled in-process, 0 new servers
shared:      Agent ─stdio─► [bridge] ─HTTP(MCP)──► SAP MCP (daemon/adt-lsc)
                               └────HTTP(read)─► [daemon: aux /read-object,/search] ─LSP─► adt-lsc
```

## TODO (Option B — next pass)

Read is currently available to **stdio** clients (the default path). **HTTP** clients (`serve` without
`--stdio`, connecting straight to SAP MCP) have no read tools — that needs a single federating
OpenADT MCP front:

- [ ] OpenADT MCP HTTP front on the daemon: `initialize` / `tools/list` (merge SAP + ours) /
      `tools/call` (route: ours → LSP, rest → internal SAP MCP) / sessions / SSE.
- [ ] HTTP clients and the stdio bridge both connect to this front; SAP MCP moves to an internal port.
- [ ] Update `specs/mcp.md` (OpenADT stops being a "pure transparent proxy with no own tools").
- [ ] Possibly fold the Option-A aux endpoint into the front, so there is a single listener for everything.
- [ ] Robustness (if needed in production): stronger `cold-retry`, dead SAP-session revive
      (re-`ensureLoggedOn`) — port from `arc-1-lsp/src/adt-ls/{cold-retry,session-retry}.ts`.
- [ ] Class-include reads (`includeAffUri`) and package listing (`adtLs/fileSystem/readDirectory`).

## Verification (end-to-end)

1. Launcher typecheck + `bun test` for the new `*.test.ts`.
2. `./dev-openadt mcp serve --stdio --destination <DEST>` (wait for logon).
3. `tools/list` → `adt_read_object` and `adt_search_objects` appear next to the SAP tools.
4. `tools/call adt_read_object { destination:<DEST>, objectName:"CL_ABAP_TYPEDESCR" }` → source;
   compare with what VS Code `openAbapObject` shows for the same object.
5. Negative: a classic type (e.g. `TABL`/`PROG`) → adt-ls placeholder; a missing name → a clear
   timeout error (never an empty result).
6. Both modes: shared (via the daemon aux endpoint) and `--standalone` (in-process LSP).
