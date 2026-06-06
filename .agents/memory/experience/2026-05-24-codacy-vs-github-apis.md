---
date: 2026-05-24
context: https://github.com/abapify/openadt/pull/12
tags: [codacy, review, api]
---

## What went wrong

Agent queried GitHub Code Scanning / invented counts instead of Codacy or PR review threads; claimed "7 issues fixed" without matching API evidence.

## Why

Codacy, Code Scanning, Code Quality (Copilot review), and Dependabot treated as one bucket.

## Proposed fix

REVIEW.md tool table; P6 requires naming the source before claiming fix counts.

## Scope

universal
