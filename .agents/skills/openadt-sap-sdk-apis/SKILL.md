---
name: openadt-sap-sdk-apis
description: SAP ADT SDK API decision tree for OpenADT discover/logon and future typed tools.
---

# SAP ADT SDK APIs (OpenADT)

## When to use which layer

| Need | Use | Avoid |
|------|-----|-------|
| Arbitrary ADT URI (debug, agents) | `openadt fetch` / `openadt proxy` + `IStatelessSystemSession.sendRequest` | Reimplementing HTTP in product code |
| Check SSO/SNC logon | `openadt adt logon` / `logon-status` → `IAdtLogonService` | Raw `/sap/bc/adt/discovery` only for transport test |
| ADT discovery document / collection member | `openadt adt discover` → `IAdtDiscovery` | Parsing Atom/XML by hand in CLI |
| Object / project APIs | Phase 2 — research `com.sap.adt.tools.core` | Blocking discover/logon on tools.core |

## Factory entry points (3.58.x)

- **Logon:** `AdtLogonServiceFactory.createLogonService()` → `ensureLoggedOn(IDestinationData, IAuthenticationToken, IProgressMonitor)`
- **Discovery:** `AdtDiscoveryFactory.createDiscovery(destinationId, AdtDiscoveryFactory.RESOURCE_URI)` → `getStatus` / `getCollectionMember`
- **Request:** `AdtSystemSessionFactory.createSystemSessionFactory().createStatelessSession(destinationId)` → `sendRequest`

## OpenADT service layer

Package `org.openadt.sap.adt.services`:

1. `SapAdtSessionContext.open(config, system)` — `SapSdkRuntime.prepare` + `SapDestinationResolver`
2. `LogonService` / `DiscoveryService` — thin SDK wrappers
3. CLI/MCP call `AdtSdkServiceGateway` (reflection) so `-Pdistribution` CI compiles without SAP JARs

## Spec

[specs/sdk-capabilities.md](../../specs/sdk-capabilities.md)

## Research notes

Local agent research may exist under `tmp/sap-sdk-research/` (gitignored). Do not copy decompiled paths or WSL locations into specs, README, or skills.
