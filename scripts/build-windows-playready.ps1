param([Parameter(Mandatory = $true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$source = Join-Path $projectRoot 'src/main/cpp/playready/DesktopProtectedPlayer.cpp'
$exe = Join-Path $outputPath 'KittyTuneProtectedPlayer.exe'
$sdkRoots = @(
    "${env:ProgramFiles(x86)}/Windows Kits/10/Include",
    (Join-Path $projectRoot 'build/sdk-cpp/c/Include')
)
$cppWinRt = $null
foreach ($sdkRoot in $sdkRoots) {
    if (-not (Test-Path -LiteralPath $sdkRoot)) { continue }
    foreach ($version in (Get-ChildItem -LiteralPath $sdkRoot -Directory | Sort-Object Name -Descending)) {
        $candidate = Join-Path $version.FullName 'cppwinrt'
        if (Test-Path -LiteralPath (Join-Path $candidate 'winrt/base.h')) { $cppWinRt = $candidate; break }
    }
    if ($cppWinRt) { break }
}
if (-not $cppWinRt) { throw 'Install a Windows 10/11 SDK containing C++/WinRT headers to build the PlayReady companion.' }
$compiler = Get-Command g++.exe -ErrorAction SilentlyContinue
if (-not $compiler) { throw 'Install MinGW-w64 g++ with C++20 support.' }
& $compiler.Source -std=c++20 -O2 -static -municode "-I$cppWinRt" $source -o $exe -lmfplat -lmf -lmfuuid -lpropsys -lole32 -loleaut32 -lwinhttp -luuid -lruntimeobject -lshlwapi -lmfreadwrite -lwindowsapp
if ($LASTEXITCODE -ne 0) { throw 'PlayReady renderer compilation failed.' }
# GUI subsystem avoids a console window. This is a normal desktop executable, not AppContainer.
$stream = [IO.File]::Open($exe, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite)
try {
    $reader = New-Object IO.BinaryReader($stream)
    $writer = New-Object IO.BinaryWriter($stream)
    $stream.Position = 0x3c
    $peOffset = $reader.ReadInt32()
    $stream.Position = $peOffset + 24 + 70
    $dllFlags = $reader.ReadUInt16()
    $stream.Position = $peOffset + 24 + 68
    $writer.Write([uint16]2)
    $writer.Write([uint16]($dllFlags -band 0xefff))
    $writer.Flush()
} finally { $stream.Dispose() }
Write-Output 'Built unpackaged native PlayReady renderer. No package or certificate was installed.'
