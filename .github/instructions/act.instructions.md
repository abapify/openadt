---
applyTo: ".github/**,specs/**,apps/**,.agents/**"
excludeAgent: "none"
---

# PR follow-up (`/act`)

When the user invokes **`/act`** or asks to fix CI/review on this PR, read **`.agents/skills/act/SKILL.md`** before changing code.

- Work P0 CI → P1 blocking review → P2 nits → P3 suggestions
- Reply in thread, then **Resolve conversation** on GitHub for every handled thread
- Do not claim merge-ready while unresolved review threads remain
