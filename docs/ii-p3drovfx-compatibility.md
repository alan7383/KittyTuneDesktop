# ii-p3drovfx compatibility audit

Scope: repository-wide text search in `/home/alan/ii-p3drovfx`, followed by review
of the media, taskbar, tray, audio, automation and launcher integration points.
The search covered 4,800 readable text files and found 214 files referencing the
integration APIs. This is a source audit plus a live audio-identity check, not an
end-to-end runtime test of every shell widget or external provider. The dotfiles
were not modified.

## Supported integration contracts

| Feature | Contract / evidence | Assessment |
| --- | --- | --- |
| Bar, vertical bar, dock media widget, desktop widgets, Dynamic Island, lock screen | `MprisController`, title/artist/album/artwork, PlaybackStatus, Position and capabilities | Standard MPRIS, supported by KittyTune. |
| Play/pause, next/previous, seek, volume, repeat, shuffle, rate | MPRIS methods and writable properties | Supported; existing integration tests cover these calls. |
| Keyboard/media keys and touch gestures | `playerctl`, no player whitelist | Supported; default selection among several players is the shell/playerctl policy. |
| Music-player priority and pinning | Desktop categories, Identity, DesktopEntry and bus name | Supported by the canonical `kittytune` identity and Audio/Music categories. |
| Launcher “Now Playing” and AI media controls | Forward to the same MPRIS controller | No additional app-specific protocol required. |
| Automation media conditions | Matches Identity/DesktopEntry/dbusName and playing state | Can match KittyTune normally. |
| Bluetooth earbud pause/resume | Enumerates MPRIS players and calls Pause/Play | No KittyTune exclusion; hardware behavior was not tested. |
| Cava visualizers | Standard audio monitor, enabled when any MPRIS player is playing | No KittyTune-specific integration needed; not runtime-tested with Cava here. |
| Online lyrics and music-video background | Search using title/artist/duration, position via MPRIS/playerctl | Required metadata is present. Results depend on external services and shell settings. |
| Taskbar icon and window association | WM_CLASS, desktop entry and theme icon | Canonical ID was corrected to `kittytune`; existing window and rendered-icon tests cover it. |
| System tray activation and context menu | StatusNotifierItem + DBusMenu | Implemented, including a live title/artist tooltip. |
| Default audio-file application | Desktop MIME types + Exec/file arguments | Supported; being the user's default remains a user choice. |

## App-side improvements implemented after the audit

- Linux audio declares `application.name=KittyTune`, an application ID and icon,
  process ID and music role before ALSA initialization. Both PipeWire's ALSA
  plugin and PulseAudio's ALSA plugin receive the metadata.
- Output selection uses per-stream routing scoped to the exact process ID.
  It never calls `set-default-sink`. Crossfading/auxiliary playback uses the same
  opener. Changing the system default no longer clears a manually selected app output.
- Sink enumeration uses JSON and `get-default-sink`, so translated command output
  does not break device detection.
- The tray exports a typed SNI `ToolTip` with track title and artist, updates it on
  track/artist/icon changes, and clears it when the track is unloaded. Menu, icon
  and tooltip signals now have the correct D-Bus signatures. `Properties.Get`
  correctly returns variants for structured values.

Verification: the combined Linux identity, rendered icon, MPRIS, tray and live
silent-output suite passed 36 tests with no skips. Three additional audio tests
also passed with an isolated ALSA configuration selecting the PulseAudio plugin
against the system's pipewire-pulse server. Virtual sinks were removed after the
checks. The live checks verified stream identity, explicit device selection,
return to default, and preservation of the system default and other apps' streams.
The tests do not replace the running user application.

The metadata mechanisms are documented by
[PipeWire client.conf(5)](https://docs.pipewire.org/page_man_pipewire-client_conf_5.html)
and [PulseAudio application properties](https://wiki.freedesktop.org/www/Software/PulseAudio/Documentation/Developer/Clients/ApplicationProperties/).
They configure this process; the implementation does not write ALSA, PipeWire,
PulseAudio or shell configuration files.

## Remaining consumer-side limits

### Dock smart grouping: shell-side name heuristic

`ii/modules/ii/dock/DockContent.qml:2979` classifies any ID containing `kitty` as a
terminal/file-manager category, before checking its media list. Thus `kittytune`
can be grouped with terminals when smart grouping is enabled. This does not undo
the icon fix or affect playback. Audio/Music desktop categories are not consulted
by this particular function. The constraint comes from the shell heuristic;
adding MPRIS fields or desktop categories cannot correct that categorization.

Hyprland's optional swallow regex also contains `kitty`, but swallowing is
explicitly disabled in the supplied configuration (`hyprland/general.lua:283`).
There is no default functional failure from that rule.

### Intentionally internal features

The shell local queue is explicitly restricted to its own `ii_local` helper
(`ii/services/LocalMediaService.qml:23`,
`ii/modules/ii/background/MediaModeQueue.qml:12`). External players, including
KittyTune, Spotify and browsers, do not supply that queue.

Local lyric sidecars are similarly gated on the internal local session
(`ii/services/LyricsService.qml:35`). The shell can fetch online lyrics for
KittyTune, but does not automatically consume KittyTune's own cached/LRC lyrics.
Implementing MPRIS TrackList or adding custom lyric metadata alone would not
change these explicit consumer-side restrictions.
