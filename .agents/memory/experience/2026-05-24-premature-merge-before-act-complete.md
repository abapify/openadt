---
date: 2026-05-24
context: https://github.com/abapify/openadt/pull/12
tags: [act, merge, workflow]
---

## What went wrong

PR merged while review threads still needed code fixes; later `/act` runs chased already-merged work.

## Why

Merge-ready declared after resolve-only or before P0–P3 finished on all threads.

## Proposed fix

P6 cycle guard in act skill — do not merge until P4 **and** P6 pass; reopened threads block merge.

## Scope

universal
