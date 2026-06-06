---
name: backlog
description: >-
  Actionable improvement items derived from experience and retrospectives.
  Items live in .agents/backlog/.
---

# Backlog

Actionable work items. Unlike memory (knowledge), backlog is about **what to do next**.

## Storage

Write to `.agents/backlog/YYYY-MM-DD-<slug>.md`:

```yaml
---
date: YYYY-MM-DD
tags: [tag1, tag2]
source: <path to memory/experience entry or PR URL>
---

## Problem
<what needs fixing or improving>

## Proposed action
<concrete next step — not a vague idea>
```

## Rules

1. **Actionable only** — every item must have a concrete "Proposed action". Vague ideas go to `.agents/memory/observations/`.
2. **Link to source** — if the item came from a retrospective or experience, reference it in `source:`.
3. **One item per file** — keep atomic, easy to close by deleting the file.
4. **Close by deleting** — when done, remove the file (git tracks history).
