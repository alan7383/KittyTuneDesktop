# Desktop stability & memory overhaul

This document lists everything changed in this branch, why, and how it was verified. It is written
to be pasted into (or linked from) the pull request.

Every change was measured or reproduced on a real machine (Windows 11, two monitors, JDK 25),
running the app against a copy of a real user profile. Linux changes are covered by code review
and by keeping the behaviour identical on the D-Bus side.

## Memory

### Result

Muted playback driven through the Windows media session: start, then skip to the next track every
30 s for six minutes (12 tracks), sampling private memory each time.

| | start | 3 min | 6 min |
|---|---|---|---|
| this branch | 537 MB | 605 MB | **712 MB, flat for the last 2.5 min** |

Java heap stays at ~215 MB committed throughout; the rest is native (Skia/DirectX, FFmpeg, threads).
The leaks fixed below do not show in a clean run like this — they add up on network errors, decode
failures, second launches and comment browsing — which is how an install reaches the 1.7 GB reported.

### Before

Measured on a running install before the change: **784 MB private memory, but only 181 MB of Java
heap committed (56 MB used) and 90 MB of class metadata**. The rest — roughly half a gigabyte — is
native memory. So the growth users report is mostly *outside* the heap, which is why `-Xmx` alone
never fixed it.

### Native FFmpeg contexts leaked on every error path

`FFmpegFrameGrabber` keeps its demuxer and codec contexts in native memory that garbage collection
never frees; only `release()` does. Several paths skipped it:

| Where | What leaked | When |
|---|---|---|
| `AudioEngine.recoverStream` | one grabber per failed reconnect attempt | **every 0.4–2 s for as long as the network is down** (attempts are unbounded) |
| `BeatAnalyzer` (HLS windows) | up to 3 grabbers + their HTTP streams per track | any decode error during automix analysis |
| `BeatAnalyzer`, `AudioScannerManager`, `AudioEngine` cleanup | the grabber | whenever `stop()` threw, since `release()` came after it in the same `try` |
| `DownloadManager.remuxHls`, `RainPlayer` | grabber and recorder | any failure mid-conversion |

Fixed with one shared helper (`audio/FFmpegGrabbers.kt`) that calls `release()` alone (`stop()` is
an alias for it), and `use {}` / `finally` on every path.

### Language detection pulled every language model into the heap for good

The comment "Translate" button asks Lingua whether a comment is in the reader's language. The
detector was built with all 75 languages in high-accuracy mode, so the first comment on screen
loaded every n-gram model — several hundred megabytes — into static caches that were never emptied.

Now: low-accuracy mode (trigram models only, a fraction of the size, plenty for a yes/no check),
built lazily on first use, and the models are unloaded after a minute without a request.

### Other

- **Single instance.** Closing the window only hides it to the tray, so clicking the shortcut again
  started a *second* full app — a second JVM, renderer and player, both writing the same database.
  A second launch now finds the first through a lock file, asks it to show its window over a
  loopback socket, and exits before loading anything (`core/SingleInstance.kt`).
- **Skia GPU resource cache 256 MB → 64 MB** (`-Dskiko.gpu.resourceCacheLimit`). Same six-minute
  track-switching run, one before and one after: steady state ~760 MB → ~710 MB private memory.
- **Image memory cache 128 MB → 64 MB.** Decoded covers are Skia memory, invisible to `-Xmx`, and
  the GPU holds its own copy of what is on screen. Evicted images come back from the 256 MB disk
  cache in milliseconds.
- **A new HTTP client per request.** `ProxyManager.getOkHttpClient()` and several getters built on it
  (telemetry, downloads, Musixmatch, updates, uploads, short-link resolving) created a fresh
  `OkHttpClient` on every call — each with its own connection pool, which keeps sockets and TLS
  buffers alive for five minutes, and its own dispatcher threads. Playback telemetry alone fires
  several requests per track; a thread dump after a few minutes of listening showed a dozen idle
  `OkHttp telemetry.soundcloud.com` threads. There is now one shared client per proxy configuration
  (rebuilt when the proxy changes); callers needing other timeouts derive it with `newBuilder()`,
  which shares the pool and threads.
- **Unclosed HTTP responses** in `MusixmatchApi` (translation and romanization, on every non-2xx
  answer) and in the short-link resolver on the home screen leaked a pooled connection each.

## Lag / UI smoothness

Three places recomposed far more of the UI than they needed to:

- **The whole theme was rebuilt every five seconds.** `KittyTuneTheme` observed the entire preference
  map, and the player saves its position there every five seconds — so the Material colour scheme was
  generated again (material-kolor, not cheap) and a new `Typography` was handed down, recomposing every
  piece of text in the app. The theme now reads only its own settings, compared as a value
  (`distinctUntilChanged`), and remembers the typography per font setting.
- **`MainScreen` recomposed 4–5 times a second during playback** because it read the playback position
  to pass it to the automix *debug* overlay, usually hidden. The overlay now takes a lambda.
- **The player bar recomposed on every position tick.** The progress row is its own composable now, and
  the "track is nearly over" flag is a `derivedStateOf` that changes once per track.

