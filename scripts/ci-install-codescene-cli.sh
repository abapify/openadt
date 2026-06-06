#!/usr/bin/env bash
# Local/non-Docker CodeScene CLI install (install-cs-tool.sh -y, verified like codescene-mcp Dockerfile).
# CI uses scripts/ci-codescene-delta.sh (Docker) to avoid flaky downloads.codescene.io 403.
# Docs: https://codescene.io/docs/cli/index.html
# Note: the versioned download endpoint requires a CS_ACCESS_TOKEN header (403 otherwise),
# so we rely on the redirect-stable "latest" channel here and rely on the GitHub App /
# quality-gate profile for deterministic findings. Override locally with CS_CLI_VERSION
# if your token is exposed to the runner.
set -euo pipefail

installer_sha256="${CS_CLI_INSTALLER_SHA256:-6a119bd0746de31740bb899fbcc16f44b31df2392740642d5a29616961501f06}"
installer="/tmp/install-cs-tool.sh"
dest="${HOME}/.local/bin"

curl --proto '=https' --tlsv1.2 -fsSL -o "${installer}" \
  https://downloads.codescene.io/enterprise/cli/install-cs-tool.sh
echo "${installer_sha256} ${installer}" | sha256sum -c -
bash "${installer}" -y
rm -f "${installer}"

if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "${dest}" >>"${GITHUB_PATH}"
fi

"${dest}/cs" version
