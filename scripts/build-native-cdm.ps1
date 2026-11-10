# Builds the native Linux-CDM host for Windows.
#
#   probe.exe  loader harness: maps the .so, runs its initialisers, calls its entry points
#   ktcdm.exe  the sidecar daemon the app talks to (same protocol, same C ABI as the Linux build)
#
# Run it with:  powershell -ExecutionPolicy Bypass -File scripts\build-native-cdm.ps1
# The build system passes -OutputDirectory <dir> -OnlyHost to produce the executable it packages.
param(
    [string]$OutputDirectory = '',
    [switch]$OnlyHost
)
$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'src\main\cpp\widevinelinux'
$sidecar = Join-Path $root 'src\main\cpp\cdmsidecar\CdmSidecar.cpp'
$output = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $root 'build\widevinelinux' }
$include = Join-Path $root 'src\main\cpp\widevine'
New-Item -ItemType Directory -Force $output | Out-Null

$flags = @(
    '-std=c++17', '-O1', '-g', '-mfsgsbase', '-fno-exceptions', '-fno-rtti',
    '-Wall', '-Wextra', '-Wno-unused-parameter', '-Wno-missing-field-initializers',
    '-Wno-unused-function', '-Wno-cast-function-type', "-I$include"
)
# MinGW GCC 13-16 crashes in choose_baseaddr while emitting the prologue of a function that calls a
# sysv_abi function (GCC PR target/97822), which is what cdm_bridge.c does. -O2 and an explicit frame
# pointer are the workarounds the GCC developers give in that report; the file is not hot enough to care.
$cdmBridgeFlags = @('-O2', '-fno-omit-frame-pointer')
$sources = @('winelf.c', 'glibc_shim.c', 'shim_printf.c', 'sysv_support.S', 'probe_main.c', 'cdm_bridge.c')

$objects = @()
$failed = $false
foreach ($name in $sources) {
    Write-Host "== $name"
    $object = Join-Path $output "$name.o"
    $extra = if ($name -eq 'cdm_bridge.c') { $cdmBridgeFlags } else { @() }
    & g++ @flags @extra -c (Join-Path $source $name) -o $object 2>&1 | Select-Object -First 30
    if ($LASTEXITCODE -ne 0) { $failed = $true } else { $objects += $object }
}
if ($failed) { Write-Host 'COMPILE FAILED'; exit 1 }

if (-not $OnlyHost) {
Write-Host '== link probe.exe'
& g++ @objects -o (Join-Path $output 'probe.exe') -lbcrypt -lcrypt32 -static 2>&1 | Select-Object -First 20
if ($LASTEXITCODE -ne 0) { Write-Host 'LINK FAILED (probe)'; exit 1 }
}

Write-Host '== compile CdmSidecar.cpp'
$sidecarObject = Join-Path $output 'CdmSidecar.cpp.o'
& g++ @flags -c $sidecar -o $sidecarObject 2>&1 | Select-Object -First 20
if ($LASTEXITCODE -ne 0) { Write-Host 'COMPILE FAILED (sidecar)'; exit 1 }

# 32 MiB of stack: the Linux code subtracts large frames and writes far below rsp, which Windows does
# not grow the stack for.
$hostObjects = $objects | Where-Object { $_ -notmatch 'probe_main' }
Write-Host '== link ktcdm.exe'
& g++ @hostObjects $sidecarObject -o (Join-Path $output 'ktcdm.exe') -lbcrypt -lcrypt32 -static `
    '-Wl,--stack,33554432' 2>&1 | Select-Object -First 20
if ($LASTEXITCODE -ne 0) { Write-Host 'LINK FAILED (ktcdm)'; exit 1 }

Write-Host ''
if (-not $OnlyHost) { Write-Host "built: $(Join-Path $output 'probe.exe')" }
Write-Host "built: $(Join-Path $output 'ktcdm.exe')"
Write-Host ''
Write-Host 'try:  ktcdm.exe <path>\libwidevinecdm.so --probe'
