# Lyrics merge benchmark

Runs the actual `ManualLyricsSearch.merge` on an AWT event thread, with deterministic provider responses. Six batches accumulate 24, 96 or 240 results. Inputs include duplicates, timing tiers, duration differences, alternative titles/artists, Unicode and lyric text.

Each size receives 12 warm-up bursts and 25 measured bursts per JVM. The runner alternates baseline/candidate order across five pairs of fresh JVMs. It checks output checksums and records every measurement, exact thread allocations, thread CPU time, wall time and the longest individual merge in each burst. JVM flags match between variants. Timing is an observation, not a CI assertion.

Prepare a clean baseline checkout at the parent commit and a candidate checkout. Copy the same `src/test/kotlin/LyricsMergeBenchmark.kt` from the candidate into the baseline test source directory. In each checkout, generate its classpath with:

```
./gradlew -I /path/to/candidate/experiments/lyrics-merge-benchmark/benchmark.init.gradle writeLyricsBenchmarkClasspath -PdesktopOnly=true
```

If the local native toolchain is unavailable, these pure matching benchmarks can be built with `-x compileNativeWidevine -x compileCdmNativeHost`; they do not use native audio. Use the same exclusions for both checkouts.

Then run with Java 25:

```
python experiments/lyrics-merge-benchmark/run.py --java-home /path/to/jdk25 --baseline-classpath /path/to/baseline/build/lyrics-benchmark-classpath.txt --candidate-classpath /path/to/candidate/build/lyrics-benchmark-classpath.txt --output /path/to/results
```

Keep the machine otherwise idle. Preserve `samples.csv`, `environment.json`, fork logs and `summary.json`. Compare per-fork medians as well as pooled tails. Baseline and candidate must use the same dependencies and unchanged fixture code.

This measures the synchronous matching/sorting work seen in the UI profile. It does not measure network time, song buffering, native rendering or whole-app frame rate, and does not establish that all navigation stutter has been fixed.
