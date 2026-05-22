# OpenADT Agent Guidelines

Read specs first, prefer TDD, keep SAP binaries external, never commit private SAP landscape data, redact secrets, and update specs when command behavior changes.

## Rules
1. Read specs/ before broad implementation changes
2. Prefer TDD: write tests first for config parsing, request/response mapping, proxy auth, header redaction, setup detectors
3. SAP/JCo integration tests must be opt-in (annotated @Tag("integration")) and skipped by default
4. Keep SAP binaries external: never bundle or commit JCo jars, sapcrypto.dll, or Secure Login Client files
5. **Never commit private SAP landscape data** — no real SIDs, clients, usernames, logon groups, hostnames/FQDNs, SNC partner names, Secure Login profile UUIDs, discovery URLs, or organization names. Use only fictional fixtures: `DEV`, `DEVELOPER`, `dev-ms.example.com`, `DEV_100_developer_en`, `PUBLIC`, `p:CN=SAPServiceDEV`, and clearly fake UUIDs. Do not copy values from the user's machine, live command output, or `~/.openadt/config.toml` into the repo.
6. Redact secrets in logs: never log SAP credentials, cookies, tickets, SNC tokens, or authorization headers
7. Update specs/ when command behavior changes
8. Put temporary repo-local artifacts under `tmp/`, which must stay gitignored; do not leave ad hoc scratch files elsewhere in the workspace

## Specs
- specs/cli.md — CLI contract and command behavior
- specs/config.md — Config file format and field descriptions
- specs/proxy.md — Proxy server behavior and security requirements
- specs/setup.md — Setup analyzer behavior and detector descriptions

## Skills
- .agents/skills/ — Reusable agent skills for common tasks
