#!/usr/bin/env bash
# Single-call PR state dump for /act: HEAD SHA, mergeability, open threads (table),
# and required CI status (excluding AI reviewers like cubic / CodeRabbit).
#
# Replaces 5-6 separate `gh pr view` / `gh pr checks` invocations per /act run.
#
# Usage: pr-state.sh OWNER REPO PR_NUMBER
# Output: key=value lines (HEAD_SHA, MERGEABLE, MERGE_STATE, OPEN_THREADS, CI_REQUIRED_PENDING)
#         followed by a TSV table of open threads (id<TAB>author<TAB>path:line<TAB>body[:120]).
set -euo pipefail

OWNER="${1:?owner}"
REPO="${2:?repo}"
PR="${3:?pr number}"

for bin in gh jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "error: $bin required" >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "error: gh not authenticated" >&2; exit 1; }

state_query='query($o:String!,$r:String!,$pr:Int!) {
  repository(owner:$o, name:$r) {
    pullRequest(number:$pr) {
      headRefOid
      headRefName
      mergeable
      state
      url
      reviewThreads(first:100) {
        nodes {
          id isResolved isOutdated
          comments(first:1) { nodes { author { login } path line body } }
        }
      }
    }
  }
}'

state_json="$(gh api graphql -f query="$state_query" -f o="$OWNER" -f r="$REPO" -F pr="$PR" 2>&1)" || {
  echo "$state_json" >&2; exit 1;
}
if echo "$state_json" | jq -e '.errors' >/dev/null 2>&1; then
  echo "$state_json" | jq -c '.errors' >&2; exit 1;
fi

read -r head_sha head_ref mergeable url <<<"$(echo "$state_json" | jq -r '
  [
    .data.repository.pullRequest.headRefOid,
    .data.repository.pullRequest.headRefName,
    (.data.repository.pullRequest.mergeable // "UNKNOWN" | ascii_downcase),
    .data.repository.pullRequest.url
  ] | @tsv
')"

# GitHub may report mergeable=MERGEABLE (REST) or mergeable=mergeable (GraphQL). Normalize.
case "$mergeable" in
  mergeable|MERGEABLE) mergeable="MERGEABLE" ;;
  conflicting|CONFLICTING) mergeable="CONFLICTING" ;;
  *) mergeable="${mergeable^^}" ;;
esac

# mergeStateStatus comes from the REST Checks API (not in the GraphQL PR object).
rest_json="$(gh pr view "$PR" --repo "$OWNER/$REPO" --json mergeStateStatus 2>&1)" || {
  echo "$rest_json" >&2; exit 1;
}
merge_state="$(echo "$rest_json" | jq -r '.mergeStateStatus // "UNKNOWN"')"

open_count="$(echo "$state_json" | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved==false)] | length')"

# Required CI status (exclude AI reviewers that can stay PENDING without blocking merge).
checks_json="$(gh pr checks "$PR" --repo "$OWNER/$REPO" --json name,state,bucket 2>&1)" || {
  echo "$checks_json" >&2; exit 1;
}
ci_required_pending="$(echo "$checks_json" | jq -r '
  [
    .[]
    | select(.bucket == null)   # null bucket = required (non-artifact)
    | select(.name | test("(?i)(cubic|code\\s*rabbit|amazon\\s*q|qodo|chatgpt\\s*codex|gemini)"; "x") | not)
    | select(.state != "SUCCESS" and .state != "SKIPPED")
  ] | length
')"

echo "HEAD_SHA=$head_sha"
echo "HEAD_REF=$head_ref"
echo "URL=$url"
echo "MERGEABLE=$mergeable"
echo "MERGE_STATE=$merge_state"
echo "OPEN_THREADS=$open_count"
echo "CI_REQUIRED_PENDING=$ci_required_pending"
echo
echo "OPEN_THREADS_TABLE:"
echo "$state_json" | jq -r '
  .data.repository.pullRequest.reviewThreads.nodes[]
  | select(.isResolved == false)
  | . as $t
  | ($t.comments.nodes[0] // {}) as $c
  | "\($t.id)\t\($c.author.login // "-")\t\($c.path // "-")\t\($c.line // "-" | tostring)\t\($c.body // "" | gsub("\n"; " ") | .[0:120])"
' | sed 's/^/  /'
