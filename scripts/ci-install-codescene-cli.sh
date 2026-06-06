#!/usr/bin/env bash
# Non-interactive CodeScene CLI install for CI (install-cs-tool.sh prompts for /dev/tty).
# Docs: https://codescene.io/docs/cli/index.html
set -euo pipefail

version="${CS_CLI_VERSION:-latest}"
arch="amd64"
case "$(uname -m)" in
  aarch64 | arm64) arch="aarch64" ;;
esac

zip_path="/tmp/cs-cli.zip"
extract_dir="/tmp/cs-cli"
url="https://downloads.codescene.io/enterprise/cli/cs-linux-${arch}-${version}.zip"
dest="${HOME}/.local/bin"

curl -fsSL -o "${zip_path}" "${url}"
rm -rf "${extract_dir}"
mkdir -p "${extract_dir}"
unzip -q "${zip_path}" -d "${extract_dir}"
chmod +x "${extract_dir}/cs"
mkdir -p "${dest}"
install -m 755 "${extract_dir}/cs" "${dest}/cs"

if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "${dest}" >>"${GITHUB_PATH}"
fi

"${dest}/cs" version
