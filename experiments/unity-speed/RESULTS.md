# Neutral speed/pitch path

At normal speed and pitch, `TimeStretcher` previously converted every short sample to float, wrote a float ring, then converted back into a short ring. It also allocated the float rings and sinc table before they were needed. The direct path copies shorts when both existing unity predicates hold and pending input is empty. Pending data and mode-switch behavior retain the original path. Float rings, ramp and sinc table are initialized only when needed, with local references inside sample loops to avoid lazy-getter overhead. Output-ring copies handle wrap and growth in bulk.

No new native dependency or UI change. Kotlin remains appropriate here: eliminating redundant conversions and using JVM array-copy intrinsics removes the work directly.

## Measurements

Windows, Zulu JDK 25, Kotlin 2.4.21; baseline `3a7185a`. The actual original and candidate `TimeStretcher` sources were compiled with identical settings and dependency classpaths. Three alternating pairs of fresh JVMs, fixed `-Xms256m -Xmx512m`, five measured samples per mode per JVM: 15 samples per variant/mode. All samples are retained in `samples.json`.

Unity sample: 16,384 stereo blocks of 1,024 frames at 44.1 kHz, about 380 seconds of generated PCM, excluding three warmup iterations. Pitch/stretch samples use 64 blocks, excluding 12 warmup iterations. Pitch uses speed=pitch=1.25; stretch uses speed=1.25, pitch=1. The benchmark verifies identical sample counts and sampled output checksums between variants. Separate regression tests hash every output sample.

| Median | Original | Candidate |
| --- | ---: | ---: |
| Neutral queue/drain wall time | 442.096 ms | 4.348 ms |
| Pitch queue/drain wall time | 8.360 ms | 7.724 ms |
| Stretch queue/drain wall time | 55.129 ms | 43.673 ms |
| Constructor + first neutral block + flush allocations, per instance | 396,536 B | 65,984 B |

Neutral wall time is 99.0% lower; first-block allocations are 83.4% lower (about 323 KiB saved per instance). The allocation experiment warms up three batches of 32 instances, then measures five batches of 64, retaining the last instance through a volatile sink. This measures JVM allocations per instance, not whole-app RAM.

| Process pair | Neutral original | Neutral candidate |
| --- | ---: | ---: |
| 1 | 437.945 ms | 5.056 ms |
| 2 | 382.456 ms | 3.991 ms |
| 3 | 467.016 ms | 4.348 ms |

Pitch/stretch show process-to-process variation; no general speedup is claimed for them. Windows process CPU sampling is too coarse for the optimized neutral workload (median 0 ms versus 453.125 ms original); zero does not mean no CPU use. Steady-state allocations are unchanged: 24 B per unity/pitch sample and 10,672 B per stretch sample, including the benchmark's boxed checksum. No whole-player CPU percentage, startup time, download-size reduction or Linux performance percentage is claimed. Decoding, other effects, audio output and UI rendering are outside this benchmark.

## Verification

Ten original-output golden hashes cover unity, mono, pitch, stretch, mode changes, flush, ring growth, wrap, near-unity parameters and partial stereo frames. Two tests verify that neutral playback and flush leave stretch workspaces unallocated and that ring growth preserves unread samples across a wrap. The tests run in Windows headless CI and Linux x64 and arm64 CI.

## Reproduce

Copy this experiment directory into a separate baseline checkout too. In both checkouts run:

```
./gradlew -I experiments/unity-speed/benchmark.init.gradle writeTimeStretcherBenchmarkClasspath -PdesktopOnly=true
```

Then use the generated absolute classpaths and a fresh output directory:

```
python experiments/unity-speed/run.py --java /jdk-25/bin/java --baseline-classpath /baseline/build/time-stretcher-benchmark-classpath.txt --candidate-classpath /candidate/build/time-stretcher-benchmark-classpath.txt --output /new/benchmark-directory
```

On Windows use `gradlew.bat` and `java.exe`. The benchmark uses synthetic PCM and never initializes an account profile, opens an audio device or sends network requests.
