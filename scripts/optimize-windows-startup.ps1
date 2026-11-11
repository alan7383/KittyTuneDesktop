#Requires -Version 7.2
param(
    [Parameter(Mandatory)][string]$AppImage,
    [Parameter(Mandatory)][string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$image = (Resolve-Path -LiteralPath $AppImage).Path
$app = Join-Path $image 'app'
$cfg = Join-Path $app 'KittyTune.cfg'
$java = Join-Path $image 'runtime/bin/java.exe'
$cache = Join-Path $app 'kittytune-startup.aot'
$cacheOption = 'java-options=-XX:AOTCache=$APPDIR/kittytune-startup.aot'
$lines = @(Get-Content -LiteralPath $cfg | Where-Object { $_ -ne $cacheOption })
$options = @($lines | Where-Object { $_.StartsWith('java-options=') } | ForEach-Object {
    $_.Substring('java-options='.Length).Replace('$APPDIR', $app)
})
$classpath = @($lines | Where-Object { $_.StartsWith('app.classpath=') } | ForEach-Object {
    $_.Substring('app.classpath='.Length).Replace('$APPDIR', $app)
})
$mainClass = 'app.mainclass=com.alananasss.kittytune.MainKt'
if ($mainClass -notin $lines -or '[JavaOptions]' -notin $lines -or !$classpath.Count) {
    throw "Unexpected KittyTune launcher configuration in $cfg"
}
foreach ($jar in $classpath) {
    if (!(Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing app JAR: $jar" }
}

# Record after extraction: HotSpot checks the actual timestamp of every JAR in the image.
$work = Join-Path $app "startup-training-$([Guid]::NewGuid().ToString('N'))"
$profile = Join-Path $work 'roaming/KittyTune'

function Invoke-TrainingJvm([string[]]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new($java)
    $start.WorkingDirectory = $image
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.CreateNoWindow = $true
    foreach ($option in $Arguments) { $start.ArgumentList.Add($option) }
    foreach ($name in 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'KITTYTUNE_STARTUP_LOG') {
        [void]$start.Environment.Remove($name)
    }
    $start.Environment['APPDATA'] = "$work/roaming"
    $start.Environment['LOCALAPPDATA'] = "$work/local"
    $start.Environment['TEMP'] = $start.Environment['TMP'] = "$work/temp"
    $start.Environment['ProgramData'] = $start.Environment['OneDrive'] = $start.Environment['ALLUSERSPROFILE'] = "$work/home"
    $process = [Diagnostics.Process]::Start($start)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit(120000)) {
            $process.Kill($true)
            $process.WaitForExit()
            throw 'Java startup cache preparation timed out.'
        }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "Java startup cache preparation failed.`n$output" }
        return $output
    } finally {
        $process.Dispose()
    }
}

$copiedJava = $false
try {
    New-Item -ItemType Directory -Path $profile, "$work/local", "$work/temp", "$work/home" | Out-Null
    '{"has_completed_setup":true,"protected_tracks_announced":true}' | Set-Content "$profile/prefs.json"
    '{"is_guest_mode":true}' | Set-Content "$profile/soundtune_auth.json"
    '{"announced":true}' | Set-Content "$profile/kitty_pet.json"

    if (!(Test-Path -LiteralPath $java)) {
        Copy-Item -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -Destination $java
        $copiedJava = $true
    }
    $settings = $options + @("-Duser.home=$work/home", "-Djava.io.tmpdir=$work/temp")
    $launch = @('-cp', ($classpath -join ';'), 'com.alananasss.kittytune.MainKt')
    $configuration = Join-Path $work 'recording.aotconfig'
    $candidate = Join-Path $work 'kittytune-startup.aot'
    $output = Invoke-TrainingJvm ($settings + @(
        '-XX:AOTMode=record', "-XX:AOTConfiguration=$configuration", '-Dkittytune.trainStartupCache=true'
    ) + $launch)
    if (!$output.Contains('KittyTune startup cache training finished')) {
        throw "KittyTune did not finish drawing the training window.`n$output"
    }
    $null = Invoke-TrainingJvm ($settings + @(
        '-XX:AOTMode=create', "-XX:AOTConfiguration=$configuration", "-XX:AOTCache=$candidate"
    ) + $launch)
    $null = Invoke-TrainingJvm ($settings + @(
        '-XX:AOTMode=on', "-XX:AOTCache=$candidate", '-cp', ($classpath -join ';'), '-version'
    ))

    $newConfig = Join-Path $work 'KittyTune.cfg'
    $lines | ForEach-Object { $_; if ($_ -eq '[JavaOptions]') { $cacheOption } } |
        Set-Content -LiteralPath $newConfig -Encoding utf8
    [IO.File]::Move($candidate, $cache, $true)
    [IO.File]::Move($newConfig, $cfg, $true)
    Write-Output "Startup cache prepared for $image"
} finally {
    if ($copiedJava) { Remove-Item -LiteralPath $java }
    # This directory was generated directly under the resolved app image.
    $resolved = (Resolve-Path -LiteralPath $work -ErrorAction SilentlyContinue).Path
    if ($resolved -and [IO.Path]::GetDirectoryName($resolved) -eq $app) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
