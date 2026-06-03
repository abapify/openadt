#!/usr/bin/env bash
# Sync Formula/openadt.rb to abapify/homebrew-openadt (standard Homebrew tap).
# CI: installation token from org app abapify-bro (GH_TOKEN / OPENADT_HOMEBREW_TAP_TOKEN).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
formula="${root}/Formula/openadt.rb"
branch="main"
external_repo="${OPENADT_HOMEBREW_TAP_REPO:-abapify/homebrew-openadt}"
formula_path="Formula/openadt.rb"

if [[ ! -f "${formula}" ]]; then
  echo "Missing ${formula}" >&2
  exit 1
fi

version="$(grep -m1 'STABLE = ' "${formula}" | sed 's/.*"\(.*\)".*/\1/')"

tap_token() {
  if [[ -n "${OPENADT_HOMEBREW_TAP_TOKEN:-}" ]]; then
    printf '%s' "${OPENADT_HOMEBREW_TAP_TOKEN}"
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

formula_base64() {
  if base64 --help 2>&1 | grep -q -- '-w'; then
    base64 -w0 "${formula}"
  else
    base64 <"${formula}" | tr -d '\n'
  fi
}

push_formula_via_gh_contents() {
  local repo_slug="$1"
  local token="$2"
  export GH_TOKEN="${token}"
  local sha=""
  sha="$(gh api "repos/${repo_slug}/contents/${formula_path}" --jq .sha 2>/dev/null || true)"
  local content
  content="$(formula_base64)"
  local api_args=(
    --method PUT
    "repos/${repo_slug}/contents/${formula_path}"
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

push_formula_to_repo() {
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

  mkdir -p "${work}/Formula"
  cp "${formula}" "${work}/${formula_path}"
  cd "${work}"
  git add "${formula_path}"

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

token="$(tap_token || true)"
if [[ -z "${token}" ]]; then
  echo "Skipping ${external_repo}: configure abapify-bro or set OPENADT_HOMEBREW_TAP_TOKEN / GH_TOKEN." >&2
  echo "Users: brew tap abapify/openadt" >&2
  exit 0
fi

if push_formula_via_gh_contents "${external_repo}" "${token}"; then
  exit 0
fi

if push_formula_to_repo "${external_repo}" "${branch}" "${token}"; then
  exit 0
fi

echo "Failed to sync formula to ${external_repo}" >&2
exit 1
