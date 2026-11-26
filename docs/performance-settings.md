# Performance settings

Settings → Performance controls rendering and cache budgets without changing the player layout, artwork quality or audio effects. Changes take effect after restarting KittyTune. The initial settings retain the app's existing defaults.

**Presets** offers Resource saver for smaller caches, Balanced for KittyTune's defaults, and Smooth browsing for larger caches and fewer artwork reloads. Each preset selects automatic graphics and enables vertical sync. Use **Graphics** and **Cache limits** to adjust the settings individually.

- Renderer options depend on the operating system and architecture. Automatic uses Skiko's usual fallback order. Software compatibility mode renders on the CPU and may be slower.
- On Windows, choose Automatic or select a graphics card by name for Direct3D. KittyTune loads the DXGI device list in the background and checks the saved PCI and name identifiers again at startup. The choice requests Skiko's adapter priority order, subject to driver and renderer compatibility. KittyTune uses automatic selection when the saved card is unavailable or ambiguous.
- The graphics page shows the renderer's reported device when available. It warns if the renderer uses a different device than the Windows card requested at startup or falls back to the CPU.
- Use Windows graphics settings to select a card for OpenGL or choose a card the in-app menu cannot select. Add the KittyTune.exe you run and choose the desired graphics preference.
- Metal on macOS offers a power-saving or high-performance preference. The system and driver control device selection on Linux. A dedicated GPU may increase battery use.
- Vertical synchronization: enabled by default. Turning it off may introduce tearing or increase power use; it is not a universal speed improvement.
- Artwork RAM cache: 16–128 MiB, default 32 MiB.
- Graphics resource cache: 32–256 MiB, default 64 MiB.
- Artwork disk cache: 128–1024 MiB, default 256 MiB. Downloads and library files are unaffected.

Cache values are upper limits, not reserved memory, and do not cap the entire application's RAM or VRAM. Smaller caches can require more decoding or network fetches. Restore defaults clears the performance preferences. All eight existing language catalogs include the new page's strings.

Saved graphics options are applied before Skiko loads. The image loader uses the startup snapshot, so adjusting the next-launch preference does not silently rebuild caches during playback. Invalid or unsupported imported values fall back to safe defaults. The implementation adds no background polling, native libraries or dependencies. No speedup has been claimed or benchmarked for these controls.

If a renderer fails at startup, launch KittyTune with the environment variable `SKIKO_RENDER_API=SOFTWARE_COMPAT` and choose **Restore performance defaults** in **Settings → Performance → Presets**.
