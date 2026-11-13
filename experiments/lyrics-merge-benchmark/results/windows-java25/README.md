# Windows measurements

Windows 10, Intel family 6 model 94, Java 25.0.5, Kotlin 2.4.21. Five fresh JVMs per variant; 12 warm-up bursts and 25 measured bursts per size in each JVM. Order alternates between pairs. A burst merges six provider responses. Timings below sum those six synchronous merges; they are not a single song-change or navigation duration.

| Results | Burst median, before / after | Longest merge p95, before / after | Allocated per burst, before / after |
| --- | --- | --- | --- |
| 24 | 37.34 / 13.56 ms | 23.86 / 9.17 ms | 26.28 / 3.29 MB |
| 96 | 241.93 / 21.75 ms | 101.07 / 24.86 ms | 153.83 / 12.13 MB |
| 240 | 734.16 / 46.60 ms | 422.11 / 34.33 ms | 466.16 / 28.80 MB |

Raw samples, JVM flags, per-fork medians and source hashes are included alongside this report.

The original implementation revisited alternative titles 1,950 times for 80 distinct results in the work-bound regression. The fixed implementation stays within three visits per candidate. All output checksums agree, and randomized parity tests also compare full result objects and order.

Wall times varied considerably between forks in this normal desktop session; KittyTune was also left open. Use the raw per-fork figures rather than treating pooled timings as a precise whole-app speedup. The allocation reduction is measured by ThreadMXBean on the executing event thread, not sampled JFR weights. Windows thread CPU values are visibly quantized, so small CPU differences should not be overinterpreted.

The 16.67 ms column in raw samples is only a reference budget: this harness does not render a screen or measure presentation deadlines. Native GPU rendering, network requests, playback startup and general navigation remain outside this comparison. Larger merges can still exceed a frame budget after the fix.
