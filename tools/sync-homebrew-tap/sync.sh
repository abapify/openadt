#!/usr/bin/env bash
# Sync Formula/<product>.rb to abapify/homebrew-openadt (standard Homebrew tap).
# CI: installation token from org app abapify-bro (GH_TOKEN / OPENADT_HOMEBREW_TAP_TOKEN).
#
# Products (positional args, default = "openadt"): openadt openadt-mcp
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
branch="main"
external_repo="${OPENADT_HOMEBREW_TAP_REPO:-abapify/homebrew-openadt}"

products=("$@")
if [[ ${#products[@]} -eq 0 ]]; then
  products=("openadt")
fi

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
    base64 -w0 "$1"
  else
    base64 <"$1" | tr -d '\n'
  fi
}

push_formula_via_gh_contents() {
  local product="$1"
  local formula="$2"
  local version="$3"
  local repo_slug="$4"
  local token="$5"
  local formula_path="Formula/${product}.rb"
  export GH_TOKEN="${token}"
  local sha=""
  sha="$(gh api "repos/${repo_slug}/contents/${formula_path}" --jq .sha 2>/dev/null || true)"
  local content
  content="$(formula_base64 "${formula}")"
  local api_args=(
    --method PUT
    "repos/${repo_slug}/contents/${formula_path}"
    -f "message=chore(release): ${product} ${version}"
    -f "content=${content}"
  )
  if [[ -n "${sha}" ]]; then
    api_args+=(-f "sha=${sha}")
  fi
  if gh api "${api_args[@]}" >/dev/null; then
    echo "Updated ${repo_slug}@main via Contents API (${product} ${version})"
    return 0
  fi
  return 1
}

push_formula_to_repo() {
  local product="$1"
  local formula="$2"
  local version="$3"
  local repo_slug="$4"
  local target_branch="$5"
  local token="$6"
  local formula_path="Formula/${product}.rb"
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
    echo "${repo_slug}@${target_branch} already up to date (${product} ${version})."
    return 0
  fi

  git config user.name "${GIT_AUTHOR_NAME:-github-actions[bot]}"
  git config user.email "${GIT_AUTHOR_EMAIL:-41898282+github-actions[bot]@users.noreply.github.com}"
  git commit -m "chore(release): ${product} ${version}"
  git -c "${git_cfg}" push "${clone_url}" "HEAD:${target_branch}" && \
    echo "Updated ${repo_slug}@${target_branch} with ${product} ${version}"
}

token="$(tap_token || true)"
if [[ -z "${token}" ]]; then
  echo "Skipping ${external_repo}: configure abapify-bro or set OPENADT_HOMEBREW_TAP_TOKEN / GH_TOKEN." >&2
  echo "Users: brew tap abapify/openadt" >&2
  exit 0
fi

sync_product() {
  local product="$1"
  local formula="${root}/Formula/${product}.rb"

  if [[ ! -f "${formula}" ]]; then
    echo "Skipping ${product}: missing ${formula}" >&2
    return 0
  fi

  local version
  version="$(grep -m1 'STABLE = ' "${formula}" | sed 's/.*"\(.*\)".*/\1/')"

  if push_formula_via_gh_contents "${product}" "${formula}" "${version}" "${external_repo}" "${token}"; then
    return 0
  fi
  if push_formula_to_repo "${product}" "${formula}" "${version}" "${external_repo}" "${branch}" "${token}"; then
    return 0
  fi

  echo "Failed to sync ${product} formula to ${external_repo}" >&2
  return 1
}

for product in "${products[@]}"; do
  sync_product "${product}"
done
