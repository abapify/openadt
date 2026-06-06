---
date: 2026-06-06
context: https://github.com/abapify/openadt/pull/50
tags: [codescene, cli, ci]
---

## What went wrong

Tried to address cubic's "CodeScene CLI version should be pinned" thread by setting `CS_CLI_VERSION=2.4.4` in `scripts/ci-install-codescene-cli.sh`. CI install step immediately 403'd.

## Why

The versioned download endpoint requires `CS_ACCESS_TOKEN` on the request (exposed to the delta step, not the install step), and the guessed version string does not exist.

## Proposed fix

When a reviewer asks to pin an externally-installed binary that's behind auth, do a `curl -fsSLI` check on the candidate URL **with and without the token header** before committing — and if the public path is `latest`, reply in-thread explaining the tradeoff rather than shipping a half-fix.

## Scope

project
