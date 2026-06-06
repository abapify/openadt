---
date: 2026-06-05
context: https://github.com/abapify/openadt/pull/42
tags: [act, stale-threads, force-push]
---

## What went wrong

PR #42 carried 12+ open review threads pointing at files not in the current PR diff. An earlier scope included a Java CLI shim that was force-pushed out; the auto-reviews were never pruned.

## Why

Reviewers anchor a thread to a `path:line` at review time, but the file can move out of the PR between force-pushes; the PR UI still shows the thread as "open".

## Proposed fix

On every `/act`, after `pr-state.sh`, run `git diff main..HEAD --stat` to confirm the files the threads reference are actually in the current PR. Threads whose path is outside the current diff are resolved as **stale** with an in-thread reply.

## Scope

universal
