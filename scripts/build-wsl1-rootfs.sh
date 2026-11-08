#!/usr/bin/env bash
# Builds the minimal WSL rootfs that KittyTune imports for protected tracks.
#
# It is NOT a distribution: just enough Linux to run the Widevine CDM sidecar as a near-native
# process. Imported `--version 1` it runs under WSL1 (no VM, no vmmemWSL) which is the whole point;
# imported `--version 2` it is the WSL2 fallback. The Widevine CDM itself is NOT bundled (it cannot
# be redistributed) — the rootfs carries curl + dpkg-deb so the app fetches it from Chrome's .deb at
# setup, exactly as the old flow did.
#
# Runs on Linux (CI: ubuntu-latest). Produces ktroot.tgz in the output directory.
#
#   scripts/build-wsl1-rootfs.sh [--cpp src/main/cpp] [--out build/wsl1]
#
# What goes in (learned the hard way this session):
#   - ktcdm + the full ldd closure of every binary (glibc, ld-linux, libstdc++, libcurl, liblzma…)
#   - curl, dpkg-deb, ca-certificates bundle            (to self-fetch the CDM)
#   - /bin/sh                                           (the app runs scripts through it)
#   - /etc/passwd,group,shadow,nsswitch.conf            (without passwd, WSL fails getpwnam(root))
#   - /proc /sys /dev /tmp /root /run /opt/kittytune    (without these a bare rootfs won't launch:
#                                                        STATUS_CONNECTION_DISCONNECTED)
#   - /etc/wsl.conf: systemd off, no Windows PATH interop, no automount (quiet + fast launch)
set -euo pipefail

CPP_DIR="src/main/cpp"
OUT_DIR="build/wsl1"
while [ $# -gt 0 ]; do
  case "$1" in
    --cpp) CPP_DIR="$2"; shift 2 ;;
    --out) OUT_DIR="$2"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done

log() { printf '%s\n' "$*" >&2; }

[ -f "$CPP_DIR/cdmsidecar/CdmSidecar.cpp" ] || { log "no CdmSidecar.cpp under $CPP_DIR"; exit 66; }
for tool in g++ ldd curl dpkg-deb; do
  command -v "$tool" >/dev/null 2>&1 || { log "missing $tool (need build-essential, curl, dpkg)"; exit 69; }
done

CPP_DIR="$(cd "$CPP_DIR" && pwd)"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
R="$OUT_DIR/rootfs"
rm -rf "$R"
mkdir -p "$R"/{etc,bin,lib,lib64,proc,sys,dev,tmp,run,root,mnt,opt/kittytune,usr/bin,usr/lib}
chmod 1777 "$R/tmp"

# ---------------------------------------------------------------------------------------------
# ktcdm (bridge + sidecar, one binary), compiled exactly as scripts/setup-cdm-sidecar.sh does.
# ---------------------------------------------------------------------------------------------
log "compiling ktcdm"
g++ -std=c++17 -O2 -pthread \
  "$CPP_DIR/widevine/WidevineBridge.cpp" \
  "$CPP_DIR/cdmsidecar/CdmSidecar.cpp" \
  -ldl -o "$R/opt/kittytune/ktcdm"
chmod 0755 "$R/opt/kittytune/ktcdm"

# ---------------------------------------------------------------------------------------------
# Tools the app runs inside the distro to fetch the CDM. /bin/sh to run scripts at all.
# ---------------------------------------------------------------------------------------------
copy_bin() {
  local src dst
  src="$(command -v "$1")" || { log "cannot find $1"; exit 69; }
  dst="$R${2:-$src}"
  mkdir -p "$(dirname "$dst")"
  cp -L "$src" "$dst"
  chmod 0755 "$dst"
}
# GNU tar fails under WSL1 (newer syscalls return ENOSYS), and so does dpkg-deb (it forks GNU tar).
# busybox's ar + tar use simpler syscalls and work under WSL1 — so the CDM fetch at setup is
# curl + busybox ar + xz + busybox tar, and we ship exactly those. Nothing from host coreutils.
copy_bin sh /bin/sh    # dash
copy_bin curl
copy_bin xz            # real xz: busybox is often built without the xz decompressor
# One busybox provides ar, tar, mkdir, mktemp, find, install, cp, rm, cat, wc, awk, ls, …
BB="$(command -v busybox || true)"
[ -n "$BB" ] || { log "busybox required (apt-get install -y busybox-static)"; exit 69; }
cp -L "$BB" "$R/usr/bin/busybox"
chmod 0755 "$R/usr/bin/busybox"
# Only link applets this busybox really has: a link to a missing applet fails only at the user's setup.
# REQUIRED is what WslSidecarSetup.CDM_SCRIPT runs (besides the real curl and xz); a busybox without one
# of them must fail the build, not the user's setup.
REQUIRED="ar tar mkdir mktemp cp chmod mv find head rm"
OPTIONAL="cat wc awk ls chown ln sed grep"
APPLETS="$("$BB" --list)"
for applet in $REQUIRED; do
  echo "$APPLETS" | grep -qx "$applet" || { log "this busybox has no '$applet' applet, which the CDM fetch needs"; exit 69; }
  ln -sf busybox "$R/usr/bin/$applet"
