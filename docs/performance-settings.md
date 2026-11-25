# Performance settings

Windows graphics choices now use the detected adapter names. DXGI lists cards and their OS preference order through the existing JNA dependency; this does not run a helper process or poll hardware. Selection stores a stable PCI/name identity, then resolves it again at startup. Missing, duplicate or unaddressable cards are not silently replaced with another named card. Only choices representable by the renderer's existing adapter preference are selectable; other cards require Windows graphics settings. This is a preference request, not arbitrary adapter-ID binding.

The graphics page reads the renderer's actual device name separately and warns if the requested card is not used, including CPU fallback. OpenGL adapter routing still belongs to Windows/the driver, so that selection remains in Windows settings. No driver compatibility checks or renderer fallback are disabled. GPU inventory is loaded off the UI thread, only when needed. No migration or native renderer replacement is included.

The new regression cases cover mapping, duplicate/missing devices, persistence, reset, real Windows DXGI enumeration and screenshots at 100%, 125% and 150% display scales. Screenshot fixtures do not verify physical GPU routing. Check results are pending for this follow-up.

Settings → Performance controls rendering and cache budgets without changing the player layout, artwork quality or audio effects. Changes take effect after restarting KittyTune. The initial settings retain the app's existing defaults.

**Presets** offers Resource saver for smaller caches, Balanced for KittyTune's defaults, and Smooth browsing for larger caches and fewer artwork reloads. Each preset selects automatic graphics and enables vertical sync. Use **Graphics** and **Cache limits** to adjust the settings individually.

- Renderer options depend on the operating system and architecture. Automatic uses Skiko's usual fallback order. Software compatibility mode renders on the CPU and may be slower.
- Preferred GPU: Automatic, integrated or dedicated. The renderer supports this preference through Direct3D on Windows and Metal on macOS. It is disabled for explicit OpenGL/software and on Linux, where the OS/driver controls adapter selection. A dedicated GPU may increase battery use; the preference is not a guarantee if the renderer falls back or Windows overrides it.
- Windows graphics preferences: opens the system's Graphics page. Add the actual KittyTune.exe and choose High performance to request the dedicated GPU, including for OpenGL on hybrid laptops. No system setting is changed automatically.
- Vertical synchronization: enabled by default. Turning it off may introduce tearing or increase power use; it is not a universal speed improvement.
- Artwork RAM cache: 16–128 MiB, default 32 MiB.
- Graphics resource cache: 32–256 MiB, default 64 MiB.
- Artwork disk cache: 128–1024 MiB, default 256 MiB. Downloads and library files are unaffected.

Cache values are upper limits, not reserved memory, and do not cap the entire application's RAM or VRAM. Smaller caches can require more decoding or network fetches. Restore defaults clears only these six application preferences. All eight existing language catalogs include the new page's strings.

Saved graphics options are applied before Skiko loads. The image loader uses the startup snapshot, so adjusting the next-launch preference does not silently rebuild caches during playback. Invalid or unsupported imported values fall back to safe defaults. The implementation adds no background polling, native libraries or dependencies. No speedup has been claimed or benchmarked for these controls.

If a renderer fails at startup, launch KittyTune with the environment variable `SKIKO_RENDER_API=SOFTWARE_COMPAT` and choose **Restore performance defaults** in **Settings → Performance → Presets**.
