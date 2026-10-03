# Compiles the SMTC bridge (WindowsSmtcBridge.cs) with the .NET Framework 4 csc that ships with
# Windows, so releases carry a bridge built from the source in the tree instead of a checked-in
# binary that slowly rots. Run on windows-latest in GitHub Actions, or locally from a Developer
# Command Prompt.
#
# The WinRT metadata (Windows.winmd) and its .NET projection come from the Windows SDK, whose
# location this script discovers by version, newest first.

param(
    [string]$OutDir = ""
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repoRoot 'src\main\resources\native\WindowsSmtcBridge.cs'
$icon = Join-Path $repoRoot 'src\main\resources\icons\kittytune.ico'
if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'src\main\resources\native' }
$outExe = Join-Path $OutDir 'WindowsSmtcBridge.exe'

foreach ($required in @($source, $icon)) {
    if (-not (Test-Path $required)) { throw "Input not found: $required" }
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# csc from the .NET Framework 4 installation — present on every Windows runner and desktop.
$csc = Join-Path $env:windir 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { $csc = Join-Path $env:windir 'Microsoft.NET\Framework\v4.0.30319\csc.exe' }
if (-not (Test-Path $csc)) { throw "csc.exe not found: $csc" }

# Newest Windows SDK wins; 10.0.19041.0 or anything later has the metadata we reference.
$sdkRoots = @(
    'HKLM:\SOFTWARE\Microsoft\Microsoft SDKs\Windows\v10.0',
    'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Microsoft SDKs\Windows\v10.0'
)
$installedRoot = $null
foreach ($root in $sdkRoots) {
    if (Test-Path $root) {
        $val = (Get-ItemProperty $root -ErrorAction SilentlyContinue).InstallationFolder
        if ($val -and (Test-Path $val)) {
            $installedRoot = $val
            break
        }
    }
}
if (-not $installedRoot) {
    foreach ($cand in @(
        "${env:ProgramFiles(x86)}\Windows Kits\10",
        "$env:ProgramFiles\Windows Kits\10"
    )) {
        if ($cand -and (Test-Path (Join-Path $cand 'Include'))) {
            $installedRoot = $cand
            break
        }
    }
}
if (-not $installedRoot) { throw 'Windows 10 SDK not found (no v10.0 InstallationFolder)' }

$sdkVersion = (Get-ChildItem (Join-Path $installedRoot 'Include') -Directory |
    Where-Object Name -match '^\d+\.\d+\.\d+\.\d+$' |
    Sort-Object { try { [version]$_.Name } catch { [version]'0.0.0.0' } } -Descending |
    Select-Object -First 1).Name
if (-not $sdkVersion) { throw 'Windows SDK include dir not found' }

$winmd = Join-Path $installedRoot "Include\$sdkVersion\um\windows.winmd"
$projection = Join-Path $env:windir 'Microsoft.NET\assembly\GAC_MSIL\System.Runtime.WindowsRuntime\v4.0_4.0.0.0__b77a5c561934e089\System.Runtime.WindowsRuntime.dll'
if (-not (Test-Path $projection)) {
    $projCandidates = Get-ChildItem (Join-Path $env:windir 'Microsoft.NET\assembly\GAC_MSIL\System.Runtime.WindowsRuntime') -Filter 'System.Runtime.WindowsRuntime.dll' -Recurse -ErrorAction SilentlyContinue
    if ($projCandidates) { $projection = $projCandidates[0].FullName }
}
foreach ($required in @($winmd, $projection)) {
    if (-not (Test-Path $required)) { throw "Required reference not found: $required" }
}

Write-Host "Compiling WindowsSmtcBridge.cs (SDK $sdkVersion)"
& $csc /nologo /target:winexe /platform:anycpu32bitpreferred `
    /win32icon:$icon /out:$outExe `
    /r:$winmd /r:$projection /r:System.Runtime.dll $source
if ($LASTEXITCODE -ne 0) { throw "csc failed with exit code $LASTEXITCODE" }
if (-not (Test-Path $outExe)) { throw "csc reported success but $outExe is missing" }

Write-Host "Built $outExe"
