# Agent Skills

This directory contains reusable agent skills for common OpenADT development tasks.

## Available Skills

- `openadt-local-sap-runtime`
  - **Knowledge base** for SDK+JCo transport: Eclipse destinations, JCo jar canonical names, classpath order, Secure Login hub vs portal, headless `jco.eclipse`, fetch/proxy parity, failure modes, validation commands.
- `openadt-devcontainer-host-runtime`
  - Split config (WSL/container) vs host-native `fetch`/`proxy`; bootstrap paths; anti-patterns.
- `act`
  - **`/act`** on PRs/MRs: priority queue P0 CI → P1 blocking review → P2 nits → P3 suggestions; all threads answered/resolved; idempotent re-runs.
  - Resolve helper: [`act/resolve-open-threads.sh`](act/resolve-open-threads.sh) (same directory as `SKILL.md`)

When changing transport, logon, or setup detectors, update the relevant skill in the same PR as code/spec changes.

**Privacy:** Skills and specs are public documentation. Never store real customer SIDs, usernames, organization-specific logon groups, hostnames, or Secure Login UUIDs from a developer machine.

## Skill Format

Each skill is a Markdown file with:
- A description of what the skill does
- Step-by-step instructions for the agent
- Any relevant code examples or templates
