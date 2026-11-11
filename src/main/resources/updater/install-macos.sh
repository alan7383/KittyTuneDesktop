#!/bin/bash
set -eu

pid=$1
dmg=$2
target=$3
arch=$4
work=$5
mount="$work/mount"
stage=
mounted=0
replaced=0
committed=0

finish() {
    local result=$?
    trap - EXIT
    if [[ "$committed" == 0 && -n "$stage" && -d "$stage/previous.app" ]]; then
        if [[ "$replaced" == 1 ]]; then
            /bin/mv "$target" "$stage/failed.app" || result=1
        fi
        if [[ ! -e "$target" ]]; then
            /bin/mv "$stage/previous.app" "$target" || result=1
        fi
    fi
    if [[ "$mounted" == 1 ]]; then
        /usr/bin/hdiutil detach "$mount" || true
    fi
    if [[ -n "$stage" && ( "$committed" == 1 || ! -e "$stage/previous.app" ) ]]; then
        /bin/rm -r "$stage"
    fi
    if [[ "$result" != 0 ]]; then
        echo "KittyTune update failed (exit $result). Log: $work/update.log"
        if [[ -d "$target" ]] && ! kill -0 "$pid" 2>/dev/null; then
            /usr/bin/open -n "$target" || true
        fi
        /usr/bin/osascript -e 'on run argv' \
            -e 'display alert "KittyTune" message (item 1 of argv) as warning' \
            -e 'end run' "$6" || true
    fi
    exit "$result"
}
trap 'finish "$@"' EXIT

mkdir "$mount"
/usr/bin/hdiutil attach "$dmg" -readonly -nobrowse -mountpoint "$mount"
mounted=1
source="$mount/KittyTune.app"
plist="$source/Contents/Info.plist"
[[ "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$plist")" == com.alananasss.kittytune ]]
launcher=$(/usr/libexec/PlistBuddy -c 'Print CFBundleExecutable' "$plist")
[[ "$launcher" == KittyTune && -x "$source/Contents/MacOS/$launcher" ]]
/usr/bin/file -b "$source/Contents/MacOS/$launcher" | /usr/bin/grep -qw "$arch"

# Stage beside the installed bundle so both renames stay on the same filesystem.
stage=$(/usr/bin/mktemp -d "$(dirname "$target")/.kittytune-update.XXXXXX")
/usr/bin/ditto "$source" "$stage/new.app"
for ((attempt=0; attempt<60; attempt++)); do
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
done
if kill -0 "$pid" 2>/dev/null; then exit 1; fi

/bin/mv "$target" "$stage/previous.app"
/bin/mv "$stage/new.app" "$target"
replaced=1
/usr/bin/open -n "$target"
committed=1
