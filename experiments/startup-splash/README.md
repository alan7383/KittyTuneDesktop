# Startup splash benchmark

Windows only. Requires Python with `psutil` and a JDK25 matching the packaged runtime. Build the unchanged base and candidate with identical Gradle/runtime options. Run headless tests separately, before measurements.

```text
python prepare.py --baseline ORIGINAL_IMAGE --candidate CANDIDATE_IMAGE --output COMPARISON_DIRECTORY
python benchmark.py --baseline COMPARISON_DIRECTORY/normal-comparison-image --candidate COMPARISON_DIRECTORY/splash-comparison-image --jdk JDK25_DIRECTORY --output NEW_RUN_DIRECTORY --runs 8
python report.py NEW_RUN_DIRECTORY
```

`--smoke` checks one warmup of each variant without measured pairs. Output directories must not exist. Keep them outside the repository. Preparation uses hardlinks for unchanged files, replaces each comparison main JAR with an identically repacked copy, and equalizes native resources. Do not edit linked runtime files.

The benchmark compiles its own common isolation/renderer agents from the image's existing ASM JAR. Both arms disable global media shortcuts/Discord via isolated preferences and suppress SMTC initialization/Zapret configuration writes through the same agent. No test audio is played. The installed app/profile are not used. A renamed launcher and isolated shortcut roots avoid changing protocol registration or desktop shortcuts.

Endpoints, publication gate and limitations are in PROTOCOL.md. Per-run startup logs, process resource samples, prelaunch CPU, actual renderer metadata, image hashes and results are retained. Renderer attachment happens after measurements. Source code inspection or the requested renderer flag alone is not a renderer validation.

Wait until the suite and other build/benchmark jobs have stopped. Measurements use below-normal priority and a prelaunch CPU gate to allow ongoing listening, but cannot prove the absence of all audible interference or overhead on other machines. No physical audio-quality check is performed.
