# KittyTune for Windows

This community build is version 1.4.10, based on KittyTuneDesktop 1.4.1 with the original Material 3 Expressive interface, Windows build fixes and Connect support. Source provenance is in [UPSTREAM.md](UPSTREAM.md).

## Run the portable version

Extract `release/KittyTune-1.4.10-Windows-Portable-x64.zip` into a writable folder and open **`KittyTune/KittyTune.exe`**. Keep the `app` and `runtime` folders beside the executable. Java is included; Android Studio and an emulator are not needed.

To switch from a running older build, use **Exit** in its tray menu first. Closing its window may leave the old version running; single-instance handling would then reopen that old process instead of starting the new build. Settings stay in the same profile.

On first launch choose guest mode or connect SoundCloud. Search, local files, playlists, favorites, queue, lyrics, audio effects, the tray player and Windows media buttons come from the upstream native app. Service availability still depends on the relevant provider and account. Windows remains an experimental platform upstream.

The application stores settings and its database in `%APPDATA%\KittyTune`, caches in `%LOCALAPPDATA%\KittyTuneCache`, and downloads in the configured music folder. “Portable” here means no installer is required; the user profile is stored in AppData. Close-to-tray behavior can be changed in settings.

The initial theme uses the same base color and MaterialKolor palette logic as mobile. The app also supports a Windows accent, dark/light/black themes and custom colors. System-derived colors can differ from those on a phone.

## Build without opening the app

Requirements: JDK 21+, Windows SDK, and either Visual Studio C++ Build Tools or MinGW `g++` on PATH. All build tools are used without changing global environment settings.

```powershell
cd desktop
.\scripts\build-windows.ps1 -JavaHome 'C:\path\to\jdk-21'
```

The script rebuilds the Windows media bridge, runs `headlessTest`, packages the app with its runtime and writes a SHA-256 checksum beside the ZIP. It does not install or launch the player. Gradle uses at most two workers and a 2 GB heap.

Individual commands, with `JAVA_HOME` pointing at JDK 21+:

```powershell
.\gradlew.bat headlessTest --no-daemon
.\gradlew.bat zipWindowsPortable --no-daemon
```

The unpacked application is under `build/compose/binaries/1.4.10/main-release/app/KittyTune/`. Packaging uses a separate directory for each version so it does not overwrite an older running release.
To produce an MSI instead, use `packageReleaseMsi` (requires the Windows packaging tools used by jpackage).

Do not use `run` or the upstream interactive window tests during a background build. The dedicated `headlessTest` task uses a separate profile under `build/test-profile` and an explicit selection of noninteractive tests.

## Display scaling

The bundled Windows launcher is Per-Monitor V2 DPI aware. Windows scaling (including 125% and 150%) remains enabled; no fixed 1920×1080 canvas or forced 100% system scale is used. Application zoom now scales text and controls equally, in both the main window and mini-player. Static Windows text uses pixel-aligned glyph positioning on Compose's grayscale rendering surfaces while preserving the mobile font and sizes.

Use **Ctrl+0** to restore application zoom to 100%, or adjust it in Appearance. This is separate from the Windows display scale. Headless previews in `build/previews/desktop-welcome-qhd-125.png` and `desktop-welcome-qhd-150.png` are rendered directly at 2560×1440; they do not replace interactive verification on a monitor.

## Updating this fork

Automatic upstream update checks default to off. The manual updater still points to the original desktop repository; installing an upstream release can replace this fork's changes. Review and rebuild source updates using the pinned commit in `UPSTREAM.md`.

## Playback across devices

The paired Android app must support Connect (Android proposal: https://github.com/alan7383/kittytune/pull/54). Use the player's device button to open the Devices sidebar tab, then click an output to transfer the track, position and queue. The main player controls and timeline operate the selected output. **Play independently** keeps local playback separate while retaining history/library sync.

LAN control works directly. Internet control requires an optional self-hosted relay: https://github.com/VeXEveryOne/kittytune/tree/codex/playback-handoff/relay. Save its public URL in both clients; a hosting PC can use the loopback option. No personal server address is shipped. The PC/relay/tunnel must remain running; the helper does not install automatic startup.

Windows and a real Xiaomi Android 16 device were checked with a 467-track queue over LAN and mobile data. Headless tests passed (136 selected cases); macOS/Linux were not exercised and battery drain was not measured.
