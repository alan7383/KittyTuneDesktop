#!/usr/bin/env bash
set -euo pipefail

# scripts/update-nix.sh: Updates version and SHA256 hashes in nix/package.nix from GitHub Releases

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_FILE="${SCRIPT_DIR}/../nix/package.nix"

VERSION="${1:-}"

if [ -z "$VERSION" ]; then
  echo "No version supplied. Fetching latest release tag from GitHub..."
  VERSION=$(curl -s "https://api.github.com/repos/alan7383/KittyTuneDesktop/releases/latest" | grep '"tag_name":' | sed -E 's/.*"tag_name": *"v?([^"]+)".*/\1/')
fi

# Strip leading 'v' if present
VERSION="${VERSION#v}"

echo "Fetching release hashes for KittyTune v${VERSION}..."

AMD64_URL="https://github.com/alan7383/KittyTuneDesktop/releases/download/v${VERSION}/kitty-tune_${VERSION}_amd64.deb"
ARM64_URL="https://github.com/alan7383/KittyTuneDesktop/releases/download/v${VERSION}/kitty-tune_${VERSION}_arm64.deb"

echo "Downloading and calculating hash for amd64 ($AMD64_URL)..."
AMD64_HASH=$(nix-prefetch-url "$AMD64_URL" 2>/dev/null || curl -sL "$AMD64_URL" | sha256sum | awk '{print $1}')
if [[ "$AMD64_HASH" =~ ^[0-9a-f]{64}$ ]]; then
  AMD64_HASH="sha256-$(echo "$AMD64_HASH" | xxd -r -p | base64)"
fi

echo "Downloading and calculating hash for arm64 ($ARM64_URL)..."
ARM64_HASH=$(nix-prefetch-url "$ARM64_URL" 2>/dev/null || curl -sL "$ARM64_URL" | sha256sum | awk '{print $1}')
if [[ "$ARM64_HASH" =~ ^[0-9a-f]{64}$ ]]; then
  ARM64_HASH="sha256-$(echo "$ARM64_HASH" | xxd -r -p | base64)"
fi

echo "Updating ${PACKAGE_FILE}..."
sed -i -E "s/version = \"[^\"]+\";/version = \"${VERSION}\";/" "$PACKAGE_FILE"
sed -i -E "/x86_64-linux = \{/,/arch = \"amd64\";/ s|hash = \"[^\"]+\";|hash = \"${AMD64_HASH}\";|" "$PACKAGE_FILE"
sed -i -E "/aarch64-linux = \{/,/arch = \"arm64\";/ s|hash = \"[^\"]+\";|hash = \"${ARM64_HASH}\";|" "$PACKAGE_FILE"

echo "Done! nix/package.nix is now at version ${VERSION}:"
echo "  x86_64-linux (amd64): ${AMD64_HASH}"
echo "  aarch64-linux (arm64): ${ARM64_HASH}"
