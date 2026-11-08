# Provisions the optional CDM sidecar through WSL and points KittyTune at it.
#
#   .\scripts\setup-cdm-sidecar.ps1                 # first WSL distribution
#   .\scripts\setup-cdm-sidecar.ps1 -Distro Ubuntu  # a specific one
#   .\scripts\setup-cdm-sidecar.ps1 -NoInstall      # skip the apt step (already provisioned)
#
# The sidecar is optional: it is how a protected track gets decoded into the app so the equalizer,
# the filters and the pitch shifter work on it. Without it, protected tracks play through PlayReady
# and the app behaves exactly as before.
#
# What it does, in order:
#   1. checks that WSL has a distribution (it does not install one; `wsl --install` does)
#   2. installs build-essential, curl and dpkg in that distribution, as root, non-interactively
#   3. runs scripts/setup-cdm-sidecar.sh there: fetches Chrome's Linux CDM if none is present,
#      compiles the daemon, probes it
#   4. writes %LOCALAPPDATA%\KittyTune\cdm-sidecar.json, which the player reads at startup
#
# Safe to re-run: every step is idempotent, and a failure leaves the previous configuration alone.
[CmdletBinding()]
param(
    [string]$Distro,
    [string]$CdmPath,
    [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$env:WSL_UTF8 = '1'

function Fail([string]$message) {
    Write-Host "setup-cdm-sidecar: $message" -ForegroundColor Red
    exit 1
}

# A native command that writes anything to stderr becomes a terminating error under
# $ErrorActionPreference = 'Stop' — and `wsl.exe` writes to stderr for its own progress lines, so a
# perfectly successful build aborts the script before its exit code is read. Every WSL call goes
# through here, with the policy relaxed for the duration of the call only.
function Invoke-Wsl([string[]]$WslArguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        return (& wsl.exe @WslArguments 2>&1 | ForEach-Object { "$_" })
    } finally {
        $ErrorActionPreference = $previous
    }
}

# `wsl.exe -- wslpath -a C:\Users\... ` arrives as `C:Users...`: the launcher re-parses the command
# line and eats the separators. Building the /mnt path here avoids the round trip entirely, and a
# forward-slash path goes through untouched.
function ConvertTo-WslPath([string]$windowsPath) {
    if ($windowsPath -match '^([A-Za-z]):(.*)$') {
        $drive = $Matches[1].ToLowerInvariant()
        $rest = $Matches[2] -replace '\\', '/'
        return "/mnt/$drive$rest"
    }
    return $null
}

$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $root 'src/main/cpp/cdmsidecar/CdmSidecar.cpp'))) {
    Fail "run this from the KittyTune checkout (looked in $root)"
}
if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    Fail 'wsl.exe is not present; install WSL 2 with: wsl --install'
}

# ---------------------------------------------------------------------------------------------
# 1. Which distribution
# ---------------------------------------------------------------------------------------------

# `wsl.exe -l -q` answers UTF-16LE unless WSL_UTF8 is set, and PowerShell 5.1 has no `uFEFF escape
# (it would read `[`0`uFEFF]` as "strip the letters u, F and E", which is how "Ubuntu" became "bnt").
$distros = @(Invoke-Wsl @('-l', '-q') |
    ForEach-Object { ("$_".Replace([string][char]0, '')).Trim().Trim([char]0xFEFF).Trim() } |
    Where-Object { $_ })
if (-not $distros -or $distros.Count -eq 0) {
    Fail 'WSL is installed but has no distribution; run: wsl --install -d Ubuntu'
}
if (-not $Distro) { $Distro = $distros[0] }
if ($distros -notcontains $Distro) {
    Fail "no WSL distribution named '$Distro' (found: $($distros -join ', '))"
}
Write-Host "distribution: $Distro"
# A name that parses but does not launch would fail much later, inside the build, with a worse message.
Invoke-Wsl @('-d', $Distro, '--', 'true') | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "WSL cannot start '$Distro'" }

# ---------------------------------------------------------------------------------------------
# 2. Build tools
# ---------------------------------------------------------------------------------------------

if (-not $NoInstall) {
    Write-Host 'installing build tools (build-essential, curl, dpkg, ca-certificates)'
    Invoke-Wsl @('-d', $Distro, '-u', 'root', '--', 'bash', '-lc', 'export DEBIAN_FRONTEND=noninteractive; apt-get update -qq && apt-get install -y -qq build-essential curl ca-certificates dpkg >/dev/null') | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'could not install the build tools in that distribution' }
}

# ---------------------------------------------------------------------------------------------
# 3. The sidecar itself
# ---------------------------------------------------------------------------------------------

$linuxRoot = ConvertTo-WslPath $root
if (-not $linuxRoot) { Fail "the checkout must live on a mounted drive (got $root)" }
# A forward-slash path survives wsl.exe's own command-line parsing; a backslash one does not, which
# is why this is computed rather than asked for with `wslpath -a C:\...`.
Invoke-Wsl @('-d', $Distro, '--', 'test', '-d', $linuxRoot) | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "WSL cannot see $linuxRoot (checked $linuxRoot)" }
$arguments = @('-d', $Distro, '--', 'bash', "$linuxRoot/scripts/setup-cdm-sidecar.sh")
if ($CdmPath) {
    $linuxCdm = ConvertTo-WslPath $CdmPath
    if (-not $linuxCdm) { Fail "the CDM path must live on a mounted drive (got $CdmPath)" }
    $arguments += @('--cdm', $linuxCdm)
}

$output = Invoke-Wsl $arguments
$exit = $LASTEXITCODE
$output | ForEach-Object { Write-Host "  $_" }
if ($exit -ne 0) { Fail "the sidecar build failed (exit $exit)" }

$json = $output | Where-Object { $_ -match '^\s*\{.*\}\s*$' } | Select-Object -Last 1
if (-not $json) { Fail 'the build reported success but printed no paths' }
$parsed = $json | ConvertFrom-Json
if (-not $parsed.binary -or -not $parsed.cdm) { Fail 'the build reported incomplete paths' }

# ---------------------------------------------------------------------------------------------
# 4. Point the player at it
# ---------------------------------------------------------------------------------------------

$configuration = [ordered]@{
    command = @('wsl.exe', '-d', $Distro, '--', $parsed.binary, $parsed.cdm)
    label   = "WSL $Distro"
    written = (Get-Date).ToString('s')
}
$directory = Join-Path $env:LOCALAPPDATA 'KittyTune'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$file = Join-Path $directory 'cdm-sidecar.json'
$temporary = "$file.tmp"
$configuration | ConvertTo-Json -Depth 4 | Set-Content -Path $temporary -Encoding utf8
Move-Item -Force $temporary $file

Write-Host ''
Write-Host "sidecar ready: $($parsed.binary)" -ForegroundColor Green
Write-Host "CDM:           $($parsed.cdm)"
Write-Host "config:        $file"
Write-Host ''
Write-Host 'Restart KittyTune. Protected tracks now decode in-process: equalizer, filters,'
Write-Host 'pitch and speed work. Without this file, they keep playing through PlayReady.'
Write-Host ''
Write-Host 'To uninstall the option: delete that config file. Nothing else changes.'
