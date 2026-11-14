# Cold HLS opening benchmark

Compares the real `AudioEngine.setMediaItem` on an AWT event thread. The baseline downloads the HLS playlist/init there when no head is cached. The candidate leaves that work for the decoder worker. The localhost fixture serves a valid playlist and sentinel init/fragment bytes; it exercises source setup, not audio decoding.

The three cases are:

- `cached-head`: warm the real `HlsHeadCache` before selection. Both versions should consume it without another request.
- `cold-local`: no cache and no injected server delay.
- `cold-delayed`: no cache, with 250 ms injected at the playlist and another 250 ms at the init response.

The harness records the selection call, an AWT heartbeat queued immediately after it, exact UI-thread CPU/allocations and time through source creation. After selection, it invokes the actual private `createGrabber` on a dedicated worker, adapting to the baseline/candidate signature. Worker ownership fields are initialized as `prepare` would initialize them. The returned grabber is never started and is released immediately. This lets both versions complete the same source work without opening an audio device. Integration regressions separately use actual `prepare` during blocked playlist/init requests and verify cancellation/ownership.

Every iteration checks HTTP request counts: two for cold sources, or three prefetched requests and no additional requests for the cached case. The full source-ready time includes creation and release of the unstarted grabber. It is not time to audible playback. FFmpeg native loading runs before warm-up and measurement, avoiding a first native-library extraction in one timed source setup.

The runner compiles both production `AudioEngine.kt` and `HlsStreamAdapter.kt` versions from source, using the same Kotlin compiler, dependencies and fixture. Five pairs of fresh JVMs alternate baseline/candidate order. Each case has two warm-up and ten measured operations per JVM: 50 observations per version per case. Raw rows and per-fork medians are preserved; observations within a JVM are not independent forks. Timing is not a CI assertion.

Generate a configuration in the candidate checkout:

```
./gradlew -I experiments/hls-opening-benchmark/benchmark.init.gradle writeHlsOpeningBenchmarkConfig -PdesktopOnly=true
```

If the local CDM toolchain is unavailable, source setup does not use the CDM and can prepare this classpath with `-x compileNativeWidevine -x compileCdmNativeHost`. Then run:

```
python experiments/hls-opening-benchmark/run.py --repo . --baseline-ref c4885d6 --config build/hls-opening-benchmark-config.json --output /path/to/results
```

The recorded comparison used Java 25, Kotlin 2.4.21 and identical packaged 1.5.1 dependencies in a manual configuration of the same shape; `nativeDir` may optionally point to packaged native libraries. Each fork has isolated application directories. Keep `samples.csv`, `summary.json`, `environment.json` and `source-provenance.json`. Local argument files contain machine paths and should not be committed.

This measures removal of UI blocking during a cold HLS selection. It does not establish faster servers, less total CPU/RAM, faster audible playback, fewer decoder underruns or the removal of every navigation stutter. It is separate from the old-session cleanup wait addressed by PR #129.
