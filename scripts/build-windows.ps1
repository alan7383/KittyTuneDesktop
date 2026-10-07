param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\jpackage.exe'))) {
    throw 'Pass a JDK 21+ directory with -JavaHome, or set JAVA_HOME. A JRE is not sufficient.'
}
$previousJavaHome = $env:JAVA_HOME
$env:JAVA_HOME = $JavaHome
Push-Location -LiteralPath $projectDirectory
try {
    & "$PSScriptRoot\build-smtc-bridge.ps1"
    $buildTasks = @('zipWindowsPortable')
    if (-not $SkipTests) { $buildTasks = @('headlessTest') + $buildTasks }
    & .\gradlew.bat @buildTasks --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Desktop build failed with code $LASTEXITCODE" }
    $archives = Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'release') -Filter '*-Windows-Portable-x64.zip'
    foreach ($archive in $archives) {
        $checksum = (Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        Set-Content -LiteralPath ($archive.FullName + '.sha256') -Value "$checksum  $($archive.Name)" -Encoding ascii
    }
    Write-Output 'Built portable KittyTune. Extract the archive and open KittyTune\KittyTune.exe. Nothing was installed or launched.'
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
}
