#!/usr/bin/env bash
# Batch post in-thread replies to PR review threads.
#
# Replaces per-thread `gh api graphql` calls with one mutation that aliases all
# replies. The mapping comes from a TSV file (default: ./replies.tsv) with one
# row per thread: <thread_id>\t<reply body>.
#
# Newlines in the reply body MUST be escaped as the literal sequence `\n`
# (backslash + n). The script decodes them before sending. Tabs in the body
# would break the TSV format; escape them too if needed (rare).
#
# Usage: reply-threads.sh [--dry-run] [--file PATH]
#   --file PATH   TSV file (default ./replies.tsv)
#   --dry-run     Validate the file and print the resulting mutation, but do not POST
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

for bin in gh jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "error: $bin required" >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "error: gh not authenticated" >&2; exit 1; }
[[ -r "$FILE" ]] || { echo "error: cannot read $FILE" >&2; exit 1; }

# Single-pass parse: read the TSV file into memory, splitting each line on the
# first TAB. Reassemble into per-thread (id, body) pairs. Standard `while read`
# only handles one TSV row per line; this script does not attempt to allow
# literal newlines in bodies — it requires `\n` escapes (see header). That keeps
# the TSV unambiguous and the file readable.
threads=()
bodies=()
i=0
TAB=$'\t'
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" ]] && continue
  # Strip a leading tab (would mean empty thread id).
  if [[ "$line" != *"$TAB"* ]]; then
    echo "warn: line $((i+1)) has no tab separator, skipping" >&2
    continue
  fi
  tid="${line%%"$TAB"*}"
  body="${line#*"$TAB"}"
  [[ -z "$tid" ]] && { echo "warn: empty thread id on line $((i+1))" >&2; continue; }
  # Decode \n (and \t) escapes in the body.
  body="${body//\\n/$'\n'}"
  body="${body//\\t/	}"
  threads+=("$tid")
  bodies+=("$body")
  i=$((i + 1))
done < "$FILE"

count="${#threads[@]}"
if [[ "$count" -eq 0 ]]; then
  echo "no rows in $FILE"; exit 0
fi

# Build a single mutation with one alias per row.
aliases=()
for ((idx=0; idx<count; idx++)); do
  aliases+=("r$idx")
done

alias_blocks=""
for a in "${aliases[@]}"; do
  alias_blocks+="  $a: addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId: \$tid_$a, body: \$body_$a}) { comment { id } }
"
done

var_decls=""
for a in "${aliases[@]}"; do
  var_decls+="\$tid_$a:ID!,\$body_$a:String!,"
done
var_decls="${var_decls%,}"

mutation="mutation($var_decls) {
$alias_blocks}"

gh_args=(-f "query=$mutation")
for ((idx=0; idx<count; idx++)); do
  a="r$idx"
  gh_args+=(-f "tid_$a=${threads[idx]}" -f "body_$a=${bodies[idx]}")
done

if [[ "$DRY_RUN" == true ]]; then
  echo "would post $count replies via one mutation (${#aliases[@]} aliases)"
  echo "first 240 chars of mutation:"
  echo "  ${mutation:0:240}..."
  exit 0
fi

result="$(gh api graphql "${gh_args[@]}" 2>&1)" || { echo "$result" >&2; exit 1; }
if echo "$result" | jq -e '.errors' >/dev/null 2>&1; then
  echo "$result" | jq -c '.errors' >&2; exit 1
fi

ok="$(echo "$result" | jq '[.. | objects | select(has("comment")) | .comment.id] | length')"
echo "posted=$ok requested=$count"
