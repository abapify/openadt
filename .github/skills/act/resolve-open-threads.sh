#!/usr/bin/env bash
# Resolve all open PR review threads via gh CLI (GraphQL).
# Usage: resolve-open-threads.sh [--dry-run] OWNER REPO PR_NUMBER
#
# Token: GITHUB_TOKEN from the Copilot agent usually CANNOT resolve threads.
# Set Agents secret OPENADT_GH_PR_TOKEN (fine-grained: Pull requests Read+Write on this repo).
set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  shift
fi

OWNER="${1:?owner}"
REPO="${2:?repo}"
PR="${3:?pr number}"

pick_token() {
  for name in OPENADT_GH_PR_TOKEN GH_AW_GITHUB_TOKEN GH_TOKEN GITHUB_TOKEN; do
    if [[ -n "${!name:-}" ]]; then
      echo "using_token=$name" >&2
      printf '%s' "${!name}"
      return 0
    fi
  done
  return 1
}

if ! GH_TOKEN="$(pick_token)"; then
  echo "error: no token. Set Agents secret OPENADT_GH_PR_TOKEN (Pull requests: Read and write)." >&2
  exit 1
fi
export GH_TOKEN

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
  gh api graphql -f query="$list_query" -f o="$OWNER" -f r="$REPO" -F pr="$PR" 2>&1
)" || {
  echo "$threads_json" >&2
  if echo "$threads_json" | grep -qiE 'insufficient|permission|403|must have'; then
    echo "hint: GITHUB_TOKEN cannot resolve review threads. Add Agents secret OPENADT_GH_PR_TOKEN with Pull requests Read+Write on $OWNER/$REPO (Settings → Secrets and variables → Agents)." >&2
  fi
  exit 1
}

if ! command -v jq >/dev/null 2>&1; then
  echo "error: jq required" >&2
  exit 1
fi

if echo "$threads_json" | jq -e '.errors' >/dev/null 2>&1; then
  echo "$threads_json" | jq -c '.errors' >&2
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
  result="$(gh api graphql -f query="$resolve_mutation" -f id="$id" 2>&1)" || {
    echo "$result" >&2
    if echo "$result" | grep -qiE 'insufficient|permission|403|must have'; then
      echo "hint: use OPENADT_GH_PR_TOKEN (Agents secret), not GITHUB_TOKEN alone." >&2
    fi
    exit 1
  }
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