### Home screen scrolling and screen transitions

- **Scrolling stalled on every carousel.** `ScrollableLazyRow` turned the vertical wheel into sideways
  motion whenever the pointer crossed a row, with an instant `scrollBy` jump: scrolling down the home
  screen stopped at each carousel and the row lurched a card at a time. The vertical wheel now always
  scrolls the page; Shift + wheel and sideways touchpad swipes scroll a row, which Compose animates.
- **Artist cards parsed an XML file on the UI thread.** `painterResource(path)` only caches per call-site
  instance, so every round card scrolled into view opened and parsed the default-avatar vector twice
  (`error` and `fallback`). It is parsed once for the whole app now (`ui/common/DefaultAvatar.kt`).
- **Carousel edges faded into the wrong colour** (`surface` instead of the panel's
  `surfaceContainerLow`), leaving a visible band at both ends of every row. Home carousels now use the
  shared `ScrollableLazyRow` instead of their own copy of the arrow code.
- **Screen transitions**: navigation used navigation-compose's default 700 ms cross-fade for everything.
  Now Material 3 motion (`ui/main/NavTransitions.kt`): *fade through* between the sidebar's own
  destinations, *shared axis X* (30 dp slide + fade, reversed on back) when going deeper, ~300 ms.
- Home ↔ search results ↔ loading cross-fade instead of swapping in one frame; hover highlights on home
  tiles and cards ease in and out; the mix card grows smoothly when its artists row arrives.
- Measured with Skiko's long-frame log over repeated wheel scrolls of the home screen: ~58–59 fps
  while scrolling. The remaining frames over 20 ms are spent in `Direct3DRedrawer.flush` — the CPU
  waiting on the GPU — with the UI thread about 25 % busy (JFR); the GPU cache limit made no difference.

### Continuous rendering while music plays

Measured with Skiko's frame counter (`-Dskiko.fps.enabled=true`) and per-thread CPU from
`jcmd Thread.print`:

| | before | after |
|---|---|---|
| player bar, playing, default wavy seek bar | ~60 fps, UI thread ~12 % of a core | **33 fps**, UI thread ~6–7 % |
| paused | 0–3 fps | 0–3 fps |
| window minimised or in the tray | kept animating | **0 fps** |

- **The wavy seek bar** (the default style) cost about a quarter of a core by itself: compared with the
  plain bar, the same playback rendered at 60 fps instead of a few frames a second. Two loops each asked
  for every frame — Material's wave animation and a per-frame glide between position updates. The wave
  is now drawn by the slider itself, and both move on one 30 fps tick paced by `delay`, which does not
  request frames. It looks the same.
- **Animations stop when nobody can see them.** New `LocalWindowSeen`: false when the window is
  minimised, hidden in the tray, or — on Windows — completely covered by the foreground window above it
  in z-order. Losing focus is deliberately *not* enough: with two monitors the player often sits on one
  while you work on the other, and it keeps animating there. The wave, the squiggly bar, the full
  player's fluid background and its light mesh all honour it.
- **The full player recomposed at the display rate.** The drifting lights (one turn every 26 s) were a
  state advanced by awaiting every frame and read at the top of the screen, so the whole full player
  recomposed ~60 times a second. Now 30 fps, read only while drawing. The fluid background's 30 fps
  ticker also awaited frames — which requests them — so the window rendered at 60 fps anyway; it is
  paced by `delay` now.
- Tried and **reverted**: rendering the fluid background on a small CPU surface and stretching it. The
  runtime shader is cheap on the GPU and expensive on the CPU; it made the full player slower.

## Windows

### Full player minimised itself when another monitor was clicked

Cause: the full player used Compose's `WindowPlacement.Fullscreen`, which Skiko implements on Windows
with AWT's *exclusive* `GraphicsDevice.setFullScreenWindow`. AWT deliberately iconifies an exclusive
full-screen window as soon as it loses activation — so any click on a second monitor minimised the
app. Reproduced with a 20-line program: exclusive window + activate another window → `ICONIFIED`.

`master` already switched Windows to a borderless full screen (`WindowsFullScreen`), but on leaving
it the window was restored twice — once through Compose's window state, once natively — in no
guaranteed order, so the frame could come back at the wrong size or position. Windows now has a
single owner for the transition (`WindowsFullScreen.exit`).

New test `borderlessFullScreenSurvivesFocusLossAndRestoresBounds` drives a real window: entering
covers the monitor, activating another window does not minimise it, leaving restores the exact
bounds and the window frame style. Skipped on headless/non-Windows CI.

### Media flyout / lock screen / media keys (SMTC)

The SMTC bridge (`native/WindowsSmtcBridge.cs`) was rewritten and rebuilt:

- **Timeline**: position and duration are now published, so the Windows media flyout shows a
  progress bar, and **seeking from the flyout works** (new `SEEK:<ms>` message).
- **Album** is shown.
- **App icon** is embedded in the bridge, so the flyout shows KittyTune's icon instead of a blank one.
- **Play and Pause buttons did the opposite half the time**: both were wired to `togglePlayPause()`,
  so pressing Play while already playing paused. They now call `play()` / `pause()`.
