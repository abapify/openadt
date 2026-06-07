# Orchestrator doctrine for cloud-agent PR work

**When:** orchestrating a `/act` or any multi-step PR workflow in the OpenADT
cloud-agent workspace.

**Principle:** discover the real PR source branch before any push; trust live CI
status, not agent-claimed local checks; batch independent reads in one turn;
delegate to specialized subagents rather than reimplementing.

**Heuristics:**
1. Always `gh pr view N --json headRefName,baseRefName` before any push.
   `gh pr checkout` creates `pr-NN` tracking `origin/pr-NN` — that's NOT the
   PR's actual source branch. Push to `origin/<headRefName>`.
2. The live `gh pr checks N` result is the only evidence of CI pass. Local
   `cs delta` is a smell-check.
3. Batch independent reads (multiple `read`/`glob`/`grep` in one turn; multiple
   `task` calls in one turn). Bound parallel fan-out to 3–5.
4. Prefer `explore` (read-only) for research. Use `general` (multi-step) only
   for work with writes.
5. Pass file paths, not topics, to subagents. Specify return format.
6. After 3 pushes on the same branch per `/act` cycle, stop and report.
7. Scratch files: always `/tmp/agent_*/`. Never the worktree root.
8. macOS: `gsed`/`gdate`. Linux: `sed`/`date`. Detect once via `uname -s`.
9. **Never pin models in agent configs** — let the agent inherit the active
   plan's default. Pinned models route to whatever provider they belong to
   even if the user is on a different plan, burning tokens against a balance
   they didn't intend to use.

**Source:** PR #62 `/act` session (2026-06-07). Pushed to wrong branch
(`origin/pr-62` instead of `origin/feat/plan-sync-cursor-issues`), cost one
extra CI cycle. First refactor agent's local verification was inaccurate.
A follow-up synthesis agent pinned `anthropic/claude-sonnet-4-20250514` in
kilo.json which routed every subagent invocation to a paid provider the
user did not want to activate.
