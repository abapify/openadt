#!/usr/bin/env bash
# Sync packaging/scoop/openadt.json to abapify/scoop-bucket (standard Scoop bucket).
# CI: installation token from org app abapify-bro (GH_TOKEN / OPENADT_SCOOP_BUCKET_TOKEN).
# Legacy monorepo branch scoop-bucket uses GITHUB_TOKEN on the current repo only.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
manifest="${root}/packaging/scoop/openadt.json"
branch="main"
external_repo="${OPENADT_SCOOP_BUCKET_REPO:-abapify/scoop-bucket}"
legacy_branch="${OPENADT_SCOOP_BRANCH:-scoop-bucket}"

if [[ ! -f "${manifest}" ]]; then
  echo "Missing ${manifest}" >&2
  exit 1
fi

version="$(grep -m1 '"version"' "${manifest}" | sed 's/.*: "\(.*\)".*/\1/')"

external_bucket_token() {
  if [[ -n "${OPENADT_SCOOP_BUCKET_TOKEN:-}" ]]; then
    printf '%s' "${OPENADT_SCOOP_BUCKET_TOKEN}"
  elif [[ -n "${GH_TOKEN:-}" ]]; then
    printf '%s' "${GH_TOKEN}"
  else
    return 1
  fi
}

git_bearer_config() {
  local token="$1"
  printf 'http.https://github.com/.extraheader=AUTHORIZATION: bearer %s' "${token}"
}

remote_exists() {
  local repo_slug="$1"
  local target_branch="$2"
  local token="$3"
  git -c "$(git_bearer_config "${token}")" \
    ls-remote "https://github.com/${repo_slug}.git" "refs/heads/${target_branch}" \
    2>/dev/null | grep -q .
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
  if gh api "${api_args[@]}" >/dev/null; then
    echo "Updated ${repo_slug}@main via Contents API (openadt ${version})"
    return 0
  fi
  return 1
}

push_manifest_to_repo() {
  local repo_slug="$1"
  local target_branch="$2"
  local token="$3"
  local clone_url="https://github.com/${repo_slug}.git"
  local git_cfg
  git_cfg="$(git_bearer_config "${token}")"

  work="$(mktemp -d)"
  cleanup() {
    rm -rf "${work}"
  }
  trap cleanup EXIT

  if remote_exists "${repo_slug}" "${target_branch}" "${token}"; then
    git -c "${git_cfg}" clone --branch "${target_branch}" --depth 1 "${clone_url}" "${work}"
  else
    git init "${work}"
    git -C "${work}" checkout -b "${target_branch}"
    git -C "${work}" remote add origin "${clone_url}"
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
  git -c "${git_cfg}" push "${clone_url}" "HEAD:${target_branch}" && \
    echo "Updated ${repo_slug}@${target_branch} with openadt ${version}"
}

token="$(external_bucket_token || true)"
external_synced=0

if [[ -n "${token}" ]]; then
  if push_manifest_via_gh_contents "${external_repo}" "${token}"; then
    external_synced=1
  elif push_manifest_to_repo "${external_repo}" "${branch}" "${token}"; then
    external_synced=1
  fi
  if [[ "${external_synced}" -eq 0 ]]; then
    echo "Failed to sync scoop manifest to ${external_repo}" >&2
    exit 1
  fi
else
  echo "Skipping ${external_repo}: configure abapify-bro or set OPENADT_SCOOP_BUCKET_TOKEN / GH_TOKEN." >&2
  echo "Users: scoop bucket add openadt https://github.com/${external_repo}" >&2
fi

if [[ -n "${GITHUB_REPOSITORY:-}" && -n "${GITHUB_TOKEN:-}" ]]; then
  if ! push_manifest_to_repo "${GITHUB_REPOSITORY}" "${legacy_branch}" "${GITHUB_TOKEN}"; then
    echo "Warning: failed to sync legacy branch ${legacy_branch} on ${GITHUB_REPOSITORY}" >&2
  fi
fi
