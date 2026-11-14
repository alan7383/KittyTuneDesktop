# Windows results

Baseline: upstream `c4885d6`. Candidate: the retirement change in this PR. Both production source files were compiled with Kotlin 2.4.21, JVM target 21, using identical packaged 1.5.1 dependencies. Java 25.0.5, Windows 10 build 19045, Intel family 6 model 94. Native DSP was loaded in both versions. No decoder/audio device was opened.

Five pairs of fresh JVMs, alternating order; three warm-up and twelve measured iterations per case per JVM. Each entry below aggregates 60 observations; the CSV contains all 360 observations. The desktop session and JVM memory/GC settings were held the same. The machine was not a dedicated benchmark host.

| Previous stream | Median track-change call, before / after | p95 call, before / after | Median queued AWT heartbeat, before / after |
| --- | --- | --- | --- |
| Open session, immediate close | 0.980 / 1.184 ms | 1.648 / 1.751 ms | 1.173 / 1.368 ms |
| Licence acquisition held 500 ms | 501.064 / 1.125 ms | 502.374 / 1.995 ms | 501.335 / 1.339 ms |
| Session close held 500 ms | 501.119 / 1.108 ms | 502.187 / 1.761 ms | 501.384 / 1.340 ms |

The median uncontended cost increases by about 0.20 ms and UI-thread allocation by 264 bytes (4,768 to 5,032). This change is intended to remove blocking waits, not reduce CPU/allocation. CPU counters in this short Windows test are too coarse to support a CPU improvement claim. Moving close to a worker also moves some of its allocation there; the UI allocation counter is not a total-process allocation measurement.

The five individual fork medians for the pending-licence call are 501.151, 500.934, 501.227, 500.969 and 500.969 ms before; 1.148, 1.104, 1.507, 1.103 and 1.121 ms after. The improvement appears in every pair. Every iteration waits for eventual session close, including when retirement overlaps acquisition.

The 500 ms wait is injected and is still paid by the old stream in the candidate. This demonstrates removal of UI blocking, not a 500-fold faster player. In the real Windows JFR that prompted the fix, AWT spent 495.514 ms waiting for the adapter monitor in `releaseDrm`, called by `AudioEngine.setMediaItem`; the prior owner was the audio worker. The licence provider was being used on that worker around the same interval. The recording itself contains user playback data and is kept local.

No painting, frame rate, native CDM or new-track buffering is exercised here. Playlist/init requests and the existing decoder-worker join can still delay a track change; general navigation lag needs separate profiling.
