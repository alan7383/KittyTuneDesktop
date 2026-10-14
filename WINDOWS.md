# Windows portable build

Extract `KittyTune-<version>-Windows-Portable-x64.zip` into a writable folder and
open `KittyTune/KittyTune.exe`. Keep `app` and `runtime` beside the executable.
The Java runtime is included; no installer or system Java is required.

The portable package uses the normal KittyTune profile in `%APPDATA%\KittyTune`
and cache in `%LOCALAPPDATA%\KittyTuneCache`. It does not relocate settings into
the extracted folder. Exit an older running version from its tray menu before
starting another version, because single-instance handling can reopen the old
process.

## Build

Use Windows x64, JDK 21+ with `jpackage`, a Windows SDK, and either Visual Studio
C++ Build Tools or MinGW `g++` on PATH. The media bridge also uses the .NET
Framework C# compiler included with Windows.

```powershell
.\scripts\build-windows.ps1 -JavaHome 'C:\path\to\jdk-21'
```

This compiles the current DSP and media-bridge sources, runs `headlessTest`,
packages the runtime, and writes a SHA-256 checksum beside the ZIP in `release/`.
It does not install or launch the player. `-SkipTests` skips the regression run.
`JAVA_HOME` is restored when the script finishes.

The equivalent Gradle tasks, with `JAVA_HOME` pointing to a JDK 21+, are:

```powershell
.\gradlew.bat headlessTest zipWindowsPortable -PdesktopOnly=true --no-daemon --max-workers=2
```

`-PdesktopOnly=true` leaves the independent website out of this build; the
normal project configuration still includes it. Optional Compose compiler
diagnostics can be enabled with `-PcomposeReports=true`.

The Windows app image is in
`build/compose/binaries/<version>/main-release/app/KittyTune/`, so packaging a new
version does not overwrite another version's app image. Native build outputs
are in `build/generated/native-resources/`; the source tree is not overwritten.
`packageReleaseMsi` remains available for installer builds with the tools
required by `jpackage`.

## Rendering and checks

App zoom scales controls and text once in both the main window and mini-player,
while preserving accessibility font scaling. Windows text uses pixel-aligned
glyph positioning on Compose's grayscale surfaces. System display scaling is
retained; use Ctrl+0 to restore app zoom to 100%.

`headlessTest` runs a bounded selection of deterministic regressions with a
profile isolated under `build/test-profile/`, without windows or sound output.
It exercises the freshly compiled JNI library with in-memory PCM and renders
the Russian welcome screen at 2560 x 1440 with 125% and 150% display density.
Reports are in `build/reports/tests/headlessTest/` and previews in
`build/previews/`. The Windows CI workflow uploads both alongside the package.
Offscreen rendering does not replace interactive monitor and audio checks.
