#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${OPENADT_ADT_PLUGINS_DIR:-}" ]]; then
  if [[ -d "${HOME}/.p2/pool/plugins" ]]; then
    OPENADT_ADT_PLUGINS_DIR="${HOME}/.p2/pool/plugins"
  fi
fi

if [[ -z "${OPENADT_ADT_PLUGINS_DIR:-}" ]] || [[ ! -d "${OPENADT_ADT_PLUGINS_DIR}" ]]; then
  echo "::notice title=Java tests skipped::SAP ADT/Eclipse plugins are not available in this runner. Set OPENADT_ADT_PLUGINS_DIR to enable Java tests."
  exit 0
fi

export ADT_PLUGINS_DIR="${OPENADT_ADT_PLUGINS_DIR}"
cd "${root}"
chmod +x ./mvnw
./mvnw -q verify -Dadt.plugins.dir="${ADT_PLUGINS_DIR}"
