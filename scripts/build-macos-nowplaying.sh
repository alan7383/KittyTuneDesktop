#!/usr/bin/env bash
# Reproducible developer build for KittyTune macOS Now Playing bridge.
# Requires a Mac with Xcode Command Line Tools / Swift and the project's JDK.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This build must run on macOS (AppKit/MediaPlayer required)." >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HELPER="$ROOT/build/macos-nowplaying/KittyTune-NowPlaying-Helper.app"
SWIFT="$ROOT/src/main/swift/KittyTuneNowPlaying.swift"
mkdir -p "$HELPER/Contents/MacOS" "$HELPER/Contents/Resources"

cat > "$HELPER/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleIdentifier</key><string>com.alananasss.kittytune.nowplaying.helper</string>
  <key>CFBundleName</key><string>KittyTune Now Playing Helper</string>
  <key>CFBundleExecutable</key><string>KittyTuneNowPlaying</string>
  <key>CFBundleIconFile</key><string>KittyTune.icns</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSUIElement</key><true/>
</dict></plist>
PLIST

swiftc -O -framework AppKit -framework MediaPlayer "$SWIFT" \
  -o "$HELPER/Contents/MacOS/KittyTuneNowPlaying"
chmod 755 "$HELPER/Contents/MacOS/KittyTuneNowPlaying"
plutil -lint "$HELPER/Contents/Info.plist"

# Pre-populate icon from resources if available
if [[ -f "$ROOT/src/main/resources/icons/kittytune.icns" ]]; then
  cp "$ROOT/src/main/resources/icons/kittytune.icns" "$HELPER/Contents/Resources/KittyTune.icns"
fi

echo "Compiled helper: $HELPER"
if [[ "${1:-}" == "--helper-only" ]]; then
  echo "For development: ./gradlew run"
  exit 0
fi

MODE="main"
TASK="createDistributable"
BUILD_DMG=0

if [[ "${1:-}" == "--release" || "${1:-}" == "--dmg" ]]; then
  MODE="main-release"
  TASK="createReleaseDistributable"
fi

if [[ "${1:-}" == "--dmg" ]]; then
  BUILD_DMG=1
fi

./gradlew "$TASK" --console=plain
APP_DIR="$ROOT/build/compose/binaries/$MODE/app"
# The upstream v1.4.2 app name is KittyTune.app.
APP="$APP_DIR/KittyTune.app"
if [[ ! -d "$APP" ]]; then
  echo "Missing $APP. Confirm nativeDistributions.packageName in build.gradle.kts." >&2
  exit 1
fi

# Reuse the original icon so that Now Playing clients can resolve its source.
ICON="$(find "$APP/Contents/Resources" -maxdepth 1 -type f -name '*.icns' -print -quit 2>/dev/null || true)"
if [[ -n "$ICON" ]]; then
  cp "$ICON" "$HELPER/Contents/Resources/KittyTune.icns"
fi

mkdir -p "$APP/Contents/Helpers"
ditto "$HELPER" "$APP/Contents/Helpers/KittyTune-NowPlaying-Helper.app"

echo "Packaged Now Playing helper in: $APP"

if [[ $BUILD_DMG -eq 1 ]]; then
  mkdir -p "$ROOT/dist-mac"
  VERSION="$(grep -m 1 '^version = ' "$ROOT/build.gradle.kts" | cut -d '"' -f 2)"
  DMG_PATH="$ROOT/dist-mac/KittyTune-${VERSION}.dmg"
  echo "Packaging DMG at: $DMG_PATH"
  hdiutil create -volname "KittyTune" -srcfolder "$APP" -ov -format UDZO "$DMG_PATH"
  echo "Created DMG: $DMG_PATH"
else
  echo "Launch with: open '$APP'"
fi

echo "For shipping: re-sign and notarize the final app AFTER embedding the helper."
