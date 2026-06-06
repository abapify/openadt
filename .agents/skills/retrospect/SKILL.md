---
name: retrospect
description: >-
  Reflect on current session and record experience.
  /retrospect = remember. --plan = + backlog item. --fix = + implement now.
---

# Retrospect

Reflect on what just happened. Three escalation levels:

## Command

```
/retrospect [--plan | --fix] [subject]
```

| Mode | What happens |
|------|-------------|
| `/retrospect` | Record experience to `.agents/memory/experience/` |
| `/retrospect --plan` | Record experience + create backlog item in `.agents/backlog/` |
| `/retrospect --fix` | Record experience + create backlog item + implement the fix now |

**Subject** is optional. Without it — reflect on the current session trajectory. With it — reflect on the given topic or memory link.

## Workflow

### Default (`/retrospect`)

1. Review current session — what went wrong, what was learned.
2. Write to `.agents/memory/experience/YYYY-MM-DD-<slug>.md`.
3. Done.

### Plan (`/retrospect --plan`)

1. Everything from default.
2. Create actionable item in `.agents/backlog/YYYY-MM-DD-<slug>.md` with `source:` linking to the experience entry.

### Fix (`/retrospect --fix`)

1. Everything from `--plan`.
2. Implement the fix in code immediately.
3. Update the backlog item status or delete it.

## Rules

1. **Current trajectory first** — without subject, reflect on what just happened, not on old files.
2. **Always record** — every mode writes experience. `--plan` adds backlog. `--fix` adds backlog + code.
3. **Link sources** — backlog items reference the experience entry they came from.
4. **Merge, don't duplicate** — if an observation or experience on the same topic exists, update it.
