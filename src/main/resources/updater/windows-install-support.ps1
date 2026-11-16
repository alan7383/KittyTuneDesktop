function Get-KittyTuneVersion([string]$Executable) {
    $app = Join-Path ([IO.Path]::GetDirectoryName($Executable)) 'app'
    $config = Join-Path $app 'KittyTune.cfg'
    if (!(Test-Path -LiteralPath $config -PathType Leaf)) { throw 'Installed launcher configuration is missing.' }
    $lines = @(Get-Content -LiteralPath $config -Encoding UTF8)
    $version = @($lines | Where-Object { $_ -match '^java-options=-Djpackage\.app-version=' } | ForEach-Object { $_.Substring('java-options=-Djpackage.app-version='.Length) })
    if ($version.Count -ne 1) { throw 'Cannot determine the installed application version.' }
    $jars = @($lines | Where-Object { $_.StartsWith('app.classpath=') })
    if (!$jars.Count) { throw 'Installed launcher classpath is missing.' }
    foreach ($line in $jars) {
        $jar = $line.Substring('app.classpath='.Length).Replace('$APPDIR', $app)
        if (!(Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Installed application file is missing: $jar" }
    }
    return $version[0].Trim()
}

function Get-KittyTuneVersionKey([string]$Version) {
    $number = ($Version.Trim() -replace '^v', '') -replace '-.*$', ''
    $parsed = $null
    if (![Version]::TryParse($number, [ref]$parsed)) { throw "Invalid application version: $Version" }
    return '{0}.{1}.{2}.{3}' -f $parsed.Major, $parsed.Minor, [Math]::Max(0,$parsed.Build), [Math]::Max(0,$parsed.Revision)
}

function Get-KittyTuneMsiVersion([string]$Package) {
    $installer = $database = $view = $record = $null
    try {
        $installer = New-Object -ComObject WindowsInstaller.Installer
        $database = $installer.OpenDatabase($Package, 0)
        $view = $database.OpenView("SELECT Value FROM Property WHERE Property = 'ProductVersion'")
        [void]$view.Execute()
        $record = $view.Fetch()
        if (!$record) { throw 'Installer product version is missing.' }
        return [string]$record.StringData(1)
    } finally {
        foreach ($item in @($record,$view,$database,$installer)) {
            if ($null -ne $item) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($item) }
        }
    }
}

function Save-KittyTuneShortcuts {
    param([string]$Executable, [string[]]$Folders = @(
        [Environment]::GetFolderPath('Desktop'),
        [Environment]::GetFolderPath('StartMenu'),
        [Environment]::GetFolderPath('CommonDesktopDirectory'),
        [Environment]::GetFolderPath('CommonStartMenu'),
        (Join-Path $env:APPDATA 'Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar')
    ))
    $shell = New-Object -ComObject WScript.Shell
    try {
        foreach ($folder in $Folders | Select-Object -Unique) {
            if (!$folder -or !(Test-Path -LiteralPath $folder -PathType Container)) { continue }
            foreach ($file in Get-ChildItem -LiteralPath $folder -Filter '*.lnk' -File -Recurse -ErrorAction SilentlyContinue) {
                $link = $null
                try {
                    $link = $shell.CreateShortcut($file.FullName)
                    if ($link.TargetPath -and [IO.Path]::GetFullPath($link.TargetPath).Equals($Executable,[StringComparison]::OrdinalIgnoreCase)) {
                        [pscustomobject]@{Path=$file.FullName; Bytes=[IO.File]::ReadAllBytes($file.FullName)}
                    }
                } catch { Write-Warning "Could not inspect shortcut $($file.FullName): $($_.Exception.Message)" }
                finally { if ($null -ne $link) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($link) } }
            }
        }
    } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell) }
}

function Restore-KittyTuneShortcuts([object[]]$Snapshot) {
    foreach ($link in $Snapshot) {
        try {
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($link.Path))
            [IO.File]::WriteAllBytes($link.Path, $link.Bytes)
        } catch { Write-Warning "Could not restore shortcut $($link.Path): $($_.Exception.Message)" }
    }
}
