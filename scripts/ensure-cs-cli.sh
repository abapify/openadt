#!/usr/bin/env bash
# Ensure the CodeScene `cs` CLI is available.
#
# Behavior:
#   1. If `cs` is already on PATH → exit 0 (no-op).
#   2. Otherwise download the linux-amd64 (or darwin) binary to a
#      workspace-local directory and print the path for the caller.
#
# This script is designed for **cloud-agent** environments where
#   - Docker is unavailable
#   - ~/.local/bin may not be writable
#   - /tmp/cs is blocked (installer conflict)
# It also works on real developer machines as a quick no-Docker fallback.
#
# Output (stdout, last line):
#   <absolute-path-to-cs-binary>
#
# Exit codes:
#   0  cs is available (or was installed)
#   1  download / install failed
#
# Env vars:
#   CS_ACCESS_TOKEN   — required; CodeScene PAT (used for versioned downloads)
#   CS_CLI_VERSION    — optional; default "latest"
#   CS_INSTALL_DIR    — optional; override install dir (default: /tmp/kilo/cs)
set -euo pipefail

if command -v cs &>/dev/null; then
  command -v cs
  exit 0
fi

# --- Detect platform ---
os="$(uname -s)"
case "$os" in
  Linux)  cs_os="linux";  cs_arch="amd64" ;;
  Darwin) cs_os="macos";  cs_arch="$([ "$(uname -m)" = "arm64" ] && echo aarch64 || echo amd64)" ;;
  *) echo "Unsupported OS: $os" >&2; exit 1 ;;
esac

version="${CS_CLI_VERSION:-latest}"
dest="${CS_INSTALL_DIR:-/tmp/kilo/cs}"
zip_file="${dest}/cs.zip"

mkdir -p "$dest"

url="https://downloads.codescene.io/enterprise/cli/cs-${cs_os}-${cs_arch}-${version}.zip"
echo "Downloading cs ${version} for ${cs_os}-${cs_arch} …" >&2
curl -fsSL -o "$zip_file" "$url"

unzip -qo "$zip_file" -d "$dest"
rm -f "$zip_file"
chmod +x "$dest/cs"

# Verify
"$dest/cs" version >&2
echo "$dest/cs"
