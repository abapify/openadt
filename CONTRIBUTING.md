# Contributing to OpenADT

Thank you for helping improve OpenADT.

## Before you code

1. Read [specs/vision.md](specs/vision.md) and the spec for your area ([specs/README.md](specs/README.md)).
2. Use **fictional fixtures** only in commits (`DEV`, `DEVELOPER`, `dev-ms.example.com`) — see [AGENTS.md](AGENTS.md).
3. Never commit SAP JCo jars, `sapcrypto`, or Secure Login binaries.

## Development setup

```bash
bun install --frozen-lockfile
./mvnw -q verify -f pom.xml -Pdistribution
bun run openadt:test
```

## Pull requests

- One logical change per PR when possible (docs/guardrails → packages → modules → MCP).
- Update specs when behavior changes.
- Run before push:

```bash
bun scripts/verify-spec-sync.ts
bun scripts/verify-package-docs.ts
./mvnw -q verify -f pom.xml -Pdistribution
bun run openadt:test
```

- Fill out the PR template checklist.

### Dead code and dependencies

- Profile `-Pdeadcode` runs UCDetector when the Maven plugin resolves (see `tmp/dead-code-report.txt` if unavailable).
- Optional future: [depclean-maven-plugin](https://github.com/ASSERT-KTH/depclean) on the parent POM for unused dependency hints — not a merge gate.
- Do not include real landscape data, credentials, or `~/.openadt/` dumps.

## Spec-driven development

See [.agents/skills/openadt-sdd/SKILL.md](.agents/skills/openadt-sdd/SKILL.md).

## Code layout

[apps/ARCHITECTURE.md](apps/ARCHITECTURE.md) — Maven modules and Java packages.

## Review

GitHub Copilot and Codacy use repository instructions; `/act` on PRs means fix in code + reply in thread, then resolve.
