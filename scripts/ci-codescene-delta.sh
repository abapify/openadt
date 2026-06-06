#!/usr/bin/env bash
# Run cs delta via codescene/codescene-mcp (CLI baked at image build; no runtime download).
# MCP entrypoint is overridden — we only use the embedded cs binary.
# Image: https://hub.docker.com/r/codescene/codescene-mcp
set -euo pipefail

base_ref="${1:?usage: ci-codescene-delta.sh <base-ref> [head-ref]}"
head_ref="${2:-HEAD}"
workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
image="${CODESCENE_MCP_IMAGE:-codescene/codescene-mcp@sha256:70859e2a5bec1fb541fa76357bada344745bbc92db5576ef1a14bf19427e7dd5}"

if [[ -z "${CS_ACCESS_TOKEN:-}" ]]; then
  echo "CS_ACCESS_TOKEN is required" >&2
  exit 1
fi

docker run --rm \
  -v "${workspace}:${workspace}" \
  -w "${workspace}" \
  -e CS_ACCESS_TOKEN \
  --entrypoint cs \
  "${image}" \
  delta "origin/${base_ref}" "${head_ref}" --error-on-warnings
