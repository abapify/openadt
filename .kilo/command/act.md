---
description: Run /act on a PR — fix CI, address review threads, resolve.
subtask: true
---

Run the `/act` workflow on PR $ARGUMENTS.

1. Load the act skill: `.agents/skills/act/SKILL.md`
2. Run `gh pr view $ARGUMENTS --json headRefName,headRefOid,baseRefName,state`
3. Discover the real head ref and push target
4. Follow the P0–P6 sequence from the act skill
5. After P4 (resolve), run the verify block
6. Report merge-ready status
