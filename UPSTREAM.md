# Desktop integration and upstream review

Reviewed on **6 October 2026**.

## Desktop source

The desktop application is imported from [alan7383/KittyTuneDesktop](https://github.com/alan7383/KittyTuneDesktop), pinned to commit [`7c624edfd710d44332cb14032c5759fa70393199`](https://github.com/alan7383/KittyTuneDesktop/commit/7c624edfd710d44332cb14032c5759fa70393199), version **1.4.1**. This directory is an independent Gradle build; Android's Gradle modules and dependencies remain separate.

The original Kotlin/Compose implementation retains Material 3 Expressive, MaterialKolor, Google Sans Flex, the `#FF7A1A` default seed, rounded components, the full player and settings. The layout provides a resizable desktop sidebar, queue panel, bottom player, keyboard shortcuts, tray and Windows media controls. The upstream README and individual source notices are retained; upstream describes its desktop code as MIT-licensed, whereas the enclosing Android repository is GPL-3.0.

Local integration changes:

- Windows native audio can build with either MinGW or Visual Studio C++ Build Tools. MSVC uses a static runtime. Generated native outputs live under `build/` and have Gradle inputs/outputs for incremental rebuilds.
- The Windows media bridge is rebuilt from its C# source by `scripts/build-windows.ps1`.
- Fixed an additional native lifecycle leak found during Windows testing: the normalization processor had an `onReset` hook, but the desktop base class never exposed or called it when an audio engine was released. `AudioEngine.release()` now frees its JNI analyser under the existing native lock; a native PCM regression verifies processing and idempotent release without playing sound.
- `zipWindowsPortable` packages the application and its Java runtime. `headlessTest` runs an explicit regression set in an isolated profile without windows or audio output.
- Build cache is enabled, Gradle is limited to two workers and a 2 GB heap, and the independent website is excluded from desktop configuration.
- Unconditional raw Compose metrics arguments were removed after reproducing a Windows compiler failure (`Invalid file path`). Diagnostic reports are opt-in through the Compose compiler Gradle DSL.
- Launching the app no longer invokes Zapret auto-configuration. Its settings remain available for explicit user actions.
- Automatic upstream update checks default to off for this local build. Manual upstream updates may replace these local changes.

The imported desktop already contains bounded artwork/palette caching, theme preference deduplication, a shared network client, single-instance handling, G1 pause controls, and a 64 MB Skia resource-cache limit. These are upstream implementations, not newly written optimizations here. No performance percentage is claimed without profiling.

## Android upstream review

Local base: [`aa14cf83`](https://github.com/alan7383/kittytune/commit/aa14cf83). Upstream main: [`a6e6edde`](https://github.com/alan7383/kittytune/commit/a6e6edde). **282 commits** are newer than the local base. A wholesale merge would also restore removed integrations and replace substantially modified playback code.

| Upstream change | Finding and action |
| --- | --- |
| [`819fffa3`](https://github.com/alan7383/kittytune/commit/819fffa3): desktop fixes ported to Android | Confirmed the short-link response was not closed and a fresh client was built for every request. Adapted it to a shared, bounded-timeout client with `Response.use`. Moved bottom-bar progress reads into the progress lambda so playback ticks invalidate drawing rather than the surrounding composition. |
| [`fb19fc7e`](https://github.com/alan7383/kittytune/commit/fb19fc7e): artwork/cache/IPC optimizations | The local fork already uses the shared Coil loader and 128 × 128 bitmap requests in `PlayerViewModel`, plus a palette cache. Upstream thumbnail URLs could be ported separately; its provider-specific changes should not be copied blindly into the simplified fork. |
| [`1e01ed54`](https://github.com/alan7383/kittytune/commit/1e01ed54): player/UI synchronization | Local player code already checks the expected queue track and has separate playback-persistence changes. Requires a focused playback audit before transplanting upstream's larger view-model patch. |
| [`3adda30a`](https://github.com/alan7383/kittytune/commit/3adda30a): lazy crossfade player | The local `MusicManager` now builds a single ExoPlayer. Upstream's second-player optimization is not directly applicable. |
| [`ceda9576`](https://github.com/alan7383/kittytune/commit/ceda9576): MiniPlayer snapshotFlow | Useful for the separate legacy mini-player. The active unified bottom bar received the more direct draw-phase state-read fix above. |
| [`0de70277`](https://github.com/alan7383/kittytune/commit/0de70277): Eco Mode | Adds low-quality streams, smaller artwork, and per-provider controls. This is a feature with settings/UI dependencies, not a self-contained performance fix. Deferred. |
| [`401b023b`](https://github.com/alan7383/kittytune/commit/401b023b): faster builds | Enabled Gradle build cache in the Android build. Did not copy upstream's 6 GB heap or broad parallelism into a single-module Android build. |
| [`044bd93b`](https://github.com/alan7383/kittytune/commit/044bd93b): mobile/desktop LAN sync | Available in the imported desktop. The current Android fork predates the corresponding LAN sync; desktop integration alone does not add phone-to-PC synchronization. |

No upstream branch was merged into the existing Android working tree. Existing local removals, playback changes and tests are preserved.

## Validation

The local 1.4.2 update corrects squared text zoom (the UI scale was applied to both density and fontScale) in the main window and mini-player. It also aligns static Windows glyphs to physical pixels on Compose's grayscale surfaces. The launcher was already Per-Monitor V2 aware; inspection of the running 1.4.1 process confirmed 120 DPI (125%) rather than Windows bitmap virtualization. The update does not change system resolution, display scale, or the running old process. Versioned packaging keeps both builds separate.

- Windows x64 portable package built locally from source with JDK 21 and MSVC.
- Android `:app:compileDebugKotlin` and `:app:testDebugUnitTest` both passed after the short-link and bottom-bar changes.
- **118 headless tests passed**, including queue, synchronization merge, signed stream URLs, search settings, window bounds, PCM limiting, native JNI processing/release, zoom at 100–200% monitor DPI, and Russian first-launch rendering at 2560×1440 with 125% and 150% display scaling.
- The desktop and Android `google_sans_flex.ttf` files have identical SHA-256 hashes.
- The Windows DSP DLL depends only on `KERNEL32.dll`; a separate MinGW or Visual C++ runtime is not required for that library.
- Verified that the portable ZIP contains the launcher, application JARs and bundled JVM, and that its main JAR includes the rebuilt Windows media bridge and audio DLL.
- The player window, system-wide media controls and live online audio were not exercised interactively. All checks were run without Computer Use or sound output.
