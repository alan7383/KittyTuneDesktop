param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$nativeSource = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\cpp'
$outPath = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outPath | Out-Null
$outDll = Join-Path $outPath 'libkittytune_audio_dsp.dll'
$compiler = Get-Command g++.exe -ErrorAction SilentlyContinue
if ($compiler) {
    & $compiler.Source -shared -O3 -static-libgcc -static-libstdc++ `
        "-I$JavaHome/include" "-I$JavaHome/include/win32" "-I$nativeSource/ebur128/queue" `
        "$nativeSource/KittyTuneAudioDSP.cpp" "$nativeSource/ebur128/ebur128.c" -o $outDll
    if ($LASTEXITCODE -ne 0) { throw "Native audio compiler failed: $LASTEXITCODE" }
    exit 0
}

# Use the installed MSVC toolchain when MinGW is unavailable. No global PATH changes.
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere)) { throw 'Install Visual Studio C++ Build Tools or put MinGW g++ on PATH.' }
$visualStudio = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $visualStudio) { throw 'Visual Studio C++ Build Tools not found.' }
$vcvars = Join-Path $visualStudio 'VC\Auxiliary\Build\vcvars64.bat'
$environmentLines = & $env:ComSpec /d /c "call `"$vcvars`" >nul && set"
if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize the C++ compiler environment.' }
foreach ($line in $environmentLines) {
    if ($line -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
Push-Location -LiteralPath $outPath
try {
    & cl.exe /nologo /c /O2 /MT /TC /D_CRT_SECURE_NO_WARNINGS /D_USE_MATH_DEFINES "/I$nativeSource/ebur128/queue" `
        "$nativeSource/ebur128/ebur128.c" /Foebur128.obj
    if ($LASTEXITCODE -ne 0) { throw 'Failed to compile the loudness library.' }
    & cl.exe /nologo /c /O2 /MT /EHsc /std:c++17 /D_CRT_SECURE_NO_WARNINGS /D_USE_MATH_DEFINES `
        "/I$JavaHome/include" "/I$JavaHome/include/win32" "/I$nativeSource/ebur128/queue" `
        "$nativeSource/KittyTuneAudioDSP.cpp" /FoKittyTuneAudioDSP.obj
    if ($LASTEXITCODE -ne 0) { throw 'Failed to compile the audio DSP.' }
    & link.exe /nologo /DLL /OUT:libkittytune_audio_dsp.dll KittyTuneAudioDSP.obj ebur128.obj
    if ($LASTEXITCODE -ne 0) { throw 'Failed to link the audio DSP.' }
} finally { Pop-Location }
if (-not (Test-Path -LiteralPath $outDll)) { throw 'Native audio DLL was not produced.' }
