---
name: openadt-devcontainer-host-runtime
description: Use when designing or implementing a devcontainer workflow for OpenADT where configuration may live in Linux or WSL but SAP JCo and SNC may still need to execute on the host OS.
---

# OpenADT Devcontainer Host Runtime

## Core rule

Config can live in Linux/WSL/devcontainer; **JCo + SNC + Secure Login Web Adapter execute in the Java process that loads the native libraries**.

Mounted Windows paths are not enough to run Windows `sapjco3.dll` from Linux Java.

## Recommended split

| Step | Where | Tool |
|------|-------|------|
| Detect SAP landscape, write TOML | WSL or devcontainer | `./dev-openadt setup`, devcontainer bootstrap |
| `fetch` / `proxy` with SDK+JCo | **Host Windows** (or Linux with Linux natives) | `./dev-openadt` / `scripts/openadt-sdk.ps1` (from clone) or packaged `openadt` |
| IDE proxy target | Host loopback | `./dev-openadt proxy` / `openadt proxy` on `127.0.0.1` |

Generated paths (see `docs/superpowers/specs/2026-05-21-devcontainer-native-runtime-design.md`):

- `.devcontainer/openadt-config.toml`
- `.devcontainer/runtime.openadt.toml`
- `.openadt/destinations/generated-*.openadt.toml`

Bootstrap: `tools/devcontainer-bootstrap` (Bun/TS), not shell-only.

## What WSL detection is for

`SetupAnalyzer` / detectors may read:

- `/mnt/c/Users/<user>/SAP/...` landscape XML
- NWBC recents, `saprules.xml`, Eclipse workspace under `/mnt/c/...`

That populates **config only**. It does not make container Java load Windows DLLs.

## Secure Login in split setups

- Hub `https://127.0.0.1:34443` is on the **host** where Secure Login Client runs
- Container-side `fetch` cannot rely on host hub unless port-forwarded and the **same** Java process uses host-native SNC (usually wrong)

For MVP, run SDK transport on the host after `setup` in WSL wrote config to a shared path (`~/.openadt/config.toml` visible from both).

## Acceptance test (same as local skill)

On the host OS (from clone: `./dev-openadt …`; installed CLI: `openadt …`):

```text
./dev-openadt fetch <SID> /sap/bc/adt/core/http/systeminformation --json
```

Must use `SapDestinationResolver` + `AdtSdkTransportClient` (not HTTP transport), with Eclipse destination when workspace is on a mounted path detectors can scan.

## Stale `jco_native_dir` on the host (common)

Devcontainer bootstrap may set `runtime.jco_native_dir` to `.devcontainer/dist/jco` (Linux `libsapjco3.so`). If the user later runs `./dev-openadt fetch` on **Windows host Java**, JCo fails with `no sapjco3 in java.library.path` even though `./dev-openadt setup --check` already detects the correct Windows directory elsewhere.

**Fix on the host OS:** `./dev-openadt setup` (or `setup --check` then `setup --skip-build`) so `~/.openadt/local.openadt.toml` lists a directory that contains `sapjco3.dll`, not only `libsapjco3.so`.

Agents: compare configured `jco_native_dir` to the native file for the OS running the command before debugging destinations or SSO.

## Anti-patterns

- Leaving `.devcontainer/dist/jco` in `local.openadt.toml` while running fetch/proxy on Windows host Java
- Running `openadt-sdk.ps1` inside Linux container against Windows `.p2` paths without Windows Java
- Assuming `transport=http` + `MYSAPSSO2` equals Eclipse ADT behavior
- Committing `.devcontainer/dist` JCo natives as a substitute for host SAP installation

## Related skill

`openadt-local-sap-runtime` — JCo jar naming, classpath order, Secure Login profiles, Eclipse destinations.
