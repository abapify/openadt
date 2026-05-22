---
title: OpenADT Devcontainer Native Runtime Design
date: 2026-05-21
status: draft
---

# OpenADT Devcontainer Native Runtime Design

## Overview

This document defines the first supported devcontainer workflow for OpenADT with a container-native SAP runtime.

The design goal is:

- developers work inside a devcontainer
- SAP runtime artifacts are prepared on the host before container startup
- the container runs Linux-native JCo and SNC
- OpenADT executes `fetch` and `proxy` inside the container without a host-side runtime bridge

## Problem

OpenADT now supports:

- config-driven runtime behavior
- local SAP setup detection
- live JCo/SNC RFC validation against a real system

But it does not yet define a clean workflow for devcontainers.

The main constraint is that JCo and SNC load native libraries into the Java process. A Linux devcontainer therefore needs Linux-native SAP runtime artifacts. Windows DLLs are not enough.

Live validation on a real system adds one more constraint:

- a Windows host may keep the effective SNC credential in Windows Hello, a Windows certificate provider, or an in-memory PSE managed by Secure Login Client
- in that case, a Linux devcontainer can still reach the host Local Security Hub and even observe a `LOGGED_IN` Web Adapter profile, but Linux JCo may still fail with `GSS-API(maj): No credentials were supplied`
- therefore, Linux-native runtime artifacts are necessary but not sufficient; Linux-visible credential material is required for true container-native SNC

## Goals

- support OpenADT development inside a devcontainer
- support live `fetch` and `proxy` execution from inside the container
- keep configuration local to the repo or user home:
  - `./.openadt/config.toml`
  - `~/.openadt/config.toml`
- keep SAP binaries out of git
- make runtime preparation reproducible through host-side bootstrap scripts
- allow users to seed the runtime from SAP archives they already downloaded manually

## Non-Goals

- downloading SAP software automatically from `me.sap.com` in MVP
- committing SAP binaries to the repository
- relying on Windows-native JCo or SNC for container execution
- hiding runtime compatibility checks behind silent fallback

## Approaches Considered

### Option 1: Host Runner for Live JCo

Run setup in container and execute live JCo on the host.

Pros:

- works with host-native SAP installations

Cons:

- split runtime model
- extra bridge scripts
- weaker devcontainer story

Decision: reject as primary design now that Linux-native runtime is acceptable.

### Option 2: Pure Native Container Runtime with Host-Side Bootstrap

Run all OpenADT commands in the container, but prepare Linux-native SAP runtime artifacts on the host before container startup.

Pros:

- clean runtime model
- real devcontainer workflow
- no host execution bridge
- reproducible local setup

Cons:

- requires Linux JCo and Linux SNC artifacts
- requires bootstrap logic to unpack and stage runtime correctly

Decision: recommended MVP.

### Option 3: Automatic Download from SAP Software Center

Make bootstrap scripts download required SAP runtime artifacts directly.

Pros:

- fewer manual steps in theory

Cons:

- SAP auth flow is outside project control
- high fragility
- licensing and browser-based download flows are not a good first implementation target

Decision: postpone. MVP consumes user-downloaded archives only.

## Recommended Design

### High-Level Architecture

The first implementation uses four layers:

1. **Host-side bootstrap**
   - runs before container start
   - inspects a local download/source folder
   - unpacks Linux-native SAP artifacts
   - stages them into `.devcontainer/dist/`

2. **Staged runtime**
   - lives under `.devcontainer/dist/`
   - not committed to git
   - mounted into the container through the workspace

3. **Devcontainer**
   - provides JDK, Maven, and developer tooling
   - runs `openadt setup`, `openadt doctor`, `openadt fetch`, and `openadt proxy`
   - exports `SECUDIR` for Linux-native SNC credentials

4. **OpenADT config**
   - stored in `./.openadt/config.toml` or `~/.openadt/config.toml`
   - points at in-container Linux runtime paths

## Runtime Staging Layout

The bootstrap script stages runtime artifacts under:

```text
.devcontainer/dist/
  jco/
    sapjco3.jar
    libsapjco3.so
  snc/
    libsapcrypto.so
    libslcryptokernel.so
    sapgenpse
  metadata/
    manifest.json
```

Credential material is staged separately from the binaries, for example under:

```text
.devcontainer/sec/
```

That directory is the effective Linux `SECUDIR` and contains PSE files plus `cred_v2` when SNC SSO is configured.

Rules:

- `.devcontainer/dist/` must be ignored by git
- runtime artifacts are replaceable and reproducible
- bootstrap may update the staged files in place

## Bootstrap Contract

### Entry Points

Add host-side Bun/TypeScript tooling:

- `tools/devcontainer-bootstrap/src/main.ts`
- root `package.json` scripts for bootstrap execution
- optional shell wrappers under `scripts/` as convenience fallbacks

The primary path is Bun-based and OS-independent. It is called before container startup, for example through `initializeCommand`.

### Responsibilities

The bootstrap tool must:

1. locate a source directory containing user-downloaded SAP archives
2. inspect available archives and identify the latest compatible Linux runtime artifacts
3. extract and normalize the required files into `.devcontainer/dist/`
4. prepare `.devcontainer/sec/` as the Linux credential directory
5. write a small manifest that records:
   - source archive names
   - staged artifact names
   - extraction timestamp
6. fail with a precise error when required Linux artifacts are missing

### Host Contract

The host machine requirement for this workflow is:

