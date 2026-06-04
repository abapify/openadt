#!/usr/bin/env bash
# Single-call PR state dump for /act: HEAD SHA, mergeability, open threads (table),
# and required CI status (excluding AI reviewers like cubic / CodeRabbit).
#
# Replaces 4-6 separate `gh pr view` / `gh pr checks` invocations per /act run.
#
# Usage: pr-state.sh OWNER REPO PR_NUMBER
# Output: key=value lines (HEAD_SHA, HEAD_REF, MERGEABLE, MERGE_STATE,
#         OPEN_THREADS, CI_REQUIRED_PENDING) followed by a 4-column TSV table
#         of open threads:
#           id<TAB>author<TAB>path:line<TAB>body[:120]
#         (newlines and tabs in the body are collapsed to spaces so each row
#         stays on a single line; pagination uses pageInfo.hasNextPage.)
set -euo pipefail

OWNER="${1:?owner}"
REPO="${2:?repo}"
PR="${3:?pr number}"

for bin in gh jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "error: $bin required" >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "error: gh not authenticated" >&2; exit 1; }

# Page through reviewThreads (default 100 per page) to avoid silent truncation.
# `gh api graphql --paginate` does not work for arbitrary connections, so loop
# manually using `pageInfo.endCursor`.
#
# The GraphQL query needs an `after: $after` clause on every call after the
# first. We build it as one literal string with the cursor spliced in
# (escaping any quotes in the cursor).
threads_json_all='[]'
cursor=""
has_next=true
while [[ "$has_next" == "true" ]]; do
  if [[ -z "$cursor" ]]; then
    # shellcheck disable=SC2016  # GraphQL $-prefixed variables are literal.
    var_decls='$o:String!,$r:String!,$pr:Int!,$n:Int!'
    after_clause=""
  else
    # Escape backslashes and double-quotes in the cursor for embedding in a
    # double-quoted string (GraphQL accepts only double-quoted cursors).
    # The cursor is spliced into the query as a literal; we do NOT also pass
    # it as a $-prefixed variable (would trigger an "unused variable" error).
    esc_cursor="${cursor//\\/\\\\}"
    esc_cursor="${esc_cursor//\"/\\\"}"
    # shellcheck disable=SC2016  # GraphQL $-prefixed variables are literal.
    var_decls='$o:String!,$r:String!,$pr:Int!,$n:Int!'
    after_clause=", after: \"$esc_cursor\""
  fi

  page_query=$(cat <<EOF
query($var_decls) {
  repository(owner: \$o, name: \$r) {
    pullRequest(number: \$pr) {
      headRefOid
      headRefName
      mergeable
      state
      url
      reviewThreads(first: \$n$after_clause) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id isResolved isOutdated
          comments(first: 1) { nodes { author { login } path line body } }
        }
      }
    }
  }
}
EOF
)

  args=( -f query="$page_query" -f o="$OWNER" -f r="$REPO" -F pr="$PR" -F n=100 )
  state_json="$(gh api graphql "${args[@]}" 2>&1)" || { echo "$state_json" >&2; exit 1; }
  if echo "$state_json" | jq -e '.errors' >/dev/null 2>&1; then
    echo "$state_json" | jq -c '.errors' >&2; exit 1
  fi
  if echo "$state_json" | jq -e '.data.repository.pullRequest == null' >/dev/null 2>&1; then
    echo "error: pull request #$PR not found in $OWNER/$REPO" >&2; exit 1
  fi

  threads_json_all="$(jq -s '.[0] + .[1].data.repository.pullRequest.reviewThreads.nodes' \
    <(echo "$threads_json_all") <(echo "$state_json"))"
  has_next="$(echo "$state_json" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.hasNextPage')"
  cursor="$(echo "$state_json" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.endCursor // empty')"
done

read -r head_sha head_ref mergeable url <<<"$(jq -r '
  [
    .data.repository.pullRequest.headRefOid,
    .data.repository.pullRequest.headRefName,
    (.data.repository.pullRequest.mergeable // "UNKNOWN" | ascii_downcase),
    .data.repository.pullRequest.url
  ] | @tsv
' <<<"$state_json")"

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

# gh pr checks exits 1 with "no checks reported" when no checks have run yet.
checks_json="$(gh pr checks "$PR" --repo "$OWNER/$REPO" --json name,state,bucket 2>&1)" || {
  if echo "$checks_json" | grep -qi "no checks reported"; then
    checks_json="[]"
  else
    echo "$checks_json" >&2; exit 1
  fi
}

open_count="$(jq '[.[] | select(.isResolved==false)] | length' <<<"$threads_json_all")"

# Required CI status: any check whose bucket is not 'pass' (i.e. it was NOT a
# success) and whose state is not SKIPPED or NEUTRAL. Excludes AI reviewers by name.
ci_required_pending="$(jq -r '
  [
    .[]
    | select(.bucket != "pass")
    | select(.state != "SKIPPED" and .state != "NEUTRAL")
    | select(.name | test("(?i)(cubic|code\\s*rabbit|amazon\\s*q|qodo|chatgpt\\s*codex|gemini|kilo)"; "x") | not)
  ] | length
' <<<"$checks_json")"

echo "HEAD_SHA=$head_sha"
echo "HEAD_REF=$head_ref"
echo "URL=$url"
echo "MERGEABLE=$mergeable"
echo "MERGE_STATE=$merge_state"
echo "OPEN_THREADS=$open_count"
echo "CI_REQUIRED_PENDING=$ci_required_pending"
echo
echo "OPEN_THREADS_TABLE:"
jq -r '
  .[]
  | select(.isResolved == false)
  | . as $t
  | ($t.comments.nodes[0] // {}) as $c
  | "\($t.id)\t\($c.author.login // "-")\t\([$c.path // "-", ($c.line // "-" | tostring)] | join(":"))\t\($c.body // "" | gsub("[\n\t]"; " ") | .[0:120])"
' <<<"$threads_json_all"
