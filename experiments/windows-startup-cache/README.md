# Packaged Windows startup cache

This measures the same packaged KittyTune executable with Java AOT disabled
and enabled. No application code, UI assets, renderer preferences or features
are changed between variants. This is separate from PR #132's native device
probe change.

Use a disposable image. The benchmark temporarily rewrites its launcher
configuration, saves `original.cfg` in the output directory, and restores the
configuration on normal completion or a Python exception. If interrupted by
force, restore that file manually. Do not run this against a live installation.

```powershell
python experiments/windows-startup-cache/copy_image.py C:/KittyTune C:/bench/KittyTune
./scripts/optimize-windows-startup.ps1 -AppImage C:/bench/KittyTune -JavaHome C:/jdk-25
python experiments/windows-startup-cache/benchmark.py --app-image C:/bench/KittyTune --output C:/bench/results --pairs 10 --disposable-image
```

The JDK must match the packaged runtime. Preparing the cache is a one-time
operation and can take several minutes; allow up to ten minutes per JVM.
The archive is about 100 MiB and must be prepared again after changing or
re-extracting the JARs. The normal launcher uses automatic fallback when an
archive is absent or incompatible. The benchmark deliberately uses strict
AOT mode for the candidate so an unused archive cannot appear as a success.

## Protocol

- Two excluded warmups, then ten alternating baseline/candidate pairs.
  Every measurement is a fresh process; filesystem caches are warm.
- Both variants use the same executable path, runtime, JARs and settings.
  The original installation and account profile are not used or modified.
- A synthetic returning guest profile uses a refused loopback HTTP proxy.
  Setup and online feed readiness are not measured.
- The endpoint requires the first-draw marker from the actual main screen
  and a visible Win32 window titled KittyTune in the launcher's process tree.
  Visibility is polled every 25 ms; a blank or hidden window does not count.
- The packaged launcher can start a child JVM. Window ownership and PowerShell
  draining therefore cover the entire process tree, not just the launcher.
- Three quiet CPU samples precede each launch. After recording the endpoint,
  existing device-probe helpers are allowed to finish before terminating that
  benchmark tree. No timing samples are discarded.
- Raw samples and executable/JAR/archive hashes are saved in the output
  directory. These tests do not estimate CPU savings or cold-disk startup.

Record the median, observed range, every paired sample and artifact hashes in
the PR description. Do not extrapolate a result on one GPU to all hardware.

## Helper tests

```powershell
python scripts/tests/test_startup_cache.py
```

The tests compile a small .NET Framework JVM fixture on Windows and exercise
long arguments, Unicode paths, locked cleanup files, preservation of an
existing cache on assembly failure, and partial timeout diagnostics. They do
not substitute for preparing and opening the actual application with a cache.
