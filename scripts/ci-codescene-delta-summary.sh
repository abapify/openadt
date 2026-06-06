#!/usr/bin/env bash
# Markdown job summary from cs delta --output-format json (GitHub Actions GITHUB_STEP_SUMMARY).
set -euo pipefail

json="${1:-codescene-delta.json}"
summary="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

if [[ ! -f "${json}" ]]; then
  echo "No CodeScene delta JSON at ${json}." >>"${summary}"
  exit 0
fi

{
  echo "## CodeScene delta"
  echo
  echo "| File | New score | Active findings |"
  echo "|------|-----------|-----------------|"
  jq -r '
    .[]
    | "| `\(.name)` | \(.["new-score"] // "n/a") | \(
        [.findings[]? | select(.["change-type"] != "fixed" and .["change-type"] != "improved")]
        | length
      ) |"
  ' "${json}"
  echo
  below="$(jq -r '
    [.[] | select(.["new-score"] != null and .["new-score"] < 10) | .name]
    | .[]
  ' "${json}" || true)"
  if [[ -n "${below}" ]]; then
    echo "**Files below 10.0:**"
    while IFS= read -r file; do
      [[ -n "${file}" ]] && echo "- \`${file}\`"
    done <<<"${below}"
    echo
  fi
  echo "Full output: \`codescene-delta.json\` artifact."
} >>"${summary}"
