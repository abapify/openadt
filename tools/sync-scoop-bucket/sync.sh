#!/usr/bin/env bash
# Sync packaging/scoop/<product>.json to abapify/scoop-bucket (standard Scoop bucket).
# CI: installation token from org app abapify-bro (GH_TOKEN / OPENADT_SCOOP_BUCKET_TOKEN).
# Legacy monorepo branch scoop-bucket uses GITHUB_TOKEN on the current repo only.
#
# Products (positional args, default = "openadt"): openadt openadt-mcp
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
branch="main"
external_repo="${OPENADT_SCOOP_BUCKET_REPO:-abapify/scoop-bucket}"
legacy_branch="${OPENADT_SCOOP_BRANCH:-scoop-bucket}"

products=("$@")
if [[ ${#products[@]} -eq 0 ]]; then
  products=("openadt")
fi

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
    base64 -w0 "$1"
  else
    base64 <"$1" | tr -d '\n'
  fi
}

push_manifest_via_gh_contents() {
  local product="$1"
  local manifest="$2"
  local version="$3"
  local repo_slug="$4"
  local token="$5"
  export GH_TOKEN="${token}"
  local sha=""
  sha="$(gh api "repos/${repo_slug}/contents/${product}.json" --jq .sha 2>/dev/null || true)"
  local content
  content="$(manifest_base64 "${manifest}")"
  local api_args=(
    --method PUT
    "repos/${repo_slug}/contents/${product}.json"
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

push_manifest_to_repo() {
  local product="$1"
  local manifest="$2"
  local version="$3"
  local repo_slug="$4"
  local target_branch="$5"
  local token="$6"
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

  cp "${manifest}" "${work}/${product}.json"
  cd "${work}"
  git add "${product}.json"

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

sync_product() {
  local product="$1"
  local manifest="${root}/packaging/scoop/${product}.json"

  if [[ ! -f "${manifest}" ]]; then
    echo "Skipping ${product}: missing ${manifest}" >&2
    return 0
  fi

  local version
  version="$(grep -m1 '"version"' "${manifest}" | sed 's/.*: "\(.*\)".*/\1/')"

  local token
  token="$(external_bucket_token || true)"
  local synced=0

  if [[ -n "${token}" ]]; then
    if push_manifest_via_gh_contents "${product}" "${manifest}" "${version}" "${external_repo}" "${token}"; then
      synced=1
    elif push_manifest_to_repo "${product}" "${manifest}" "${version}" "${external_repo}" "${branch}" "${token}"; then
      synced=1
    fi
    if [[ "${synced}" -eq 0 ]]; then
      echo "Failed to sync ${product} scoop manifest to ${external_repo}" >&2
      return 1
    fi
  else
    echo "Skipping ${external_repo} for ${product}: configure abapify-bro or set OPENADT_SCOOP_BUCKET_TOKEN / GH_TOKEN." >&2
    echo "Users: scoop bucket add openadt https://github.com/${external_repo}" >&2
  fi
}

# External bucket: fan out across products.
for product in "${products[@]}"; do
  sync_product "${product}"
done

# Legacy monorepo branch is openadt-only (historical).
if [[ -n "${GITHUB_REPOSITORY:-}" && -n "${GITHUB_TOKEN:-}" ]]; then
  manifest="${root}/packaging/scoop/openadt.json"
  if [[ -f "${manifest}" ]]; then
    version="$(grep -m1 '"version"' "${manifest}" | sed 's/.*: "\(.*\)".*/\1/')"
    if ! push_manifest_to_repo "openadt" "${manifest}" "${version}" "${GITHUB_REPOSITORY}" "${legacy_branch}" "${GITHUB_TOKEN}"; then
      echo "Warning: failed to sync legacy branch ${legacy_branch} on ${GITHUB_REPOSITORY}" >&2
    fi
  fi
fi
