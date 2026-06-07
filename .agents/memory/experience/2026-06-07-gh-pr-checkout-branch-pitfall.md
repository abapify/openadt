---
date: 2026-06-07
context: https://github.com/abapify/openadt/pull/62
tags: [git, gh-cli, branches, act]
---

## What went wrong

On PR #62 we ran `gh pr checkout 62` to start working. It created a local branch named **`pr-62`** tracking **`origin/pr-62`** — a remote ref that does not exist as the PR's source. The first 4 commits were pushed to `origin/pr-62`, but the PR's actual `headRefName` is `feat/plan-sync-cursor-issues`. The PR head never moved, so CI kept analyzing the pre-refactor SHA and CodeScene delta stayed red.

## Why

`gh pr checkout N` derives the local branch name from the PR number, not from the PR's source branch. It also creates a `refs/remotes/origin/pr-NN` ref that is not the PR's `headRefName`. Pushing back to `origin/pr-NN` updates a ref GitHub does not consult for the PR — the PR head is whatever `headRefName` resolves to.

The trap is silent: `git push` succeeds, the log looks right, but `gh pr view N --json headRefOid` does not change.

## Proposed fix

- **Always discover the real source branch first**: `gh pr view N --json headRefName,headRefOid,baseRefName`.
- **Do not use `gh pr checkout`** for ongoing work. Instead:
  ```bash
  gh pr view N --json headRefName,baseRefName
  git fetch origin
  git switch -c <headRefName> origin/<headRefName>
  git push origin HEAD
  ```
  This way commits land on the same ref GitHub tracks for the PR.
- After pushing, **verify the PR head moved**: `gh pr view N --json headRefOid` should match `git rev-parse HEAD` on the local branch you just pushed.

## Scope

project
