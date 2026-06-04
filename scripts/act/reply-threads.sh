#!/usr/bin/env bash
# Batch post in-thread replies to PR review threads.
#
# Replaces per-thread `gh api graphql` calls with one mutation that aliases all
# replies. The mapping comes from a TSV file (default: ./replies.tsv) with one
# row per thread: <thread_id>\t<reply body, may contain tabs and newlines>.
#
# Usage: reply-threads.sh [--dry-run] [--file PATH]
#   --file PATH   TSV file (default ./replies.tsv)
#   --dry-run     Validate the file and print the resulting mutation, but do not POST
#
# Each row must have the thread ID in the first tab-separated field; everything
# after the first tab is the body (preserves newlines).
set -euo pipefail

FILE="./replies.tsv"
DRY_RUN=false
while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --file)    FILE="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "error: gh required" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "error: gh not authenticated" >&2; exit 1; }
[[ -r "$FILE" ]] || { echo "error: cannot read $FILE" >&2; exit 1; }

# Build a single mutation with one alias per row.
aliases=()
i=0
while IFS=$'\t' read -r tid _rest; do
  [[ -z "${tid:-}" ]] && continue
  aliases+=("r$i")
  i=$((i + 1))
done < "$FILE"

if [[ "${#aliases[@]}" -eq 0 ]]; then
  echo "no rows in $FILE"; exit 0
fi

alias_blocks=""
for a in "${aliases[@]}"; do
  alias_blocks+="  $a: addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId: \$tid_$a, body: \$body_$a}) { comment { id } }
"
done

# Variable decls: $tid_r0:ID!, $body_r0:String!, $tid_r1:ID!, $body_r1:String!, ...
var_decls=""
for a in "${aliases[@]}"; do
  var_decls+="\$tid_$a:ID!,\$body_$a:String!,"
done
var_decls="${var_decls%,}"

mutation="mutation($var_decls) {
$alias_blocks}"

# Build gh args. Bodies must be passed via -f/--field; complex strings are fine.
gh_args=(-f "query=$mutation")
i=0
while IFS=$'\t' read -r tid body; do
  [[ -z "${tid:-}" ]] && continue
  a="r$i"
  gh_args+=(-f "tid_$a=$tid" -f "body_$a=$body")
  i=$((i + 1))
done < "$FILE"

if [[ "$DRY_RUN" == true ]]; then
  echo "would post $i replies via one mutation (${#aliases[@]} aliases)"
  echo "first 200 chars of mutation:"
  echo "  ${mutation:0:200}..."
  exit 0
fi

result="$(gh api graphql "${gh_args[@]}" 2>&1)" || { echo "$result" >&2; exit 1; }
if echo "$result" | jq -e '.errors' >/dev/null 2>&1; then
  echo "$result" | jq -c '.errors' >&2; exit 1
fi

ok="$(echo "$result" | jq '[.. | objects | select(has("comment")) | .comment.id] | length')"
echo "posted=$ok requested=$i"
