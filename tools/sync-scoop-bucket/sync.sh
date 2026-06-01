#!/usr/bin/env bash
# Sync packaging/scoop/openadt.json to Scoop install sources.
# - CI (GITHUB_TOKEN): branch scoop-bucket on this repo (always).
# - abapify/scoop-bucket: git push or Contents API when OPENADT_SCOOP_BUCKET_TOKEN is set.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
manifest="${root}/packaging/scoop/openadt.json"
branch="main"
external_repo="${OPENADT_SCOOP_BUCKET_REPO:-abapify/scoop-bucket}"
same_repo_branch="${OPENADT_SCOOP_BRANCH:-scoop-bucket}"

if [[ ! -f "${manifest}" ]]; then
  echo "Missing ${manifest}" >&2
  exit 1
fi

version="$(grep -m1 '"version"' "${manifest}" | sed 's/.*: "\(.*\)".*/\1/')"

git_token() {
  if [[ -n "${OPENADT_SCOOP_BUCKET_TOKEN:-}" ]]; then
    printf '%s' "${OPENADT_SCOOP_BUCKET_TOKEN}"
  elif [[ -n "${GH_TOKEN:-}" ]]; then
    printf '%s' "${GH_TOKEN}"
  elif [[ -n "${GITHUB_TOKEN:-}" ]]; then
    printf '%s' "${GITHUB_TOKEN}"
  else
    return 1
  fi
}

scoop_bucket_token() {
  if [[ -n "${OPENADT_SCOOP_BUCKET_TOKEN:-}" ]]; then
    printf '%s' "${OPENADT_SCOOP_BUCKET_TOKEN}"
  fi
}

remote_exists() {
  git ls-remote --heads "$1" "$2" 2>/dev/null | grep -q .
}

manifest_base64() {
  if base64 --help 2>&1 | grep -q -- '-w'; then
    base64 -w0 "${manifest}"
  else
    base64 <"${manifest}" | tr -d '\n'
  fi
}

push_manifest_via_gh_contents() {
  local repo_slug="$1"
  local token="$2"
  export GH_TOKEN="${token}"
  local sha=""
  sha="$(gh api "repos/${repo_slug}/contents/openadt.json" --jq .sha 2>/dev/null || true)"
  local content
  content="$(manifest_base64)"
  local api_args=(
    --method PUT
    "repos/${repo_slug}/contents/openadt.json"
    -f "message=chore(release): openadt ${version}"
    -f "content=${content}"
  )
  if [[ -n "${sha}" ]]; then
    api_args+=(-f "sha=${sha}")
  fi
  gh api "${api_args[@]}" >/dev/null
  echo "Updated ${repo_slug}@main via Contents API (openadt ${version})"
}

push_manifest_to_repo() {
  local repo_slug="$1"
  local target_branch="$2"
  local token="$3"
  local auth_url="https://x-access-token:${token}@github.com/${repo_slug}.git"

  work="$(mktemp -d)"
  cleanup() {
    rm -rf "${work}"
  }
  trap cleanup EXIT

  if remote_exists "${auth_url}" "${target_branch}"; then
    git clone --branch "${target_branch}" --depth 1 "${auth_url}" "${work}"
  else
    git init "${work}"
    git -C "${work}" checkout -b "${target_branch}"
    git -C "${work}" remote add origin "${auth_url}"
  fi

  cp "${manifest}" "${work}/openadt.json"
  cd "${work}"
  git add openadt.json

  if git diff --cached --quiet; then
    echo "${repo_slug}@${target_branch} already up to date (${version})."
    return 0
  fi

  git config user.name "${GIT_AUTHOR_NAME:-github-actions[bot]}"
  git config user.email "${GIT_AUTHOR_EMAIL:-41898282+github-actions[bot]@users.noreply.github.com}"
  git commit -m "chore(release): openadt ${version}"
  git push origin "HEAD:${target_branch}"
  echo "Updated ${repo_slug}@${target_branch} with openadt ${version}"
}

token="$(git_token || true)"
if [[ -z "${token}" ]]; then
  echo "No git token (GITHUB_TOKEN / GH_TOKEN / OPENADT_SCOOP_BUCKET_TOKEN)." >&2
  exit 1
fi

synced=0
external_synced=0

if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
  push_manifest_to_repo "${GITHUB_REPOSITORY}" "${same_repo_branch}" "${token}"
  synced=1
fi

bucket_token="$(scoop_bucket_token || true)"
if [[ -n "${bucket_token}" ]]; then
  if push_manifest_via_gh_contents "${external_repo}" "${bucket_token}" 2>/dev/null; then
    external_synced=1
  elif push_manifest_to_repo "${external_repo}" "${branch}" "${bucket_token}"; then
    external_synced=1
  fi
  synced=1
else
  echo "Skipping ${external_repo}: set repo secret OPENADT_SCOOP_BUCKET_TOKEN (PAT with contents:write on ${external_repo})." >&2
  echo "Scoop: scoop bucket add openadt https://github.com/${GITHUB_REPOSITORY:-abapify/openadt}.git#${same_repo_branch}" >&2
fi

if [[ "${synced}" -eq 0 ]]; then
  echo "No scoop manifest destination was updated." >&2
  exit 1
fi

if [[ "${external_synced}" -eq 0 && -n "${GITHUB_REPOSITORY:-}" ]]; then
  echo "Note: ${external_repo} was not updated; use the scoop-bucket branch on ${GITHUB_REPOSITORY} or add OPENADT_SCOOP_BUCKET_TOKEN." >&2
fi
