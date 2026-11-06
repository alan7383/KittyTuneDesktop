# Linux media controls (MPRIS)

KittyTune exports `org.mpris.MediaPlayer2.kittytune` on the session bus, at
`/org/mpris/MediaPlayer2`. Desktop shells and `playerctl` can discover it without
shell-specific configuration.

Linux uses `kittytune` as the window class, desktop ID and icon name. The name
`kitty-tune` can fall back to the terminal's `kitty` icon in Qt icon themes. On
startup, the icon installer publishes `kittytune.desktop` and hides legacy launcher
IDs with compatible user overrides. Existing launch commands, MIME associations
and desktop actions are preserved. The package/binary name stays `kitty-tune`.
A dock pin saved under the old ID may need to be removed and pinned again; the app
does not edit shell settings. Restart the updated application to change WM_CLASS.

The Linux package and the launcher's user override include `Audio` and `Music`
categories. Shells such as ii-p3drovfx can therefore recognize KittyTune as a music
application and honor their music-player priority setting.

Metadata includes track identity, title, artist, album, cover and duration when
known. Local cover paths are exported as encoded `file:///` URLs, including paths
with spaces or `#`. Tracks without artwork export the bundled KittyTune icon from
a local cache, so shells replace the previous track's artwork. No artwork network
request is needed for this fallback.

Play, pause, stop, next/previous, seeking, volume, shuffle, repeat and playback
speed use the regular MPRIS interfaces. Position is in microseconds on D-Bus;
Quickshell presents it in seconds. Seek and engine resynchronization emit
`Seeked`; duration backfill and other metadata changes emit `PropertiesChanged`.

## Integration tests

Install Java 21, `dbus-run-session`, `playerctl`, and Quickshell (`qs`), then run:

```sh
KITTYTUNE_II_CONFIG=/path/to/ii-p3drovfx/dots/.config/quickshell/ii \
  dbus-run-session -- ./gradlew :test --tests '*MprisServiceTest*' --no-daemon
```

The same command accepts the installed ii configuration directory. Without
`KITTYTUNE_II_CONFIG`, the ii-specific test is skipped. The Quickshell test is
skipped if `qs` is unavailable; check the test report's skipped count.

Tests use a private bus and an offscreen Quickshell instance. They exercise the
application's MPRIS service with real D-Bus calls, `playerctl`, and ii's unchanged
`MprisController.qml` and `CoverArt.qml`. Only settings, translation, cache-location
and file-URL helpers are isolated. Tests never edit the source or installed
shell configuration.

Coverage includes discovery, music priority and pinning, metadata, local image
loading, replacing missing artwork, all media controls, position/rate updates,
track changes, late duration, unloading and command callback serialization.
They validate the MPRIS boundary; they do not play audio or validate external
lyrics/video providers. The dotfile's own local-library queue is separate from
external MPRIS applications.

## Audio mixer and tray

Linux playback streams identify themselves as KittyTune, with the application
icon and music role, independently of MPRIS. Selecting a device routes only this
process's streams; it does not change the system's default output. A manual device
selection is kept when another app changes that system default. This also covers
auxiliary playback and device reopening.

The native tray exports track title and artist through SNI ToolTip and announces
updates with NewToolTip. The original shell configuration requires no changes.

Opt into silent live audio tests (temporary virtual sinks, cleaned up afterward):

```sh
KITTYTUNE_LINUX_AUDIO_TEST=1 ./gradlew :test --tests LinuxAudioIntegrationTest --no-daemon
```

Run `LinuxStatusNotifierServiceTest` under `dbus-run-session`; it supplies its own
watcher, so the tests do not depend on the user's tray or insert test items into it.
See [the compatibility audit](ii-p3drovfx-compatibility.md) for consumer-side
restrictions such as the shell's internal queue and hardcoded dock grouping.
