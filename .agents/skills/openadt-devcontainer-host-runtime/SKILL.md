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
| Detect SAP landscape, write TOML | WSL or devcontainer | `openadt setup`, devcontainer bootstrap |
| `fetch` / `proxy` with SDK+JCo | **Host Windows** (or Linux with Linux natives) | `openadt` / `scripts/openadt-sdk.ps1` |
| IDE proxy target | Host loopback | `openadt proxy` on `127.0.0.1` |

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

On the host OS:

```text
openadt fetch <SID> /sap/bc/adt/core/http/systeminformation --json
```

Must use `SapDestinationResolver` + `AdtSdkTransportClient` (not HTTP transport), with Eclipse destination when workspace is on a mounted path detectors can scan.

## Anti-patterns

- Running `openadt-sdk.ps1` inside Linux container against Windows `.p2` paths without Windows Java
- Assuming `transport=http` + `MYSAPSSO2` equals Eclipse ADT behavior
- Committing `.devcontainer/dist` JCo natives as a substitute for host SAP installation

## Related skill

`openadt-local-sap-runtime` — JCo jar naming, classpath order, Secure Login profiles, Eclipse destinations.
