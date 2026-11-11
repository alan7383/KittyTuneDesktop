package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import java.io.File

/** The registered handler outlives the login screen, so expired callbacks must exit silently. */
internal object WindowsSoundCloudOAuth {
    fun script(portFile: File): String = """
        Option Explicit
        On Error Resume Next
        Dim url, fso, input, port, req
        If WScript.Arguments.Count <> 1 Then WScript.Quit 0
        url = WScript.Arguments(0)
        If InStr(url, "sc://auth?") <> 1 And InStr(url, "sc://auth/?") <> 1 Then WScript.Quit 0
        If InStr(url, "code=") = 0 And InStr(url, "error=") = 0 Then WScript.Quit 0
        Set fso = CreateObject("Scripting.FileSystemObject")
        Set input = fso.OpenTextFile("${portFile.absolutePath.replace("\"", "\"\"")}", 1)
        If Err.Number <> 0 Then WScript.Quit 0
        port = Trim(input.ReadAll())
        input.Close
        If Not IsNumeric(port) Then WScript.Quit 0
        If CLng(port) < 1 Or CLng(port) > 65535 Then WScript.Quit 0
        Set req = CreateObject("MSXML2.ServerXMLHTTP.6.0")
        req.setProxy 1
        req.setTimeouts 1000, 1000, 2000, 2000
        req.open "POST", "http://127.0.0.1:" & port & "/callback", False
        req.setRequestHeader "Content-Type", "text/plain; charset=utf-8"
        req.send url
        WScript.Quit 0
    """.trimIndent().replace("\n", "\r\n")

    fun prepare(port: Int): AutoCloseable {
        val appDir = AppDirs.dataDir.apply { mkdirs() }
        val portFile = File(appDir, "sc_oauth_port.txt")
        val handler = File(appDir, "sc_handler.vbs")
        handler.writeText(script(portFile))
        portFile.writeText(port.toString())
        val registration = AutoCloseable {
            // An old attempt must never delete a newer attempt's port.
            if (portFile.exists() && portFile.readText().trim() == port.toString()) portFile.delete()
        }
        try {
            val regExe = "${System.getenv("SystemRoot") ?: "C:\\Windows"}\\System32\\reg.exe"
            fun regAdd(key: String, vararg args: String) {
                val proc = ProcessBuilder(listOf(regExe, "add", key, "/f") + args)
                    .redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().use { it.readText() }
                check(proc.waitFor() == 0) { "Could not register SoundCloud handler: $output" }
            }
            regAdd("HKCU\\Software\\Classes\\sc", "/ve", "/d", "URL:sc Protocol")
            regAdd("HKCU\\Software\\Classes\\sc", "/v", "URL Protocol", "/d", "")
            regAdd("HKCU\\Software\\Classes\\sc\\shell\\open\\command", "/ve", "/d",
                """wscript.exe //B //NoLogo "${handler.absolutePath}" "%1"""")
            return registration
        } catch (e: Exception) {
            registration.close()
            throw e
        }
    }
}
