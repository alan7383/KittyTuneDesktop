# Linux package D-Bus verification (issue #107)

The official `v1.4.9` Arch x86_64 archive lacks `jdk.security.auth` and
`java.security.jgss` in `opt/kitty-tune/lib/runtime/release`. Its tag (`272daef`)
predates `f52f530`, which added `jdk.security.auth` to the runtime. Rebuilding
master and rebuilding that release tag therefore produce different runtimes.

dbus-java needs `com.sun.security.auth.module.UnixSystem` for Unix socket
authentication. A test running on a complete development JDK cannot detect its
absence from a jlink image. Keep the authentication modules explicit in
`nativeDistributions.modules`; `java.security.jgss` is also a dependency of
`jdk.security.auth` on JDK 21.

## Run against a package

Install Python's D-Bus and GLib bindings, playerctl, dbus-run-session, Xvfb,
xauth, and the archive tools. On Ubuntu these are `python3-dbus python3-gi
playerctl dbus-x11 xvfb xauth zstd` (plus `dpkg` for Debian packages).

```sh
bash gradlew packageReleaseDeb -PdesktopOnly=true
bash scripts/check-linux-package.sh build/compose/binaries/main-release/deb/kitty-tune_1.4.9_amd64.deb
# The same command accepts a .pkg.tar.zst archive.
```

The check extracts the final archive and runs its actual launcher and embedded
runtime on a private session bus under Xvfb. A minimal StatusNotifierWatcher
accepts tray registrations. It requires both runtime modules, MPRIS export,
Pause, a volume write/read round-trip, playerctl interoperability, tray
registration, tray properties, DBusMenu layout, and Activate. It uses a temporary
application profile and terminates its test process on completion or failure.
Application output is saved to `build/reports/linux-dbus.log`.

The release workflow runs this check on the final x86_64 and aarch64 Arch
archives before publishing them. The AppImage payload comes from the same
extracted Debian package. The pull request workflow checks the release Debian
package and the MPRIS/tray integration tests.

## Run in the real desktop session

Close any running KittyTune, then run:

```sh
python3 scripts/check-linux-dbus.py build/compose/binaries/main-release/app/KittyTune
```

This uses the desktop's real StatusNotifierWatcher and bus, with a temporary
profile. Tested locally on Hyprland: the rebuilt launcher and extracted Debian
and Arch packages passed; the original official v1.4.9 archive was rejected for
the two missing modules. ARM64 execution is covered by its release runner;
local verification was x86_64. Launching the original archive with the module
preflight deliberately bypassed reproduced failures for both MPRIS and SNI:
`com/sun/security/auth/module/UnixSystem`. The rebuilt archive passed the same
D-Bus interactions.

Publishing a new release is required to deliver the corrected runtime to users
of the old packages; editing the branch cannot change existing release assets.
