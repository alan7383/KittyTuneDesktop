#!/usr/bin/env bash
# Verify bytes extracted from the final package, including its embedded JRE.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
package=$(realpath -- "${1:?Usage: check-linux-package.sh package.deb|package.pkg.tar.zst}")
test_dir=$(mktemp -d)
trap 'rm -rf -- "$test_dir"' EXIT
case "$package" in
  *.deb) dpkg-deb --extract "$package" "$test_dir" ;;
  *.pkg.tar.zst) tar --zstd -xf "$package" -C "$test_dir" ;;
  *) echo "Unsupported package: $package" >&2; exit 1 ;;
esac
dbus-run-session -- xvfb-run -a python3 "$script_dir/check-linux-dbus.py" \
  "$test_dir/opt/kitty-tune" --private-watcher
