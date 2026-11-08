#!/usr/bin/env bash
# Builds the CDM sidecar in this Linux host (WSL or a real machine) and prints where it landed.
#
# Run from scripts/setup-cdm-sidecar.ps1; also usable by hand:
#   bash scripts/setup-cdm-sidecar.sh [--cdm /path/to/libwidevinecdm.so]
#
# What it produces:
#   $ROOT/ktcdm                  the daemon (bridge + sidecar, one binary)
#   $ROOT/libwidevinecdm.so      the CDM it loads, either given, found, or taken from Chrome's deb
# and one line of JSON on stdout with those two paths, so the caller does not have to guess $HOME.
set -euo pipefail

ROOT="${KITTYTUNE_CDM_ROOT:-$HOME/.local/share/kittytune}"
CDM="${KITTYTUNE_WIDEVINE_CDM:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CPP_DIR="$(cd "$SCRIPT_DIR/../src/main/cpp" 2>/dev/null && pwd || true)"

while [ $# -gt 0 ]; do
  case "$1" in
    --cdm) CDM="$2"; shift 2 ;;
    --root) ROOT="$2"; shift 2 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done

log() { printf '%s\n' "$*" >&2; }

# The bridge and the daemon are compiled together: the daemon calls the C ABI the bridge exports.
if [ -z "$CPP_DIR" ] || [ ! -f "$CPP_DIR/cdmsidecar/CdmSidecar.cpp" ]; then
  log "cannot find src/main/cpp beside this script; run it from the checkout"
  exit 66
fi

for tool in g++ curl dpkg-deb; do
  command -v "$tool" >/dev/null 2>&1 || { log "missing $tool (apt-get install -y build-essential curl dpkg)"; exit 69; }
done

mkdir -p "$ROOT"
ROOT="$(cd "$ROOT" && pwd)"

# ---------------------------------------------------------------------------------------------
# The CDM
# ---------------------------------------------------------------------------------------------

if [ -z "$CDM" ]; then
  for candidate in \
    "$ROOT/libwidevinecdm.so" \
    /opt/google/chrome/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so \
    /opt/google/chrome-beta/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so; do
    if [ -f "$candidate" ]; then CDM="$candidate"; break; fi
  done
fi

if [ -z "$CDM" ]; then
  log "no local CDM found; extracting one from Chrome's Linux package"
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  if ! curl -fsSL --retry 2 -o "$work/chrome.deb" \
        https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb; then
    log "download failed; pass --cdm /path/to/libwidevinecdm.so instead"
    exit 69
  fi
  dpkg-deb -x "$work/chrome.deb" "$work/root"
  found="$(find "$work/root" -name 'libwidevinecdm.so' -type f -print -quit || true)"
  if [ -z "$found" ]; then
    log "the Chrome package carried no libwidevinecdm.so"
    exit 69
  fi
  install -m 0755 "$found" "$ROOT/libwidevinecdm.so"
  CDM="$ROOT/libwidevinecdm.so"
fi

if [ ! -f "$CDM" ]; then
  log "the CDM path does not exist: $CDM"
  exit 66
fi

# ---------------------------------------------------------------------------------------------
# The daemon
# ---------------------------------------------------------------------------------------------

log "compiling ktcdm"
g++ -std=c++17 -O2 -pthread \
  "$CPP_DIR/widevine/WidevineBridge.cpp" \
  "$CPP_DIR/cdmsidecar/CdmSidecar.cpp" \
  -ldl -o "$ROOT/ktcdm.tmp"
mv -f "$ROOT/ktcdm.tmp" "$ROOT/ktcdm"
chmod 0755 "$ROOT/ktcdm"

# A cold start loads the CDM and provisions it, which is slow the first time and instant after.
log "probing $CDM"
if ! "$ROOT/ktcdm" "$CDM" --probe; then
  log "the daemon could not load that CDM"
  exit 70
fi

printf '{"binary":"%s","cdm":"%s"}\n' "$ROOT/ktcdm" "$CDM"
