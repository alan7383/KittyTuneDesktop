# Reads the system's media sessions the way the Windows flyout does, and presses buttons on the one
# whose title is $Title. Used by WindowsSmtcServiceTest; prints one KEY=value line per finding.
param([string]$Title, [string]$Press = "")

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
function Await($op, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    $null = $task.Wait(5000)
    $task.Result
}

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

foreach ($session in $manager.GetSessions()) {
    $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    if ($props.Title -ne $Title) { continue }
    $info = $session.GetPlaybackInfo()
    $timeline = $session.GetTimelineProperties()
    "FOUND=1"
    "APP=$($session.SourceAppUserModelId)"
    "ARTIST=$($props.Artist)"
    "ALBUM=$($props.AlbumTitle)"
    "STATUS=$($info.PlaybackStatus)"
    "RATE=$($info.PlaybackRate)"
    "END_MS=$([long]$timeline.EndTime.TotalMilliseconds)"
    "POSITION_MS=$([long]$timeline.Position.TotalMilliseconds)"
    "HAS_THUMBNAIL=$([int]($props.Thumbnail -ne $null))"
    "NEXT_ENABLED=$([int]$info.Controls.IsNextEnabled)"
    switch ($Press) {
        "pause" { "PRESSED=$(Await ($session.TryPauseAsync()) ([bool]))" }
        "play" { "PRESSED=$(Await ($session.TryPlayAsync()) ([bool]))" }
        "next" { "PRESSED=$(Await ($session.TrySkipNextAsync()) ([bool]))" }
        "previous" { "PRESSED=$(Await ($session.TrySkipPreviousAsync()) ([bool]))" }
        "seek" { "PRESSED=$(Await ($session.TryChangePlaybackPositionAsync([long]420000000)) ([bool]))" }
    }
    exit 0
}
"FOUND=0"
