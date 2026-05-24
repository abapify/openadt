# /act P6 evaluation checklist

Run **after P4 resolve**, before claiming merge-ready. Takes ~2 minutes.

## 1. Session retrospective (required if anything went wrong)

Answer in your closing summary or append to [RETROSPECT.md](RETROSPECT.md):

- [ ] Did we confuse review tools (Codacy vs GitHub Code Scanning vs Dependabot vs Copilot review)?
- [ ] Did we resolve threads without code fixes or in-thread replies?
- [ ] Did we claim fix counts without querying the correct API?
- [ ] Did we use whole-file semgrep exclusions instead of line-specific suppressions?
- [ ] Did we edit PR title/body without explicit user request?

If **any** box is “yes”: write **what / root cause / prevention** and update **one** durable sink (see routing table in [review.instructions.md](../../../.github/instructions/review.instructions.md)).

## 2. Cycle detection (required every /act)

- [ ] **Reopened thread:** any thread was resolved earlier in this PR then commented on again → **stop**, list threads, do **not** merge; user must confirm next step.
- [ ] **Same rule 2+ times:** same Codacy/Semgrep/rule ID flagged again after a fix commit → verify fix is on **current HEAD** and suppression/answer is in the right sink; do **not** re-merge blindly.
- [ ] **Empty /act loop:** this is the 2+ `/act` invocation on the same PR with **no new product commits** since last run → **stop** and report cycle; diagnose in retrospective.

## 3. Durable knowledge (optional unless retrospective triggered)

- [ ] Workflow/process lesson → [SKILL.md](SKILL.md) (and RETROSPECT.md if recurring)
- [ ] API/tool confusion → [review.instructions.md](../../../.github/instructions/review.instructions.md)
- [ ] Codacy/domain false positive → [review.md](../../../.codacy/instructions/review.md)

## 4. Agent memory reminder (optional)

If the lesson is user-specific (e.g. “always use Codacy MCP on this org”), suggest the user paste the snippet from [SKILL.md § Memory reminder template](SKILL.md#memory-reminder-template) into their Cursor user rules — do not commit private landscape data.
