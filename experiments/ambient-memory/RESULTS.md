# Bounded ambient playback

RainPlayer previously decoded each bundled MP3 to a persistent WAV, then opened a Clip holding its entire decoded loop. It now keeps the compressed resource in memory and decodes into an 8 KiB scratch buffer feeding a SourceDataLine with a requested 500 ms buffer. The same line stays open across loops. Existing generated rain/ocean/fireplace/cafe WAV files are removed after the first decoded PCM becomes ready; downloaded tracks and other cache files are untouched.

## Memory measurements

Windows, Zulu JDK 25, JavaCV 1.5.10/FFmpeg 6.1.1, default Java Sound output device. Three alternating pairs of fresh JVMs, identical heap/GC options. Both alternatives are warmed first. Each process records before/open/closed phases after two explicit collections, then takes five process-memory readings 150 ms apart per phase. Baseline silently opens the original generated rain WAV in a real Clip. Candidate silently opens a real SourceDataLine and the new decoder and reads its first PCM block. Neither starts audio. This isolates retained resources from audible playback, UI and the main player.

| Median increase from warm baseline | Clip | Streaming resources |
| --- | ---: | ---: |
| Live Java heap | 18.03 MiB | 1.04 MiB |
| Process private bytes | 18.83 MiB | 2.64 MiB |
| Resident working set | 17.09 MiB | 0.25 MiB |

| Pair | Clip live heap | Streaming live heap | Clip private bytes | Streaming private bytes |
| --- | ---: | ---: | ---: | ---: |
| 1 | 18.034 MiB | 1.037 MiB | 17.742 MiB | 4.156 MiB |
| 2 | 18.033 MiB | 1.035 MiB | 18.832 MiB | 0.730 MiB |
| 3 | 18.034 MiB | 1.035 MiB | 20.133 MiB | 2.637 MiB |

The component's live heap is about 17 MiB smaller; median private-byte increase is about 16.2 MiB smaller. Process values vary, so raw phases/readings are retained in `samples.json`. Heap returns near baseline after closure in both cases. These are not whole-app idle/playing-memory figures; when ambience is disabled, the old implementation already closes its Clip. Linux memory gains have not been measured locally.

Streaming trades a little continuous decoding for the retained full PCM loop. Eighteen measured full-loop decodes consumed a median 132.8 ms of the calling thread's CPU for 104.36 seconds of rain audio, approximately 0.13% of one CPU core averaged over that duration. This is a decoder measurement, not total playback CPU or a real-time scheduling guarantee.

## Storage

The four old WAVs contain 54,570,960 bytes of PCM plus headers, about 52.0 MiB in total. No replacement decoded files are created. A user who cached only rain reclaims about 17.56 MiB after successfully opening the new decoder. The bundled MP3 assets and package/download sizes are unchanged.

## Checks and limits

13 focused tests passed: all four assets compared byte-for-byte (size and SHA-256) against the original MP3-to-WAV-to-AudioSystem pipeline on the same machine, bounded first-block allocation, repeated loops/partial writes on one line, cancellation during blocked writes or device opening, closing before start, gain updates, settings/device restart ownership and scoped WAV cleanup. The first-block allocation regression failed with eager whole-loop decoding before the bounded source was implemented. No claim is made that that prototype's allocation count equals the old Clip's count.

Seekability matters: a nonseekable compressed stream retained extra MP3 padding at EOF in the initial probe. Keeping only the compressed bytes seekable produced the exact original PCM for every asset. Cross-platform tests generate the reference with the local codec rather than imposing a Windows-specific hash.

A focused review found no substantive lifecycle/resource issues. Hardware playback continuity and device switching still need a listening check; silent device opens and simulated line behavior do not replace it. Full project packaging is delegated to CI. The new tests are included in Windows headless and Linux x64/ARM64 jobs. No UI, main-player effects, new native dependency or per-distro playback implementation was added.

## Reproduce

Use a JDK 25 and an available default Java Sound output device. The test opens audio resources silently. Python requires psutil (`python -m pip install psutil`) for process-memory readings; it is not an app dependency.

```
./gradlew -I experiments/ambient-memory/benchmark.init.gradle writeAmbientMemoryClasspath -PdesktopOnly=true
python experiments/ambient-memory/run.py --java /jdk-25/bin/java --classpath build/ambient-memory-classpath.txt --output /new/benchmark-directory
```

On Windows use `gradlew.bat` and `java.exe`. The runner refuses an existing output directory, creates only a synthetic reference WAV from the bundled resource, and does not read account/library settings. Windows private-byte readings are recorded when available; other hosts still record RSS and JVM heap.
