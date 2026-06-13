# 2026-06-13 — Misrouted "course agent / cli-agent-orchestrator" request

## Context
- User opened the session asking to work on
  `https://github.com/awslabs/cli-agent-orchestrator/issues/264` and to implement a
  "course agent CLI" end-to-end and submit a PR from a fork.
- The cloud-agent sandbox is checked out against **`abapify/openadt`** (OpenADT, an
  SAP ADT CLI). The GitHub token is scoped to that repo; there is no fork of
  `awslabs/cli-agent-orchestrator` in this workspace, and the toolchain
  (`mvnw`, Bun verify block, `verify-spec-sync`, ESLint tier) is wired for
  OpenADT, not a Python/TypeScript orchestrator harness.
- OpenADT has no "course agent" concept. The product surface is
  `openadt fetch`, `openadt proxy`, and the MCP launcher
  (`specs/vision.md`, `specs/mcp.md`).

## What was done
- Confirmed `git remote -v` points at `abapify/openadt`, not the AWS repo.
- Asked the user to disambiguate (different repo / stay on OpenADT with a real
  feature / stop). User selected **"You meant a different repo"** and asked for
  an analysis + handoff prompt for a *next* agent.

## Decision
- Do not start implementation in this workspace. Routing a feature PR against
  the wrong repo, with the wrong language/toolchain, on an issue that has no
  spec here, would burn CI and produce a PR that cannot be merged.
- File this as an observation so a future agent (or the user) can pick it up
  in the correct repo.

## Handoff prompt for a next agent
> You are picking up work originally intended for
> `awslabs/cli-agent-orchestrator` issue #264 ("course agent CLI"). The previous
> session was running inside the OpenADT sandbox and could not reach the AWS
> repo. Do the following, in order, before writing any code:
>
> 1. Verify the target. In a clone of `awslabs/cli-agent-orchestrator`, read
>    `README.md`, `CONTRIBUTING.md`, and the docs that describe how to add a
>    new agent to the CLI. Do not rely on the previous session's notes — the
>    project structure here is unrelated.
> 2. Read issue #264 in full, including any linked design docs or comments.
>    Confirm scope, the CLI surface the new agent must expose, and the
>    definition of "done" the maintainers expect.
> 3. Check the project's existing agent implementations (look for an `agents/`
>    or equivalent directory plus their tests). Mirror their file layout,
>    naming, and test conventions.
> 4. Open a fork, create a branch named for the change, and push there. Do
>    not open a PR from a branch inside the upstream repo.
> 5. Implement, run the project's own lint + test suite, then open the PR
>    from the fork with a body that links issue #264.
> 6. After every commit, re-run the verify block the project documents
>    (don't reuse OpenADT's `mvnw` / `bun scripts/verify-*` chain — that is
>    the wrong toolchain for this repo).
>
> Do not hallucinate the agent's contract from the issue title. If the docs
> don't say how to add a new agent, file that as a comment on the issue
> before implementing.
