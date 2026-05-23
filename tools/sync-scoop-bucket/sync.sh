#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
manifest="${root}/packaging/scoop/openadt.json"
branch="main"
repo="${OPENADT_SCOOP_BUCKET_REPO:-abapify/scoop-bucket}"

if [[ ! -f "${manifest}" ]]; then
  echo "Missing ${manifest}" >&2
  exit 1
fi

work="$(mktemp -d)"
cleanup() {
  rm -rf "${work}"
}
trap cleanup EXIT

remote_url="https://github.com/${repo}.git"
if gh api "repos/${repo}" >/dev/null 2>&1; then
  if git ls-remote --heads "${remote_url}" "${branch}" | grep -q "${branch}"; then
    git clone --branch "${branch}" --depth 1 "${remote_url}" "${work}"
  else
    git init "${work}"
    git -C "${work}" checkout -b "${branch}"
    git -C "${work}" remote add origin "${remote_url}"
  fi
else
  echo "Repository ${repo} does not exist." >&2
  exit 1
fi

cp "${manifest}" "${work}/openadt.json"
cd "${work}"
git add openadt.json

if git diff --cached --quiet; then
  echo "${repo} already up to date."
  exit 0
fi

version="$(grep -m1 '"version"' openadt.json | sed 's/.*: "\(.*\)".*/\1/')"
git config user.name "${GIT_AUTHOR_NAME:-github-actions[bot]}"
git config user.email "${GIT_AUTHOR_EMAIL:-41898282+github-actions[bot]@users.noreply.github.com}"
git commit -m "chore(release): openadt ${version}"
git push origin "HEAD:${branch}"

echo "Updated ${repo} with openadt ${version}"
