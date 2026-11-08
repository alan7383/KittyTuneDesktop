#!/usr/bin/env python3
"""Exercise the shipped launcher/runtime, not the build machine's full JDK.

Local Hyprland session: python3 scripts/check-linux-dbus.py /path/to/KittyTune
CI: dbus-run-session -- xvfb-run -a python3 scripts/check-linux-dbus.py \
    /path/to/opt/kitty-tune --private-watcher
Requires python3-dbus, python3-gi and playerctl; never uses the user's profile.
"""

import argparse
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import threading
import time
import traceback

import dbus
import dbus.service
from dbus.mainloop.glib import DBusGMainLoop
from dbus.mainloop import NULL_MAIN_LOOP
from gi.repository import GLib


WATCHER = "org.kde.StatusNotifierWatcher"
MPRIS = "org.mpris.MediaPlayer2.kittytune"
PLAYER = "org.mpris.MediaPlayer2.Player"
PROPS = "org.freedesktop.DBus.Properties"


class Watcher(dbus.service.Object):
    def __init__(self, bus):
        self.name = dbus.service.BusName(WATCHER, bus, do_not_queue=True)
        super().__init__(bus, "/StatusNotifierWatcher")
        self.items = []

    @dbus.service.method(WATCHER, in_signature="s", out_signature="")
    def RegisterStatusNotifierItem(self, service):
        self.items.append(str(service))


def eventually(check, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(0.2)
    raise AssertionError("Timed out waiting for " + check.__doc__)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", type=Path, help="directory containing bin/KittyTune and lib/runtime")
    parser.add_argument("--private-watcher", action="store_true", help="use only inside dbus-run-session")
    parser.add_argument("--log", type=Path, default=Path("build/reports/linux-dbus.log"))
    args = parser.parse_args()
    app = args.app.resolve()
    release = (app / "lib/runtime/release").read_text()
    modules = next(line for line in release.splitlines() if line.startswith("MODULES="))
    modules = set(modules.split("=", 1)[1].strip('"').split())
    missing = {"jdk.security.auth", "java.security.jgss"} - modules
    if missing:
        raise AssertionError("Packaged runtime is missing: " + ", ".join(sorted(missing)))
    print("PASS: packaged runtime contains both D-Bus authentication modules", flush=True)

    DBusGMainLoop(set_as_default=True)
    bus = dbus.SessionBus()
    if args.private_watcher:
        if bus.name_has_owner(WATCHER):
            raise RuntimeError("--private-watcher requires an isolated session bus")
        watcher = Watcher(bus)
    else:
        if not bus.name_has_owner(WATCHER):
            raise RuntimeError("No StatusNotifierWatcher in this desktop session")
        watcher = None
    if bus.name_has_owner(MPRIS):
        raise RuntimeError("Close the running KittyTune before checking the package")

    loop = GLib.MainLoop()
    failures = []
    args.log.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="kittytune-dbus-") as profile, args.log.open("w") as log:
        env = os.environ.copy()
        env.update(HOME=profile, APPDATA=profile + "/config", LOCALAPPDATA=profile + "/cache",
                   XDG_CONFIG_HOME=profile + "/config", XDG_CACHE_HOME=profile + "/cache",
                   XDG_DATA_HOME=profile + "/data")
        # The embedded JVM reads user.home independently from HOME on Linux.
        env["JAVA_TOOL_OPTIONS"] = "-Duser.home=" + profile
        # Do not let a developer's JDK or library overrides mask a broken package.
        for key in ("JAVA_HOME", "JDK_HOME", "CLASSPATH", "LD_LIBRARY_PATH", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
            env.pop(key, None)
        process = subprocess.Popen([str(app / "bin/KittyTune")], env=env,
                                   stdout=log, stderr=log, start_new_session=True)

        def verify():
            try:
                # This worker only makes blocking calls. Keep its connection off GLib's
                # main-thread dispatcher so closing it cannot race a dispatch callback.
                client = dbus.bus.BusConnection(os.environ["DBUS_SESSION_BUS_ADDRESS"], mainloop=NULL_MAIN_LOOP)
                sni = "org.kde.StatusNotifierItem-" + str(process.pid)

                def exported():
                    """packaged app to export MPRIS and register its tray item"""
                    if process.poll() is not None:
                        raise AssertionError(f"Launcher exited with {process.returncode}")
                    if watcher is not None:
                        registered = sni in watcher.items
                    else:
                        # Some real watchers expose malformed introspection XML. Properties has
                        # a standard signature, so no introspection is needed for this call.
                        props = dbus.Interface(client.get_object(WATCHER, "/StatusNotifierWatcher", introspect=False), PROPS)
                        registered = any(str(item).startswith(sni + "/") or str(item) == sni
                                         for item in props.Get(WATCHER, "RegisteredStatusNotifierItems", signature="ss"))
                    return client.name_has_owner(MPRIS) and registered

                eventually(exported)
                root = client.get_object(MPRIS, "/org/mpris/MediaPlayer2")
                props = dbus.Interface(root, PROPS)
                assert str(props.Get("org.mpris.MediaPlayer2", "Identity")) == "KittyTune"
                player = dbus.Interface(root, PLAYER)
                player.Pause()
                props.Set(PLAYER, "Volume", dbus.Double(0.37))

                def volume_changed():
                    """MPRIS volume setter to update the player"""
                    return abs(float(props.Get(PLAYER, "Volume")) - 0.37) < 0.01

                eventually(volume_changed, 10)
                result = subprocess.check_output(["playerctl", "-p", "kittytune", "volume"], text=True)
                assert abs(float(result.strip()) - 0.37) < 0.01, result
                print("PASS: MPRIS properties, Pause, volume round-trip and playerctl", flush=True)

                item = client.get_object(sni, "/StatusNotifierItem")
                assert str(dbus.Interface(item, PROPS).Get("org.kde.StatusNotifierItem", "Status")) == "Active"
                menu = dbus.Interface(client.get_object(sni, "/MenuBar"), "com.canonical.dbusmenu")
                revision, layout = menu.GetLayout(0, 1, dbus.Array([], signature="s"))
                assert int(layout[0]) == 0 and len(layout[2]) > 0
                dbus.Interface(item, "org.kde.StatusNotifierItem").Activate(100, 100)
                menu.Event(1, "clicked", dbus.Int32(0, variant_level=1), dbus.UInt32(0))
                print("PASS: tray watcher registration, properties, DBusMenu and Activate", flush=True)
                client.close()
            except BaseException:
                failures.append(traceback.format_exc())
            finally:
                GLib.idle_add(loop.quit)

        worker = threading.Thread(target=verify, daemon=True)
        worker.start()
        try:
            loop.run()
        finally:
            os.killpg(process.pid, signal.SIGTERM) if process.poll() is None else None
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
        if failures:
            print("\n".join(args.log.read_text(errors="replace").splitlines()[-80:]), flush=True)
            raise AssertionError(failures[0] + f"\nPackaged app log: {args.log.resolve()}")
    print(f"PASS: shipped Linux app D-Bus smoke test (log: {args.log})")


if __name__ == "__main__":
    main()
