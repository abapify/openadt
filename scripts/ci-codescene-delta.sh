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
  echo "::error title=CodeScene CI not configured::CS_ACCESS_TOKEN is missing. Add it under Settings → Secrets and variables → Actions (PAT from https://codescene.io/users/me/pat)." >&2
  exit 1
fi

log="$(mktemp)"
trap 'rm -f "${log}"' EXIT

set +e
docker run --rm \
  -v "${workspace}:${workspace}" \
  -w "${workspace}" \
  -e CS_ACCESS_TOKEN \
  --entrypoint cs \
  "${image}" \
  delta "origin/${base_ref}" "${head_ref}" --error-on-warnings 2>&1 | tee "${log}"
ec="${PIPESTATUS[0]}"
set -e

if [[ "${ec}" -ne 0 ]]; then
  if grep -q 'License check failed' "${log}"; then
    echo "::error title=CodeScene PAT rejected (403)::CS_ACCESS_TOKEN is set but CodeScene rejected it. Refresh the PAT at https://codescene.io/users/me/pat and update the CS_ACCESS_TOKEN secret." >&2
  fi
  exit "${ec}"
fi
