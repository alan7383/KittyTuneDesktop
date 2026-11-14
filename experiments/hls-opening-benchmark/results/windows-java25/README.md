# Windows source-setup results

Baseline: upstream `c4885d6`, version 1.5.1. Both production files were compiled from source with Kotlin 2.4.21, JVM target 21, identical packaged 1.5.1 dependencies and fixture code. Java 25.0.5, Windows 10 build 19045, Intel family 6 model 94. Source hashes and environment details are included.

Five alternating pairs of fresh JVMs. Two warm-up and ten measured operations per case per JVM: 50 observations per version per case, 300 raw rows total. Every iteration checks request counts. The delayed case injects 250 ms at each of two responses. FFmpeg native loading is completed before measurements.

| Case | Median UI selection, before / after | p95 UI selection, before / after | Median queued AWT heartbeat, before / after | Median complete source setup, before / after |
| --- | --- | --- | --- | --- |
| Prefetched head | 1.034 / 0.548 ms | 1.355 / 1.055 ms | 1.227 / 0.739 ms | 1.526 / 1.381 ms |
| Cold, no injected delay | 4.257 / 0.453 ms | 5.449 / 0.789 ms | 4.427 / 0.629 ms | 4.688 / 4.777 ms |
| Cold, 500 ms total server delay | 506.135 / 0.479 ms | 507.767 / 1.145 ms | 506.365 / 0.703 ms | 506.782 / 507.094 ms |

The delayed UI call's individual fork medians are 506.028, 506.601, 505.989, 506.385, 505.912 ms before; 0.480, 0.508, 0.463, 0.479, 0.496 ms after. The UI wait is removed in every pair, while source setup still pays the same network delay. The cached control remains fast.

The zero-delay cold case's full-setup p95 is 5.895 ms before and 6.345 ms after, including worker handoff/creation/release. This is not a claim of faster total source preparation. UI-thread allocation/CPU move with the work; they do not establish lower total process memory or CPU. Windows CPU counters are coarse for these short UI calls.

The benchmark invokes the real private source factory on a dedicated worker after selection, with ownership initialized as `prepare` would initialize it. It creates/releases an unstarted grabber without decoding or playing audio. Integration tests separately use actual `prepare` with blocked HTTP and verify skip, release and same-URL ownership. Other tests cover prefetch consumption, headers, token propagation and seek reuse.

These results concern UI responsiveness under controlled HLS setup delays, not audible startup, live AI inference, sustained audio playback or complete-page navigation. An existing decoder's separate join wait and the DRM cleanup wait in PR #129 are outside this comparison.
