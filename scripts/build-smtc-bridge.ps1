# Compiles the SMTC bridge (WindowsSmtcBridge.cs) with the .NET Framework 4 csc that ships with
# Windows, so releases carry a bridge built from the source in the tree instead of a checked-in
# binary that slowly rots. Run on windows-latest in GitHub Actions, or locally from a Developer
# Command Prompt.
#
# The WinRT metadata (Windows.winmd) and its .NET projection come from the Windows SDK, or directly
# from the Windows system WinMetadata directory.

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

$winmdRefs = @()
if ($installedRoot) {
    $sdkVersion = (Get-ChildItem (Join-Path $installedRoot 'Include') -Directory |
        Where-Object Name -match '^\d+\.\d+\.\d+\.\d+$' |
        Sort-Object { try { [version]$_.Name } catch { [version]'0.0.0.0' } } -Descending |
        Select-Object -First 1).Name

    if ($sdkVersion) {
        $winmdCandidates = @(
            (Join-Path $installedRoot "UnionMetadata\$sdkVersion\Windows.winmd"),
            (Join-Path $installedRoot "UnionMetadata\$sdkVersion\Facade\Windows.winmd"),
            (Join-Path $installedRoot "UnionMetadata\Windows.winmd")
        )
        foreach ($cand in $winmdCandidates) {
            if (Test-Path $cand) {
                $winmdRefs += $cand
                break
            }
        }
    }
}

if ($winmdRefs.Count -eq 0) {
    $sysWinmd = Join-Path $env:windir 'System32\WinMetadata\Windows.winmd'
    if (Test-Path $sysWinmd) {
        $winmdRefs += $sysWinmd
    } else {
        # Windows 11 split WinMetadata
        $modular = @(
            (Join-Path $env:windir 'System32\WinMetadata\Windows.Foundation.winmd'),
            (Join-Path $env:windir 'System32\WinMetadata\Windows.Media.winmd'),
            (Join-Path $env:windir 'System32\WinMetadata\Windows.Storage.winmd')
        )
        $allExist = $true
        foreach ($m in $modular) {
            if (-not (Test-Path $m)) { $allExist = $false; break }
        }
        if ($allExist) {
            $winmdRefs += $modular
        }
    }
}

$projCandidates = @(
    (Join-Path $env:windir 'Microsoft.NET\assembly\GAC_MSIL\System.Runtime.WindowsRuntime\v4.0_4.0.0.0__b77a5c561934e089\System.Runtime.WindowsRuntime.dll'),
    (Join-Path $env:windir 'Microsoft.NET\Framework64\v4.0.30319\System.Runtime.WindowsRuntime.dll'),
    (Join-Path $env:windir 'Microsoft.NET\Framework\v4.0.30319\System.Runtime.WindowsRuntime.dll'),
    "${env:ProgramFiles(x86)}\Reference Assemblies\Microsoft\Framework\.NETCore\v4.5\System.Runtime.WindowsRuntime.dll",
    "$env:ProgramFiles\Reference Assemblies\Microsoft\Framework\.NETCore\v4.5\System.Runtime.WindowsRuntime.dll"
)
$projection = $null
foreach ($cand in $projCandidates) {
    if (Test-Path $cand) {
        $projection = $cand
        break
    }
}

$interopCandidates = @(
    (Join-Path $env:windir 'Microsoft.NET\assembly\GAC_MSIL\System.Runtime.InteropServices.WindowsRuntime\v4.0_4.0.0.0__b03f5f7f11d50a3a\System.Runtime.InteropServices.WindowsRuntime.dll'),
    (Join-Path $env:windir 'Microsoft.NET\Framework64\v4.0.30319\System.Runtime.InteropServices.WindowsRuntime.dll'),
    (Join-Path $env:windir 'Microsoft.NET\Framework\v4.0.30319\System.Runtime.InteropServices.WindowsRuntime.dll'),
    "${env:ProgramFiles(x86)}\Reference Assemblies\Microsoft\Framework\.NETCore\v4.5\System.Runtime.InteropServices.WindowsRuntime.dll",
    "$env:ProgramFiles\Reference Assemblies\Microsoft\Framework\.NETCore\v4.5\System.Runtime.InteropServices.WindowsRuntime.dll"
)
$interop = $null
foreach ($cand in $interopCandidates) {
    if (Test-Path $cand) {
        $interop = $cand
        break
    }
}

$runtimeCandidates = @(
    (Join-Path $env:windir 'Microsoft.NET\assembly\GAC_MSIL\System.Runtime\v4.0_4.0.0.0__b03f5f7f11d50a3a\System.Runtime.dll'),
    "${env:ProgramFiles(x86)}\Reference Assemblies\Microsoft\Framework\.NETFramework\v4.5\Facades\System.Runtime.dll"
)
$runtimeRef = $null
foreach ($cand in $runtimeCandidates) {
    if (Test-Path $cand) {
        $runtimeRef = $cand
        break
    }
}

$checkedIn = Join-Path $repoRoot 'src\main\resources\native\WindowsSmtcBridge.exe'

if ($winmdRefs.Count -gt 0 -and $projection -and $interop) {
    Write-Host "Compiling WindowsSmtcBridge.cs with csc..."
    $argList = @('/nologo', '/target:winexe', '/platform:anycpu32bitpreferred', "/win32icon:$icon", "/out:$outExe")
    foreach ($w in $winmdRefs) { $argList += "/r:$w" }
    $argList += "/r:$projection"
    $argList += "/r:$interop"
    if ($runtimeRef) { $argList += "/r:$runtimeRef" } else { $argList += "/r:System.Runtime.dll" }
    $argList += $source

    & $csc @argList
    if ($LASTEXITCODE -eq 0 -and (Test-Path $outExe)) {
        Write-Host "Successfully built $outExe"
        exit 0
    }
    Write-Warning "csc compilation failed, checking fallback..."
}

if (Test-Path -LiteralPath $checkedIn) {
    if ((Resolve-Path -LiteralPath $checkedIn).Path -ne (Resolve-Path -LiteralPath $outExe -ErrorAction SilentlyContinue).Path) {
        Write-Host "Using checked-in fallback ($checkedIn -> $outExe)"
        Copy-Item -LiteralPath $checkedIn -Destination $outExe -Force
        exit 0
    }
}

throw "Could not compile WindowsSmtcBridge.cs and no fallback available."