- Bun
- user-downloaded SAP runtime archives

Java is not required on the host for bootstrap. Java lives inside the devcontainer and for the OpenADT runtime itself.

### Source Folder Strategy

MVP should not depend on direct SAP download automation.

Instead, the bootstrap script should support:

- explicit source path argument
- optional environment variable for default download folder
- simple archive discovery inside that folder

The script should analyze the folder contents and unpack what OpenADT needs. This is more robust than trying to automate `me.sap.com`.

## Devcontainer Definition

Add `.devcontainer/devcontainer.json` and a Dockerfile with:

- JDK
- Maven
- common shell tooling
- workspace mount
- `SECUDIR` pointing at `.devcontainer/sec`
- `initializeCommand` that installs Bun tooling dependencies and runs the bootstrap in non-interactive mode

The container does not fetch SAP binaries itself. It consumes the staged runtime prepared by the bootstrap script.

## Command Contract

The command contract for MVP is:

- `openadt setup`
  - detects and writes config only
- `openadt doctor`
  - validates config and runtime readiness
- `openadt fetch`
  - executes inside the container using Linux-native JCo/SNC
- `openadt proxy`
  - executes inside the container using Linux-native JCo/SNC

No host-side `fetch` or `proxy` bridge is part of this design.

## Config Contract

OpenADT already resolves config in this order:

1. `./.openadt/config.toml`
2. `~/.openadt/config.toml`

This remains unchanged.

For the devcontainer workflow, `setup` should prefer repo-local config. The staged runtime should be referenced through Linux-visible paths such as:

- `/workspaces/<repo>/.devcontainer/dist/jco/sapjco3.jar`
- `/workspaces/<repo>/.devcontainer/dist/jco`
- `/workspaces/<repo>/.devcontainer/dist/snc/libsapcrypto.so`
- `/workspaces/<repo>/.devcontainer/sec`

The exact path depends on the devcontainer mount point, but the config must point at in-container Linux paths, not host Windows paths.

## Doctor Command

Add a first-class `openadt doctor` command.

Responsibilities:

- validate config file presence and precedence
- validate staged runtime presence
- validate that the current runtime is Linux-native and internally consistent
- distinguish between:
  - missing config
  - missing staged runtime
  - missing SAP archive inputs
  - optional localhost Secure Login hub status
- report whether the current container is ready for live JCo/SNC calls

## Data Flow

### Bootstrap Flow

1. user downloads SAP runtime archives manually
2. host-side bootstrap script inspects the download/source folder
3. bootstrap extracts Linux-native artifacts into `.devcontainer/dist/`
4. bootstrap writes manifest metadata

### Setup Flow

1. container starts
2. user runs `openadt setup`
3. config is written to `./.openadt/config.toml`
4. runtime fields point at staged Linux paths

### Live Fetch Flow

1. user runs `openadt doctor`
2. doctor confirms staged runtime is usable
3. user runs `openadt fetch`
4. container-side Java loads:
   - `sapjco3.jar`
   - `libsapjco3.so`
   - `libsapcrypto.so`
5. OpenADT executes `SADT_REST_RFC_ENDPOINT`
6. ADT response is returned in-container

### Proxy Flow

1. user runs `openadt proxy`
2. proxy starts in the container
3. requests are forwarded through container-native JCo/SNC

## Security and Privacy

- never commit SAP binaries
- ignore `.devcontainer/dist/`
- never commit organization-specific SAP config or hostnames
- redact credentials and SAP auth artifacts in logs
- keep bootstrap manifest free of secrets

## Error Handling

### Bootstrap Errors

Report:

- source folder missing
- no compatible Linux JCo archive found
- no compatible Linux SNC archive found
- extraction failure
- staged runtime incomplete

### Container Runtime Errors

Report:

- config file missing
- staged runtime path missing
- native library load failure
- JCo class loading failure
- SNC initialization failure

### Live RFC Errors

Report:

- JCo destination creation failure
- RFC execution failure
- ADT response parsing failure

The existing OpenADT fixes for reflective `execute()` lookup and padded `STATUS_CODE` parsing stay part of the acceptance surface.

## Testing Strategy

### Unit Tests

Add tests for:

- bootstrap archive selection logic
- staged path generation
- doctor classification of missing vs present runtime
- config generation against staged Linux paths

### Integration Tests

Opt-in integration tests should validate:

- container-side live fetch against a real configured system
- container-side proxy startup

These tests remain skipped by default.

### Manual Acceptance

MVP is accepted when:

1. bootstrap script stages Linux-native SAP runtime into `.devcontainer/dist/`
2. repo opens successfully in a devcontainer
3. `openadt setup` writes `./.openadt/config.toml`
4. `openadt doctor` reports container runtime readiness
5. `openadt fetch <system> /sap/bc/adt/core/http/systeminformation` succeeds from inside the container

## Implementation Plan Outline

Phase 1:

- add `.devcontainer/`
- add bootstrap scripts
- add `.devcontainer/dist/` ignore rules

Phase 2:

- add `openadt doctor`
- teach `setup` to prefer staged Linux runtime paths when present

Phase 3:

- document source folder conventions
- refine diagnostics and example workflows

## Recommendation

Implement native container runtime as the first-class devcontainer workflow.

The defining rule for MVP is:

- host prepares Linux-native SAP runtime
- container executes OpenADT end to end

This yields a cleaner long-term model than a permanent host-side runtime bridge.