- Titles containing `|` (very common: "Song | Remix") were mangled because `|` was the field
  separator. Fields are now tab-separated and control characters are stripped.
- Identical updates are no longer re-sent, and the thumbnail is only re-set when the URL changes
  (each assignment made Windows download the image again).
- The bridge is extracted into a content-hashed folder, so an update never talks to a stale copy
  still locked by an earlier instance.
- It is now a windowless executable (it still exits as soon as the app's pipe closes).
- **Media keys did nothing after launch.** The bridge (and on Linux the MPRIS service) only started
  on the first playback change, so until play was pressed inside the app the keyboard's media keys
  and the flyout had no session to talk to. The restored track is now published at startup.

Verified against the system session manager (`GlobalSystemMediaTransportControlsSessionManager`):
title with `|`, artist, album, position 0:30 / 3:00 and status are reported correctly, and
`TryChangePlaybackPositionAsync` / `TryPauseAsync` reach the app as `SEEK:60000` / `CMD:PAUSE`.

### Registered as a music app ("Open with KittyTune")

- The MSI (and deb/rpm) now register KittyTune for mp3, flac, m4a, aac, ogg, opus, wav and wma, so it
  appears under "Open with" and in *Settings → Apps → Default apps*. Nothing is taken over
  without the user choosing it.
- A file opened that way is added to the local library and played. If KittyTune is already running
  (often hidden in the tray), the second launch forwards the paths to it and exits.

## Linux

### One MPRIS player instead of two

The app registered **two** MPRIS services (`org.mpris.MediaPlayer2.kittytune` and
`…kittytune.kde`), so GNOME, KDE, playerctl and bars listed KittyTune twice, each copy with its own
D-Bus connection and position timer. They are merged into one spec-complete service on the
canonical name, so the end4 integration that looks the player up by that name is unaffected:

- every required root and player property; Volume, Shuffle and LoopStatus are writable
- `Raise` now works (clicking the widget brings the window back, even from the tray)
- `DesktopEntry` is resolved from the installed `.desktop` file (deb, rpm and AUR name it
  differently), so shells can show the right icon
- `SetPosition` for a track that is no longer current is ignored, as the spec requires
- no more console line on every `Get` call (widgets poll `Position` constantly)

## Design fixes

- **Volume control.** The stock Material slider (tall bar thumb, thick track, built for touch) was hard
  to hit in a 64 dp bar and never showed the level. It is now a slim track that thickens and shows its
  thumb on hover, jumps to where you click, follows a drag past its ends, and always shows the level as
  a percentage; the wheel still works and the level is saved on release. The code moved out of the
  1,000-line `PlayerBar.kt` into `VolumeControl.kt`.
- **Volume now sounds even across the slider.** It set the amplitude linearly, so 50 % was only −6 dB and
  every usable level was crammed into the bottom sixth. It now uses the cubic curve desktop mixers use
  (50 % ≈ −18 dB). Saved levels are converted once, so nobody's music gets louder or quieter on update.
  The curve is applied where the slider meets the engine, so crossfade shapes are unchanged.
- **"Your mix" card** was off-style: a button with a play icon on both sides, seven moods in a 140 dp
  list that scrolled with no scrollbar (four were never found), a stray white bar beside the selection,
  and ad-hoc pastel colours per mood. It is now plain Material 3: title, status and a tonal settings
  button; every mood visible as a `FilterChip`; the artists the mix is based on (an existing but unused
  component); one button with the selected mood's description beside it. Picking another mood while a
  mix plays now offers to start that one, instead of the button only pausing the old one.

- **Tracks without a cover showed an empty black square** — in the player bar, the now-playing panel
  and the full player (local files without embedded art, or a cover that failed to load). There is
  now a placeholder in the theme's colours with a note icon, which also shows while a cover loads.
- **Local files showed a row of SoundCloud counters stuck at zero** (plays, likes, reposts,
  comments) in the track info panel. Hidden for local tracks.

## Localisation

- The home screen's listening-time card showed a hard-coded English "11 min" in every language; it
  now uses translated strings (new key `listening_stats_duration_min` in all six languages).

## Build

- `--enable-final-field-mutation` was added for JDK ≥ 25, but the option only exists from JDK 26.
  **An app packaged with JDK 25 refused to start at all** ("Unrecognized option"). Now gated on 26.

## Tests

- New: `VolumeCurveTest` (curve and lossless migration), `WindowsFullScreenTest.borderlessFullScreenSurvivesFocusLossAndRestoresBounds` (real window,
  Windows only) and `LanguageDetectionTest` (low-accuracy mode still tells ru / en / fr apart).
- `LinuxStatusNotifierLiveTest` now skips itself off Linux instead of failing — it needs a session bus.
- Full suite on Windows: 474 tests. The only failures are `VolumeNormalizationTest` (7), which need the
  native DSP library; the repo ships the `.so` only and the `.dll` is built by CI on `windows-latest`
  (`compileNativeDSP` needs g++). Unrelated to this branch.
