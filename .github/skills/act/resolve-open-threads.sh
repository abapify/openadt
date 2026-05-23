#!/usr/bin/env bash
# Resolve all open PR review threads via gh CLI (GraphQL).
# Usage: resolve-open-threads.sh [--dry-run] OWNER REPO PR_NUMBER
set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  shift
fi

OWNER="${1:?owner}"
REPO="${2:?repo}"
PR="${3:?pr number}"

export GH_TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
if [[ -z "${GH_TOKEN}" ]]; then
  echo "error: GH_TOKEN or GITHUB_TOKEN required" >&2
  exit 1
fi

list_query='query($o:String!,$r:String!,$pr:Int!) {
  repository(owner:$o, name:$r) {
    pullRequest(number:$pr) {
      reviewThreads(first:100) {
        nodes { id isResolved isOutdated path }
      }
    }
  }
}'

resolve_mutation='mutation($id:ID!) {
  resolveReviewThread(input:{threadId:$id}) {
    thread { isResolved }
  }
}'

threads_json="$(
  gh api graphql -f query="$list_query" -f o="$OWNER" -f r="$REPO" -F pr="$PR"
)"

if ! command -v jq >/dev/null 2>&1; then
  echo "error: jq required" >&2
  exit 1
fi

mapfile -t open_ids < <(
  echo "$threads_json" | jq -r '
    .data.repository.pullRequest.reviewThreads.nodes[]
    | select(.isResolved == false)
    | .id
  '
)

open_count="${#open_ids[@]}"
echo "open_threads=$open_count"

if [[ "$open_count" -eq 0 ]]; then
  echo "nothing to resolve"
  exit 0
fi

echo "$threads_json" | jq -r '
  .data.repository.pullRequest.reviewThreads.nodes[]
  | select(.isResolved == false)
  | "\(.id)\toutdated=\(.isOutdated)\t\(.path // "-")"
'

if [[ "$DRY_RUN" == true ]]; then
  echo "dry-run: would resolve $open_count thread(s)"
  exit 0
fi

resolved=0
for id in "${open_ids[@]}"; do
  result="$(gh api graphql -f query="$resolve_mutation" -f id="$id")"
  ok="$(echo "$result" | jq -r '.data.resolveReviewThread.thread.isResolved // false')"
  if [[ "$ok" == "true" ]]; then
    resolved=$((resolved + 1))
    echo "resolved $id"
  else
    echo "failed $id: $(echo "$result" | jq -c '.errors // .')" >&2
    exit 1
  fi
done

echo "resolved_total=$resolved open_remaining=$((open_count - resolved))"
