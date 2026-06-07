---
date: 2026-06-07
context: https://github.com/abapify/openadt/pull/62
tags: [gh-cli, pr, branches]
---

In the `abapify/openadt` repo, a PR's source branch is **not** the local `pr-NN` branch that `gh pr checkout` creates. The PR head is whichever branch `gh pr view N --json headRefName` returns (e.g. `feat/plan-sync-cursor-issues` for PR #62). Always discover the real head ref and push to it; `origin/pr-NN` is a convenience ref GitHub does not use as the PR's head.
