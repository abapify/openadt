# Memory Usage Skill

Store and retrieve session context between agent conversations.

## Location

```
.agents/
├── memories/           # Session notes (timestamped)
│   ├── README.md
│   └── YYYY-MM-DD-{topic}.md
└── skills/            # Reusable capabilities
    └── memory-usage/
        └── SKILL.md   # This file
```

## Creating Memories

### When to Create
- After debugging sessions with key findings
- When work spans multiple conversations
- To preserve configuration details
- Before switching contexts

### Format
Filename: `YYYY-MM-DD-{brief-topic}.md`

Structure:
```markdown
# {Topic} - {Date}

## Modified Files
- `path/to/file.ext` - What changed and why

## Key Findings
- Discovery 1
- Discovery 2

## Root Cause (if applicable)
What was the actual problem vs symptoms.

## Current Status
- ✅ Completed items
- ⚠️ Blockers or pending

## Next Steps
1. Action item
2. Action item

## Commands / Config
```bash
# Working commands
```
```

### ⚠️ PII Safety (REQUIRED)

Memory files may be published to public repositories. ALWAYS redact:

| PII Type | Replace With |
|----------|--------------|
| System IDs | `<SID>` or remove |
| Destination IDs | `<DESTINATION_ID>` |
| Usernames | `<USER>` |
| Hostnames | `<HOST>` |
| Client numbers | `<CLIENT>` or remove |
| Real paths | Generic paths |

**Add footer note:**
```markdown
---
*Note: This session log is anonymized for public sharing.*
```

## Usage

### Store Context
```bash
# Agent will create/update memory files in .agents/memories/
# After significant work, agent should summarize to memories/
```

### Retrieve Context
1. Check `.agents/memories/` for relevant timestamped files
2. Read most recent matching topic
3. Continue from "Current Status" / "Next Steps"

### Maintenance
- Keep only last 10 sessions in memories/
- Archive older to `memories/archive/`
- Update README.md index

## Integration

Memory files are automatically:
- Retrieved at session start (relevant topics)
- Referenced in code citations
- Used to avoid repeating debug steps
