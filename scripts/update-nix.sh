#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="${1:-}"
ENDPOINT=latest

if [ -n "$VERSION" ]; then
  VERSION="${VERSION#v}"
  if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Expected a version such as 1.6.0 or v1.6.0" >&2
    exit 2
  fi
  ENDPOINT="tags/v$VERSION"
fi

release=$(curl --fail --silent --show-error --location \
  "https://api.github.com/repos/alan7383/KittyTuneDesktop/releases/$ENDPOINT")
printf '%s' "$release" | "${PYTHON:-python3}" "$SCRIPT_DIR/../nix/update-release.py"
echo "Updated nix/package.nix from verified release metadata"
