# Scroll edge benchmark

Runs the actual `FadingEdge.kt` helpers in a native 1280 × 720 `ComposeWindow`. A 16 ms Swing timer changes content read during drawing, forcing redraws without introducing a separate composition benchmark. The two cases are one vertical list with both fades active, and a vertical list containing six horizontally faded rows. Rectangles stand in for row content; this is a rendering fixture, not a complete application page.

Both versions of the production file are compiled from source with the same Kotlin 2.4.21 compiler, Compose compiler, fixture, dependencies and JVM flags. Five pairs of fresh JVMs alternate baseline/candidate order. Each case warms up for 3 seconds, then measures for 6 seconds. The runner checks that all forks use the same renderer and that rendering actually continued during each phase.

The AWT event queue records exact UI-thread allocations and CPU time. Results are normalized by real Skiko render callbacks, so a slower run cannot appear cheaper just by drawing fewer frames. Dispatch p95 includes the native rendering event work; frame-recording duration measures the delegate callback, excluding subsequent presentation. Render callbacks are not proof that every frame reached the display. Windows CPU counters can be quantized to 15.625 ms, and system load can still affect latency.

Generate a configuration from a candidate checkout:

```
./gradlew -I experiments/scroll-edge-benchmark/benchmark.init.gradle writeScrollEdgeBenchmarkConfig -PdesktopOnly=true
```

If the native CDM toolchain is missing, this UI-only benchmark can use `-x compileNativeWidevine -x compileCdmNativeHost` when preparing the classpath. It does not play audio or use a CDM. The local recorded run used identical packaged 1.5.1 dependencies, Java 25, and the same Kotlin/Compose 2.4.21 compiler in a manual configuration of the same shape. `nativeDir` can optionally point to a packaged Skiko DLL directory.

Then run with a real desktop display:

```
python experiments/scroll-edge-benchmark/run.py --repo . --config build/scroll-edge-benchmark-config.json --output /path/to/results
```

Keep the desktop otherwise idle; do not build the app during measurement. Each JVM opens and closes its own temporary benchmark window. Application directories are isolated. The output includes per-fork measurements, source hashes, JVM/environment details and raw per-event/frame traces. Do not commit local argument files or profile directories. The checked-in fork aggregates preserve the independent observations; full traces are kept locally and can be regenerated with the runner.

The image regressions compare lossless PNG output against the original renderer in the same process, including tiny/fractional fade lengths, both axes/directions, alpha animation and resizing. These use offscreen Skia rendering. The zero-length test failed before the fix because an unnecessary offscreen layer clipped content even though there was no mask to draw.

This does not measure whole-app memory, GPU memory, audio stutter, network delays or cold page opening. Any CPU or latency claim must be supported separately from allocation savings and checked across individual paired forks.
