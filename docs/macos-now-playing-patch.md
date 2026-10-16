# KittyTune Desktop 1.4.2: macOS Now Playing proof of concept

This is an **experimental community contribution**, based on v1.4.2 of
[`alan7383/KittyTuneDesktop`](https://github.com/alan7383/KittyTuneDesktop).
The author of the contribution tested a standalone app on an Apple Silicon MacBook Pro,
including Boring Notch, Control Center, media commands, album artwork and relaunches.
**This patch has not been independently compiled or run against a fresh upstream checkout.**

## Changes

- `src/main/swift/KittyTuneNowPlaying.swift`: helper using MediaPlayer's Now Playing info
  and remote command APIs, with commands written to stdout and updates read from stdin.
- `MacNowPlayingService.kt`: launches helper and bridges player updates / commands.
- `PlayerViewModel.kt`: connects the macOS service to existing player actions.
- `AppIconRuntime.kt`: avoids overriding the app's Dock icon with a square icon on macOS.
- `scripts/build-macos-nowplaying.sh`: builds Swift helper and installs it under
  the packaged Compose `.app`'s `Contents/Helpers` directory.

## Apply/build on macOS

Prerequisites: macOS, Xcode Command Line Tools with `swiftc`, project Java/JDK 21,
working Gradle wrapper. From the **clean upstream v1.4.2 source checkout**:

```bash
git apply KittyTune-1.4.2-macOS-NowPlaying-REVIEWED.patch
bash scripts/build-macos-nowplaying.sh
open build/compose/binaries/main/app/KittyTune.app
```

To test with `./gradlew run` instead of a packaged app:

```bash
bash scripts/build-macos-nowplaying.sh --helper-only
./gradlew run
```

An optional `KITTYTUNE_NOWPLAYING_HELPER` environment variable overrides the
helper's executable location for local experiments. No username-specific paths
or user tokens are included. When packaged, the helper is found relative to
`java.home` inside the Compose `.app`.

### Important limitations / maintainers' notes

- Not wired into upstream CI or Gradle as an automatic build task yet. Use the supplied
  macOS shell script, or integrate it into `build.gradle.kts` before merging.
- Only builds the Swift helper for the host architecture; release pipelines may need
  arm64 and x86_64 binaries/universal packaging, signing and notarization.
- Embedding the helper after Compose packaging can invalidate an existing code signature.
  Perform any release signing/notarization **after** helper installation.
- The protocol currently uses tab-separated records on stdin/stdout, without versioning;
  consider a structured IPC protocol and better error/restart handling before merging.
- Remote play/pause/seek and album-artwork loading were tested manually by the contributor.
  Their compatibility with every Now Playing client is not guaranteed.
- The helper currently requires an accessible HTTPS image URL for album artwork and
  downloads artwork in memory. A production integration should consider cancellation,
  URL validation, content-type/size limits and caching.
- The helper may need additional lifecycle/error handling to cope with crashes,
  media-session competition and system sleep/wake transitions.

## Release hygiene

Do not commit personal login data, Gradle caches, app binaries or a prebuilt helper.
Review third-party code signing, sandboxing and distribution before shipping.
