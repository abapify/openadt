# OpenADT Agents Directory

**Start here:** All agents (Claude Code, subagents, external tools) must follow the universal CodeScene principles in [AGENT-GUIDELINES.md](./AGENT-GUIDELINES.md).

## Structure

### Guidelines & Standards

- **[AGENT-GUIDELINES.md](./AGENT-GUIDELINES.md)** — Universal rules for all agents (CodeScene thresholds, design principles, refactoring patterns, stop conditions)
- **[skills/openadt-codescene/SKILL.md](./skills/openadt-codescene/SKILL.md)** — Refactoring recipes (Extract Method, Parameter Object, Predicate Extraction, Guard Clauses)
- **[memory/project/codescene-contract.md](./memory/project/codescene-contract.md)** — Centralized memory for agents to inherit CodeScene context (no re-reading docs)

### Other Resources

- **[skills/](./skills/)** — Domain-specific skills (CodeScene refactoring, retrospects, backlog, etc.)
- **[memory/](./memory/)** — Agent experience logs and mental models

## Getting Started (Agents)

1. **Read first:** [AGENT-GUIDELINES.md](./AGENT-GUIDELINES.md) — binds all agents
2. **Before coding in tools/ or scripts/:** [skills/openadt-codescene/SKILL.md](./skills/openadt-codescene/SKILL.md) for recipes
3. **Before reviewing PRs:** Verify `cs delta origin/main HEAD --error-on-warnings` passes
4. **After 3 pushes:** Report findings; do not iterate unilaterally

## Key Principles (TL;DR)

| Rule | Threshold | Action |
|------|-----------|--------|
| Cyclomatic complexity per function | ≤ 9 | Extract Method |
| File mean CC | ≤ 4 | Guard clauses + extract predicates |
| Function arguments | ≤ 4 | Use Parameter Object |
| Primitive args in file | ≤ 30% | Use domain types |
| String args in file | ≤ 39% | Use domain types + Parameter Objects |
| CodeScene health score (new code in tools/scripts) | ≥ 10.0 | Refactor before merge |
| **Design principle** | **Design-to-10.0** | Never inherit low-health code; simplify from first push |

## Avoiding Duplication

- **CLAUDE.md** — minimal, points to universal guidelines
- **AGENT-GUIDELINES.md** — single source of truth for all agents
- **codescene-contract.md** — memory file agents can access without re-reading docs
- **docs/codescene.md** — official contract (reference only, not for agent distribution)

Each file has a single purpose; no content is duplicated across files.
