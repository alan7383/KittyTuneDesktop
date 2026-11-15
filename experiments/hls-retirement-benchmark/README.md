# HLS retirement benchmark

Runs the actual `AudioEngine.setMediaItem` on an AWT event thread. A previous protected HLS adapter is attached to the engine through reflection; its real input stream reads the same CENC test fixture used by the DRM tests. Only the external CDM/licence provider is a stand-in. The benchmark never prepares a decoder or opens an audio device.

Three cases run in each fresh JVM:

- `idle`: the old session is already open and its close returns immediately.
- `pending-license`: the old reader is inside the adapter's session acquisition, holding its monitor. The fixture holds the provider for 500 ms after the track change is submitted.
- `closing-session`: acquisition has finished, but session close waits for the same controlled 500 ms gate.

The fixture waits for the retired session to close before starting another iteration. Two scenarios deliberately inject a known wait: they measure whether it blocks the UI, not faster licence/network processing. The unchanged implementation also has no real decoder worker in this harness, so its separate worker-join wait is excluded.

The runner compiles both versions of `AudioEngine.kt` and `HlsStreamAdapter.kt` from source with the same Kotlin compiler, dependency classpath, fixture and JVM settings. Five pairs of fresh JVMs alternate baseline/candidate order. Each case has three warm-up and twelve measured iterations per fork, for 60 measured operations per version per case. It records per-operation wall time, delay of an AWT heartbeat queued behind it, exact UI-thread allocations and thread CPU time. Timing is not a CI assertion. CPU samples on Windows may be quantized to 15.625 ms, so do not interpret zero CPU samples as zero work.

To generate a configuration from a built candidate checkout:

```
./gradlew -I experiments/hls-retirement-benchmark/benchmark.init.gradle writeHlsRetirementBenchmarkConfig -PdesktopOnly=true
```

Then, with Python 3 and the same idle desktop session throughout:

```
python experiments/hls-retirement-benchmark/run.py --repo . --baseline-ref c4885d6 --config build/hls-retirement-benchmark-config.json --output /path/to/results
```

The results included here used the packaged 1.5.1 dependency jars, the installed native audio library and a local compiler configuration with Kotlin 2.4.21 and Java 25. Both changed production files were compiled for both versions, overriding the dependency jar's copies. `source-provenance.json` records those source hashes and the dependency main-jar hash. The runner accepts the same JSON configuration shape emitted by the Gradle task; `nativeDir` can additionally point to a directory containing native libraries when using packaged dependencies.

Application directories are isolated for each fork. Keep raw `samples.csv`, `summary.json`, `environment.json` and `source-provenance.json`. Local argument files and profile directories contain machine paths and should not be committed. Review individual fork medians as well as pooled medians/tails.

This measures UI responsiveness during retirement of the old adapter. It does not measure painting, navigation FPS, time to hear the new song, or the playlist/init HTTP requests still performed by `setMediaItem`. It does not establish that all track changes or all navigation stutter are fixed.
