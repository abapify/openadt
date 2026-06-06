---
name: memory-bank
description: >-
  Unified agent memory. Use /remember to store facts, experience, observations,
  and mental-models in .agents/memory/.
---

# Memory Bank

Unified agent memory inspired by [Hindsight](https://hindsight.vectorize.io/).
All entries are markdown files in `.agents/memory/`.

## Memory types

| Type | Dir | What | Example |
|------|-----|------|---------|
| **fact** | `facts/` | Observable truth about the project | "tsdown bundles MCP launcher to dist/" |
| **experience** | `experience/` | Something that happened — mistake, debugging session, decision | "git merge bloated PR #56 history" |
| **observation** | `observations/` | Consolidated pattern from multiple facts/experiences | "Codacy 'N issues (0 annotations)' always means run linter locally" |
| **mental-model** | `mental-models/` | Curated principle or heuristic | "Always rebase, never merge feature branches" |

### Hierarchy (reflect order)

```
mental-models  →  observations  →  facts + experience
 (principles)    (patterns)       (raw ground truth)
```

When reasoning, check mental-models first (curated), then observations (consolidated), then raw facts/experience (evidence).

## Operations

### retain — store a memory

```
/remember <type> — <content>
```

Write to `.agents/memory/<type>/YYYY-MM-DD-<slug>.md`:

```yaml
---
date: YYYY-MM-DD
tags: [tag1, tag2]
---

<content>
```

Frontmatter is minimal: only `date` and `tags`. Add `context: <URL>` when there's a relevant link. Omit fields you don't know.

### recall — retrieve memories

Before starting work, scan `.agents/memory/` for relevant entries. Read mental-models first, then observations, then facts/experience as needed.

### reflect — consolidate knowledge

After a session with multiple related experiences or facts, consolidate them into an observation or mental-model. Delete or archive the raw entries if the consolidation captures everything.

## Rules

1. **One file per memory** — keep atomic
2. **No opinions in facts** — facts are observable; opinions go in observations or mental-models
3. **No implementation in experience** — record what happened and why, not the fix (fix goes in code)
4. **Consolidate aggressively** — if 3+ facts/experiences say the same thing, write one observation
5. **Mental-models are curated** — only promote an observation to mental-model if it's proven and durable
6. **PII safety** — redact system IDs, hostnames, credentials before committing
