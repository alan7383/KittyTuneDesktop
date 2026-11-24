# Performance settings

Settings → Performance controls rendering and cache budgets without changing the player layout, artwork quality or audio effects. Changes take effect after restarting KittyTune. The initial settings retain the app's existing defaults.

- Renderer options depend on the operating system and architecture. Automatic uses Skiko's usual fallback order. Software compatibility mode renders on the CPU and may be slower.
- Preferred GPU: Automatic, integrated or dedicated. The renderer supports this preference through Direct3D on Windows and Metal on macOS. It is disabled for explicit OpenGL/software and on Linux, where the OS/driver controls adapter selection. A dedicated GPU may increase battery use; the preference is not a guarantee if the renderer falls back or Windows overrides it.
- Windows graphics preferences: opens the system's Graphics page. Add the actual KittyTune.exe and choose High performance to request the dedicated GPU, including for OpenGL on hybrid laptops. No system setting is changed automatically.
- Vertical synchronization: enabled by default. Turning it off may introduce tearing or increase power use; it is not a universal speed improvement.
- Artwork RAM cache: 16–128 MiB, default 32 MiB.
- Graphics resource cache: 32–256 MiB, default 64 MiB.
- Artwork disk cache: 128–1024 MiB, default 256 MiB. Downloads and library files are unaffected.

Cache values are upper limits, not reserved memory, and do not cap the entire application's RAM or VRAM. Smaller caches can require more decoding or network fetches. Restore defaults clears only these six application preferences. All eight existing language catalogs include the new page's strings.

Saved graphics options are applied before Skiko loads. The image loader uses the startup snapshot, so adjusting the next-launch preference does not silently rebuild caches during playback. Invalid or unsupported imported values fall back to safe defaults. The implementation adds no background polling, native libraries or dependencies. No speedup has been claimed or benchmarked for these controls.

If a renderer fails at startup, launch KittyTune with the environment variable `SKIKO_RENDER_API=SOFTWARE_COMPAT` and restore the performance defaults in Settings.
