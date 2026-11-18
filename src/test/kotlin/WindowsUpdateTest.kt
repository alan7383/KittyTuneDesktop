package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.network.GithubRelease
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assume.assumeTrue
import kotlin.test.*

class WindowsUpdateTest {
    @Test fun `restart resolves the launcher property before installed fallback`() {
        val folder = kotlin.io.path.createTempDirectory("kitty portable ").toFile()
        val exe = File(folder, "KittyTune.exe").apply { writeText("fixture") }
        val before = System.getProperty("jpackage.app-path")
        try {
            System.setProperty("jpackage.app-path", exe.path)
            assertEquals(exe.canonicalFile, UpdateManager.resolveKittyTuneExe()?.canonicalFile)
            System.setProperty("jpackage.app-path", File(folder, "missing.exe").path)
            assertNull(UpdateManager.resolveKittyTuneExe())
        } finally {
            if (before == null) System.clearProperty("jpackage.app-path") else System.setProperty("jpackage.app-path", before)
            folder.deleteRecursively()
        }
    }

    @Test fun `failed install never relaunches the existing exe`() = runInstaller(1603, false, false)
    @Test fun `successful exit with stale files is rejected`() = runInstaller(0, false, false)
    @Test fun `verified update stays in place and writes its version`() = runInstaller(0, true, true)
    @Test fun `missing application jar is rejected`() = runInstaller(0, true, false, missingJar = true)
    @Test fun `a registered MSI installation in another folder blocks the update`() =
        runInstaller(0, true, false, conflictingInstall = true)
    @Test fun `missing installed executable is rejected`() = runInstaller(0, true, false, missingExe = true)
    @Test fun `reboot initiated does not trigger another install`() = runInstaller(1641, true, false, true)
    @Test fun `reboot required is success rather than another install attempt`() = runInstaller(3010, true, false, true)

