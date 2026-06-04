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
#   --dry-run     Validate the file (parses rows, reports planned batch
#                 count of up to --batch-size per GraphQL mutation), but do
#                 not POST anything
set -euo pipefail

FILE="./replies.tsv"
DRY_RUN=false
BATCH_SIZE=6
while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --file)        FILE="$2"; shift 2 ;;
    --dry-run)     DRY_RUN=true; shift ;;
    --batch-size)  BATCH_SIZE="$2"; shift 2 ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
done

# Validate --batch-size: must be a positive integer. Non-positive values would
# cause a division-by-zero in the dry-run chunk count or a non-advancing loop
# at runtime. Reject non-integer / non-positive input.
if ! [[ "$BATCH_SIZE" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: --batch-size must be a positive integer (got: $BATCH_SIZE)" >&2
  exit 2
fi

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
i=0            # success-row counter (index into threads/bodies)
lineno=0       # actual TSV file line number (for warnings)
TAB=$'\t'
while IFS= read -r line || [[ -n "$line" ]]; do
  lineno=$((lineno + 1))
  [[ -z "$line" ]] && continue
  # Strip a leading tab (would mean empty thread id).
  if [[ "$line" != *"$TAB"* ]]; then
    echo "warn: line $lineno has no tab separator, skipping" >&2
    continue
  fi
  tid="${line%%"$TAB"*}"
  body="${line#*"$TAB"}"
  [[ -z "$tid" ]] && { echo "warn: empty thread id on line $lineno" >&2; continue; }
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
  chunks=$(( (count + BATCH_SIZE - 1) / BATCH_SIZE ))
  echo "would post $count replies in $chunks batch(es) of up to $BATCH_SIZE"
  exit 0
fi

# Run in chunks of BATCH_SIZE to stay under GitHub's GraphQL query
# complexity limit (RESOURCE_LIMITS_EXCEEDED starts around 7-8 mutations per
# query depending on payload size).
posted=0
for ((start=0; start<count; start+=BATCH_SIZE)); do
  end=$(( start + BATCH_SIZE ))
  [[ $end -gt $count ]] && end=$count

  chunk_aliases=()
  for ((i=start; i<end; i++)); do chunk_aliases+=("r$i"); done

  chunk_alias_blocks=""
  for a in "${chunk_aliases[@]}"; do
    chunk_alias_blocks+="  $a: addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId: \$tid_$a, body: \$body_$a}) { comment { id } }
"
  done
  chunk_var_decls=""
  for a in "${chunk_aliases[@]}"; do
    chunk_var_decls+="\$tid_$a:ID!,\$body_$a:String!,"
  done
  chunk_var_decls="${chunk_var_decls%,}"
  chunk_mutation="mutation($chunk_var_decls) {
$chunk_alias_blocks}"

  chunk_args=(-f "query=$chunk_mutation")
  for ((i=start; i<end; i++)); do
    a="r$i"
    chunk_args+=(-f "tid_$a=${threads[i]}" -f "body_$a=${bodies[i]}")
  done

  result="$(gh api graphql "${chunk_args[@]}" 2>&1)" || { echo "$result" >&2; exit 1; }
  if echo "$result" | jq -e '.errors' >/dev/null 2>&1; then
    echo "batch [$start..$((end-1))] error:" >&2
    echo "$result" | jq -c '.errors' >&2; exit 1
  fi
  ok="$(echo "$result" | jq '[.. | objects | select(has("comment")) | .comment.id] | length')"
  posted=$((posted + ok))
  echo "batch [$start..$((end-1))] posted=$ok"
done

echo "posted=$posted requested=$count"
