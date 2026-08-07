#!/usr/bin/env bash
# OpenADT launcher for macOS and Linux.
#
# The shipped openadt.jar is the `-Pdistribution` build, which excludes the ADT SDK
# classes. Commands that talk to SAP through the SDK therefore need the full runtime
# jar built by `openadt config build` (~/.openadt/runtime/openadt-full.jar) plus the
# staged SAP bundles in ~/.openadt/runtime/sap-lib. Everything else runs from the
# lite jar, which starts faster and needs no SAP bundles.
#
# This mirrors packaging/windows/openadt-launcher.ps1.
set -uo pipefail

OPENADT_HOME="${OPENADT_HOME:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
export OPENADT_HOME

LITE_JAR="$OPENADT_HOME/openadt.jar"
RUNTIME_DIR="$HOME/.openadt/runtime"
FULL_JAR="$RUNTIME_DIR/openadt-full.jar"
SAP_LIB="$RUNTIME_DIR/sap-lib"
MAIN_CLASS="org.openadt.cli.OpenAdtCommand"

# Commands that reach SAP through the ADT SDK.
SDK_COMMANDS=" fetch proxy auth discovery sdk transports "

die() {
  echo "openadt: $*" >&2
  exit 1
}

resolve_java() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    printf '%s' "$JAVA_HOME/bin/java"
    return 0
  fi
  local found
  found="$(command -v java 2>/dev/null)" || true
  [[ -n "$found" ]] || die "java not found on PATH; install JDK 21 or set JAVA_HOME"
  printf '%s' "$found"
}

installed_version() {
  if [[ -f "$OPENADT_HOME/VERSION" ]]; then
    tr -d '[:space:]' < "$OPENADT_HOME/VERSION"
  fi
}

run_lite() {
  [[ -f "$LITE_JAR" ]] || die "missing $LITE_JAR - reinstall OpenADT"
  exec "$JAVA_BIN" -jar "$LITE_JAR" "$@"
}

# Build the SDK runtime on first use, or when it was built by a different OpenADT version.
ensure_runtime_prepared() {
  local version marker prepared
  version="$(installed_version)"
  marker="$RUNTIME_DIR/version.txt"
  if [[ -f "$FULL_JAR" ]]; then
    if [[ -z "$version" ]]; then
      return 0
    fi
    if [[ -f "$marker" ]]; then
      prepared="$(tr -d '[:space:]' < "$marker")"
      [[ "$prepared" == "$version" ]] && return 0
    fi
  fi
  echo "openadt: preparing SAP SDK runtime (first run may take several minutes)..." >&2
  [[ -f "$LITE_JAR" ]] || die "missing $LITE_JAR - reinstall OpenADT"
  "$JAVA_BIN" -jar "$LITE_JAR" config build || exit $?
}

# The staged sap-lib is a coherent, Maven-resolved bundle set and already carries JCo
# under its required archive name. Do not substitute the whole plugin pool: it holds
# several versions of each ADT bundle and mixing them breaks logon.
sdk_classpath() {
  local classpath="$FULL_JAR" jar
  if [[ -d "$SAP_LIB" ]]; then
    for jar in "$SAP_LIB"/*.jar; do
      [[ -f "$jar" ]] && classpath="$classpath:$jar"
    done
  fi
  printf '%s' "$classpath"
}

run_sdk() {
  ensure_runtime_prepared
  [[ -f "$FULL_JAR" ]] || die "SDK runtime jar missing at $FULL_JAR. Run: openadt config build"
  exec "$JAVA_BIN" -cp "$(sdk_classpath)" "$MAIN_CLASS" "$@"
}

# Mirrors LocalProxyRegistry.sanitizeAlias: lowercase, and each run of characters
# outside [a-z0-9._-] collapses to a single underscore.
sanitize_alias() {
  local alias="$1"
  [[ -n "$alias" ]] || { printf 'default'; return 0; }
  printf '%s' "$alias" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9._-]+/_/g'
}

# True when a local `openadt proxy` is serving this alias, in which case fetch should go
# through it rather than opening its own SDK session.
proxy_active() {
  local registry host port
  registry="$RUNTIME_DIR/proxy-$(sanitize_alias "$1").json"
  [[ -f "$registry" ]] || return 1
  port="$(sed -n 's/.*"port"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$registry" | head -1)"
  [[ -n "$port" ]] || return 1
  host="$(sed -n 's/.*"host"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$registry" | head -1)"
  [[ -n "$host" ]] || host="127.0.0.1"
  (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1 || return 1
  exec 3>&- 2>/dev/null || true
  return 0
}

# fetch uses the lite jar when a local proxy serves the alias and --direct was not given.
fetch_uses_lite_proxy() {
  local arg saw_fetch=0
  for arg in "$@"; do
    [[ "$arg" == "--direct" ]] && return 1
  done
  for arg in "$@"; do
    if [[ "$saw_fetch" -eq 1 ]]; then
      proxy_active "$arg"
      return $?
    fi
    [[ "$arg" == "fetch" ]] && saw_fetch=1
  done
  return 1
}

JAVA_BIN="$(resolve_java)"

if [[ $# -eq 0 ]]; then
  run_lite
fi

subcommand="$1"

if [[ "$subcommand" == "fetch" ]] && fetch_uses_lite_proxy "$@"; then
  run_lite "$@"
fi

if [[ "$SDK_COMMANDS" == *" $subcommand "* ]]; then
  run_sdk "$@"
fi

run_lite "$@"
