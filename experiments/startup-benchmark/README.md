# Windows startup and device detection

The baseline is upstream `335c791`. The change replaces the two PowerShell
`Get-PnpDevice` calls in Windows headphone detection with SetupAPI and
Configuration Manager calls through the existing JNA dependency. Bluetooth
enumeration is skipped when no headset endpoint was found.

The OS probe runs during startup and is eligible again after 15 seconds.
It runs on an IO worker, but its helper processes still compete with the UI
for CPU. This experiment measures whether removing them changes the time to
the first drawn, visible main window.

## Reproduce

Build the baseline and candidate application JARs with the same dependencies
and native sources. Keep both files outside the build directory. Supply an
extracted Windows app image with those dependencies and the exact same JDK
build as its runtime. The script makes a disposable copy of the runtime; it
does not install either variant or use your KittyTune profile.

Close games and stop other builds before measuring.

```powershell
python experiments/startup-benchmark/run.py `
  --app-image C:/bench/KittyTune --java-home C:/jdk-25 `
  --baseline-jar C:/bench/baseline.jar --candidate-jar C:/bench/candidate.jar `
  --output C:/bench/startup-results --pairs 10 --probe-pairs 5
```

Python 3.11+ and a JDK with `java.exe` and `javac.exe` are required. Run from
a normal desktop session with access to Windows device enumeration.

The script:

- Uses a fresh JVM for every launch, two excluded warmups, and ten pairs in
  alternating baseline/candidate order. No samples are excluded by timing.
- Creates a synthetic returning guest profile. HTTP requests use a refused
  loopback proxy so service response times and account data do not affect
  the comparison. First-run setup and online feed readiness are not measured.
- Uses identical packaged runtime, dependency JARs, native DSP bytes, JVM
  flags and renderer selection. Both application JARs are repacked with the
  same compressor. An unchanged DSP DLL is taken from the baseline to remove
  rebuild timestamp/debug-ID differences. Input and controlled JAR hashes are
  recorded. The optional application AOT cache is disabled for both variants.
- Requires three low-load CPU samples before each launch. During a launch,
  CPU outside the JVM includes KittyTune's PowerShell helpers and system
  services; it is recorded rather than used to exclude the slower baseline.
- Waits for the existing first-draw marker and a visible Win32 window titled
  `KittyTune` belonging to the launched PID. It also checks that the router
  composed the main screen. Visibility is polled every 25 ms. This measures
  a drawn window, not a blank HWND or an arbitrary sleep.
- Records the endpoint before waiting for the old PowerShell queries to
  finish. It then terminates only that benchmark process tree. This avoids
  leaving a PnP scan running into the next sample.
- Also times the actual Windows detection method in five alternating pairs
  of fresh JVMs. This is component latency, separate from application startup.

Filesystem caches are warm. These are process-cold launches, not reboot/cold
disk measurements. The supplied app image's packaged dependencies are shared
by both variants; this is a source-change comparison, not an installer test.
Raw samples, logs, profiles and launch argument files remain in the output
directory. Only sanitized timing data belongs in the repository.

## Native correctness checks

`DeviceProbe names` reads actual audio endpoint names through the new API;
compare them with `Get-PnpDevice -Class AudioEndpoint -Status OK` on the same
machine. Names stay in local output. `DeviceProbe handles` performs 20 warmups
and 200 additional reads, then checks that process handles did not accumulate.

The unit tests exercise speaker-only output, lazy Bluetooth detection, wired
and Bluetooth AutoEq identity, wireless names, localized names and endpoint
selection. Physical headset hotplug needs separate hardware testing.

API references: [device registry properties](https://learn.microsoft.com/en-us/windows/win32/api/setupapi/nf-setupapi-setupdigetdeviceregistrypropertyw),
[device status](https://learn.microsoft.com/en-us/windows/win32/api/cfgmgr32/nf-cfgmgr32-cm_get_devnode_status),
[releasing a device list](https://learn.microsoft.com/en-us/windows/win32/api/setupapi/nf-setupapi-setupdidestroydeviceinfolist).