done
for applet in $OPTIONAL; do
  if echo "$APPLETS" | grep -qx "$applet"; then ln -sf busybox "$R/usr/bin/$applet"; fi
done

# ---------------------------------------------------------------------------------------------
# The shared-library closure of everything above. The interpreter (ld-linux) too.
# ---------------------------------------------------------------------------------------------
add_closure() {
  (ldd "$1" 2>/dev/null | grep -oE '/[^ ]+\.so[^ ]*' || true) | sort -u | while read -r lib; do
    [ -f "$lib" ] || continue
    local d="$R$(dirname "$lib")"
    mkdir -p "$d"
    cp -n -L "$lib" "$d/" 2>/dev/null || true
  done
  local interp
  interp="$(readelf -l "$1" 2>/dev/null | sed -n 's/.*interpreter: \(.*\)]/\1/p' || true)"
  if [ -n "$interp" ]; then
    mkdir -p "$R$(dirname "$interp")"
    cp -n -L "$interp" "$R$interp" 2>/dev/null || true
  fi
}
# Resolve closures from the ORIGINALS on the build host (their paths still exist there).
while IFS= read -r -d '' f; do add_closure "$f"; done < <(find "$R" -type f -perm -u+x -print0)
# The CDM (libwidevinecdm.so) is fetched at setup, NOT bundled, but its NEEDED libs must already be
# in the rootfs when it is dlopen'd. ktcdm's own closure covers most; name them all to be sure, plus
# libnss_files for getpwnam(root).
for soname in libdl.so.2 libpthread.so.0 libm.so.6 libgcc_s.so.1 libc.so.6 libnss_files.so.2; do
  for dir in /lib/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu /lib64 /usr/lib; do
    if [ -f "$dir/$soname" ]; then
      mkdir -p "$R$dir"; cp -n -L "$dir/$soname" "$R$dir/"; break
    fi
  done
done

# Without an ld.so.cache the loader does not search the multiarch dirs (/usr/lib/x86_64-linux-gnu),
# so ktcdm fails with "libdl.so.2: cannot open shared object file". Build the cache for this rootfs.
printf '/lib/x86_64-linux-gnu\n/usr/lib/x86_64-linux-gnu\n/lib64\n/usr/lib\n/lib\n' > "$R/etc/ld.so.conf"
ldconfig -r "$R" || { log "ldconfig -r failed: without ld.so.cache ktcdm cannot load its libraries"; exit 70; }
[ -s "$R/etc/ld.so.cache" ] || { log "ldconfig wrote no ld.so.cache"; exit 70; }

# ---------------------------------------------------------------------------------------------
# TLS trust store, so curl can reach dl.google.com.
# ---------------------------------------------------------------------------------------------
if [ -f /etc/ssl/certs/ca-certificates.crt ]; then
  mkdir -p "$R/etc/ssl/certs"
  cp /etc/ssl/certs/ca-certificates.crt "$R/etc/ssl/certs/"
fi

# ---------------------------------------------------------------------------------------------
# The skeleton WSL needs to launch and to resolve root.
# ---------------------------------------------------------------------------------------------
printf 'root:x:0:0:root:/root:/bin/sh\n'            > "$R/etc/passwd"
printf 'root:x:0:\n'                                > "$R/etc/group"
printf 'root:*:19000:0:99999:7:::\n'                > "$R/etc/shadow"
printf 'passwd: files\ngroup: files\nhosts: files dns\n' > "$R/etc/nsswitch.conf"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n'   > "$R/etc/resolv.conf"
cat > "$R/etc/wsl.conf" <<'WSLCONF'
[boot]
systemd=false
[user]
default=root
[interop]
appendWindowsPath=false
[automount]
enabled=false
WSLCONF

# ---------------------------------------------------------------------------------------------
# Pack it.
# ---------------------------------------------------------------------------------------------
log "packing"
( cd "$R" && tar --numeric-owner -czf "$OUT_DIR/ktroot.tgz" . )
SIZE="$(du -h "$OUT_DIR/ktroot.tgz" | cut -f1)"
SHA="$(sha256sum "$OUT_DIR/ktroot.tgz" | cut -d' ' -f1)"
log "wrote $OUT_DIR/ktroot.tgz ($SIZE)"
log "sha256=$SHA"
# One machine-readable line on stdout for CI to capture.
printf '{"tgz":"%s","size":"%s","sha256":"%s"}\n' "$OUT_DIR/ktroot.tgz" "$SIZE" "$SHA"