    @Test fun `shortcuts retain their original bytes and unrelated links are untouched`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = kotlin.io.path.createTempDirectory("kitty shortcuts ").toFile()
        fun quote(file: File) = file.path.replace("'", "''")
        try {
            val support = requireNotNull(javaClass.getResourceAsStream("/updater/windows-install-support.ps1")).use { it.bufferedReader().readText() }
            val script = support + "\n" + """
                ${'$'}ErrorActionPreference = 'Stop'
                ${'$'}root = '${quote(root)}'
                ${'$'}exe = Join-Path ${'$'}root 'KittyTune.exe'
                ${'$'}path = Join-Path ${'$'}root 'custom.lnk'
                ${'$'}otherPath = Join-Path ${'$'}root 'unrelated.lnk'
                [IO.File]::WriteAllText(${'$'}exe, 'fixture')
                ${'$'}shell = New-Object -ComObject WScript.Shell
                try {
                    ${'$'}link = ${'$'}shell.CreateShortcut(${'$'}path)
                    ${'$'}link.TargetPath = ${'$'}exe
                    ${'$'}link.Arguments = '--custom'
                    ${'$'}link.WorkingDirectory = ${'$'}root
                    ${'$'}link.IconLocation = "${'$'}env:SystemRoot\system32\shell32.dll,5"
                    ${'$'}link.Save()
                    ${'$'}other = ${'$'}shell.CreateShortcut(${'$'}otherPath)
                    ${'$'}other.TargetPath = Join-Path ${'$'}root 'other.exe'
                    ${'$'}other.Save()
                    ${'$'}original = [Convert]::ToBase64String([IO.File]::ReadAllBytes(${'$'}path))
                    ${'$'}unrelated = [Convert]::ToBase64String([IO.File]::ReadAllBytes(${'$'}otherPath))
                    ${'$'}snapshot = @(Save-KittyTuneShortcuts -Executable ${'$'}exe -Folders @(${'$'}root))
                    if (${'$'}snapshot.Count -ne 1) { throw 'Wrong shortcuts selected' }
                    Remove-Item -LiteralPath ${'$'}path
                    Restore-KittyTuneShortcuts -Snapshot ${'$'}snapshot
                    if ([Convert]::ToBase64String([IO.File]::ReadAllBytes(${'$'}path)) -ne ${'$'}original) { throw 'Shortcut bytes changed' }
                    if ([Convert]::ToBase64String([IO.File]::ReadAllBytes(${'$'}otherPath)) -ne ${'$'}unrelated) { throw 'Unrelated shortcut changed' }
                } finally {
                    foreach (${'$'}item in @(${'$'}link,${'$'}other,${'$'}shell)) {
                        if (${'$'}null -ne ${'$'}item) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject(${'$'}item) }
                    }
                }
            """.trimIndent()
            val file = File(root, "shortcuts.ps1").apply { writeBytes(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())+script.toByteArray()) }
            val outputFile = File(root, "powershell-output.log")
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", file.path)
                .redirectErrorStream(true).redirectOutput(outputFile).start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                fail("Updater test timed out: ${outputFile.readText()}")
            }
            val output = outputFile.readText()
            assertEquals(0,process.waitFor(),output)
        } finally { root.deleteRecursively() }
    }

    private fun runInstaller(exit: Int, replace: Boolean, success: Boolean, reboot: Boolean = false, missingJar: Boolean = false,
        conflictingInstall: Boolean = false, missingExe: Boolean = false,
    ) {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = kotlin.io.path.createTempDirectory("kitty update Ω [mix] O'Brien ").toFile()
        val image = File(root, "portable app").apply { mkdirs() }
        val app = File(image, "app").apply { mkdirs() }
        val exe = File(image, "KittyTune.exe").apply { writeText("fixture") }
        File(app, "main.jar").writeText("fixture")
        val cfg = File(app, "KittyTune.cfg").apply { writeText("app.classpath=${'$'}APPDIR/main.jar\njava-options=-Djpackage.app-version=1.0.0\n") }
        val installer = File(root, "KittyTune installer.msi").apply { writeText("fixture") }
        val launched = File(root, "launched")
        val arguments = File(root, "arguments.txt")
        val prior = UpdateManager.releaseInfo
        fun quote(file: File) = file.path.replace("'", "''")
        try {
            UpdateManager.releaseInfo = GithubRelease(tagName="v2.0.0")
            val script = UpdateManager.buildWindowsUpdaterScript(2147483000,installer,exe,File(root, "update.log"),File(root, "msi.log"))
            // Replace only the Windows Installer query. The real guard and generated script still run.
            val registered = if (conflictingInstall) File(root, "another installation") else image
            val query = "function Get-KittyTuneRelatedInstallLocations { '${quote(registered)}' }"
            val scriptWithFixture = script.replaceFirst("Log \"Starting updater", "$query\nLog \"Starting updater")
            val mocks = """
                function Get-Process { param(${'$'}Id, ${'$'}Name, ${'$'}ErrorAction) return ${'$'}null }
                function Start-Sleep { param(${'$'}Seconds, ${'$'}Milliseconds) }
                function Start-Process {
                    param(${'$'}FilePath, ${'$'}ArgumentList, [switch]${'$'}Wait, [switch]${'$'}PassThru, ${'$'}WindowStyle)
                    if (${'$'}FilePath -eq 'msiexec.exe') {
                        [IO.File]::AppendAllText('${quote(arguments)}', ((${'$'}ArgumentList -join '|') + "`n"))
                        ${if(replace) "[IO.File]::WriteAllText('${quote(cfg)}', ('app.classpath=${'$'}APPDIR/main.jar' + [Environment]::NewLine + 'java-options=-Djpackage.app-version=2.0.0'))" else ""}
                        ${if (missingJar) "Remove-Item -LiteralPath '${quote(File(app, "main.jar"))}' -ErrorAction SilentlyContinue" else ""}
                        ${if (missingExe) "Remove-Item -LiteralPath '${quote(exe)}'" else ""}
                        return [pscustomobject]@{ExitCode=$exit}
                    }
                    [IO.File]::WriteAllText('${quote(launched)}', [string]${'$'}FilePath)
                    return [pscustomobject]@{ExitCode=0}
                }
            """.trimIndent()
            val file = File(root, "run.ps1").apply { writeBytes(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())+(mocks+"\n"+scriptWithFixture).toByteArray()) }
            val outputFile = File(root, "powershell-output.log")
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", file.path)
                .redirectErrorStream(true).redirectOutput(outputFile).start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                fail("Updater test timed out: ${outputFile.readText()}")
            }
            val output = outputFile.readText()
            assertEquals(success || reboot, process.exitValue() == 0, output)
            assertEquals(success, launched.exists(), output)
            if (conflictingInstall) {
                assertFalse(arguments.exists(), "Windows Installer must not be launched for the other copy")
                assertContains(File(root, "update.log").readText(), "another installation")
                return
            }
            val attempts = arguments.readLines()
            attempts.forEach {
                assertTrue(it.contains("INSTALLDIR=\"${image.path}\""),it)
                assertTrue(it.contains("REINSTALLMODE=amus"),it)
            }
            if (reboot) assertEquals(1,attempts.size)
            if (success) {
                assertEquals(exe.path,launched.readText())
                assertEquals("2.0.0",File(image, "version.txt").readText().trim())
                assertTrue(arguments.readText().contains("INSTALLDIR=\"${image.path}\""), arguments.readText())
                assertEquals(1,arguments.readLines().size)
            }
        } finally { UpdateManager.releaseInfo = prior; root.deleteRecursively() }
    }
}
