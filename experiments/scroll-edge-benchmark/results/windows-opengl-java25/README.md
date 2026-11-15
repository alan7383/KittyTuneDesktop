# Windows OpenGL comparison

Baseline: upstream `c4885d6`. Candidate: cached, bounded edge masks in this PR. Both versions of the production file and the same fixture were compiled with Kotlin/Compose compiler 2.4.21, JVM target 21, identical packaged 1.5.1 runtime dependencies. Native windows reported `OPENGL` in every phase. Java 25.0.5, Windows 10 build 19045, Intel family 6 model 94. Source hashes and environment details are included. The machine was a normal desktop, not an isolated benchmark host.

Five alternating pairs of fresh JVMs. Each JVM runs both cases, with 3 s warm-up and 6 s measured time per case. Each table entry is the median of five independent fork observations; frames within a fork are not treated as independent trials.

| Metric | Single list, before / after | Six carousels, before / after |
| --- | --- | --- |
| UI allocated bytes per render callback | 6,070 / 3,338 | 20,210 / 3,791 |
| AWT dispatch p95 | 3.677 / 2.766 ms | 5.514 / 3.826 ms |
| Frame-recording median | 0.486 / 0.400 ms | 0.435 / 0.340 ms |
| Render callbacks per phase | 355 / 356 | 354 / 356 |
| UI CPU per callback | 1.843 / 1.532 ms | 2.325 / 2.057 ms |

UI allocations drop about 45% and 81%, consistently across all paired forks. Dispatch p95 improves by about 25% and 31%, also in every pair. The five six-carousel dispatch p95 observations are 6.302, 5.514, 5.966, 5.233 and 5.245 ms before; 3.928, 3.890, 3.826, 3.611 and 3.719 ms after.

CPU is less conclusive: it regresses in two of five paired forks in each case despite lower aggregate medians. Do not use the aggregate CPU difference to claim a reliable whole-app CPU saving. Render callback counts remain comparable, so the allocation reduction does not come from freezing the animation or dropping most frames. These callbacks do not measure presented display FPS.

The event queue instrumentation adds the same measurement overhead to both versions. Raw per-event/frame traces are retained in the local `work/scroll-edge-results/trace-*.json` files; the runner reproduces them. `forks.csv` contains all twenty case/fork observations, including both cases from every fork, not a selected subset.

Pixel regressions compare the original and candidate with offscreen Skia in the same process, including animated alpha, size changes and fractional mask boundaries. The boundary extends one opaque pixel beyond the fade to preserve antialiasing. No fixed golden PNG from another OS is used.

This is a rendering fixture with simple row content and active fades. It excludes networking, images, text shaping, audio, full navigation and cold page opening. The reduction is in allocated UI-thread bytes, not resident memory or GPU memory. It does not prove that every lag source in KittyTune is fixed.
