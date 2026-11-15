#Requires -Version 7.2
param(
    [Parameter(Mandatory)][string]$AppImage,
    [Parameter(Mandatory)][string]$JavaHome,
    [ValidateRange(1, 3600)][int]$TrainingTimeoutSeconds = 600
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

function Invoke-TrainingJvm([string[]]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new($java)
    $start.WorkingDirectory = $image
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.CreateNoWindow = $true
    # A packaged classpath can exceed Windows' command-line limit.
    $argumentFile = Join-Path $work ("jvm-" + [Guid]::NewGuid().ToString('N') + '.args')
    $quoted = foreach ($option in $Arguments) {
        '"' + $option.Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n').Replace("`t", '\t') + '"'
    }
    [IO.File]::WriteAllText($argumentFile, ($quoted -join "`n"))
    # The Windows Java launcher can lose Unicode characters in the @file path.
    $start.ArgumentList.Add('@' + [IO.Path]::GetRelativePath($image, $argumentFile))
    foreach ($name in 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'KITTYTUNE_STARTUP_LOG') {
        [void]$start.Environment.Remove($name)
    }
    $start.Environment['APPDATA'] = "$scratch/roaming"
    $start.Environment['LOCALAPPDATA'] = "$scratch/local"
    $start.Environment['TEMP'] = $start.Environment['TMP'] = "$scratch/temp"
    $start.Environment['ProgramData'] = $start.Environment['OneDrive'] = $start.Environment['ALLUSERSPROFILE'] = "$scratch/home"
    $process = [Diagnostics.Process]::Start($start)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TrainingTimeoutSeconds * 1000)) {
            $process.Kill($true)
            $process.WaitForExit()
            $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
            throw "Java startup cache preparation timed out.`n$output"
        }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "Java startup cache preparation failed.`n$output" }
        return $output
    } finally {
        $process.Dispose()
    }
}

$copiedJava = $false
$scratch = $null
try {
    # Native libraries extracted under user.home and java.io.tmpdir need short paths.
    # Keep the archive staging directory beside the final cache for same-volume moves.
    $scratch = (New-Item -ItemType Directory -Path ([IO.Path]::GetTempPath()) -Name ("kt-" + [IO.Path]::GetRandomFileName())).FullName
    $profile = Join-Path $scratch 'roaming/KittyTune'
    New-Item -ItemType Directory -Path "$work", $profile, "$scratch/local", "$scratch/temp", "$scratch/home" | Out-Null
    '{"has_completed_setup":true,"protected_tracks_announced":true}' | Set-Content "$profile/prefs.json"
    '{"is_guest_mode":true}' | Set-Content "$profile/soundtune_auth.json"
    '{"announced":true}' | Set-Content "$profile/kitty_pet.json"

    if (!(Test-Path -LiteralPath $java)) {
        Copy-Item -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -Destination $java
        $copiedJava = $true
    }
    $settings = $options + @("-Duser.home=$scratch/home", "-Djava.io.tmpdir=$scratch/temp")
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
    if ($copiedJava) {
        try { Remove-Item -LiteralPath $java } catch { Write-Warning "Could not remove temporary Java launcher: $($_.Exception.Message)" }
    }
    # This directory was generated directly under the resolved app image.
    $resolved = (Resolve-Path -LiteralPath $work -ErrorAction SilentlyContinue).Path
    if ($resolved -and [IO.Path]::GetDirectoryName($resolved) -eq $app) {
        try { Remove-Item -LiteralPath $resolved -Recurse -Force }
        catch { Write-Warning "Could not remove startup training files: $($_.Exception.Message)" }
    }
    if ($scratch) {
        $tempRoot = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath()) -ErrorAction SilentlyContinue).Path
        $resolved = (Resolve-Path -LiteralPath $scratch -ErrorAction SilentlyContinue).Path
        if ($tempRoot -and $resolved -and [IO.Path]::GetDirectoryName($resolved) -eq [IO.Path]::TrimEndingDirectorySeparator($tempRoot)) {
            try { Remove-Item -LiteralPath $resolved -Recurse -Force }
            catch { Write-Warning "Could not remove the temporary training profile: $($_.Exception.Message)" }
        }
    }
}
