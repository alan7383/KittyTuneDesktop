package com.alananasss.kittytune.ui.musicimport

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.openUrl
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicApiAuth
import com.alananasss.kittytune.data.musicimport.MusicImportCoordinator
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Launches the musicapi connection flow in the system browser, mirroring the
 * official app's WebAuthenticationStarter.
 *
 * The flow ends by redirecting to `soundcloud://musicapi/auth?data64=...`.
 * Since there is no custom-scheme runtime on desktop, we register a temporary
 * `soundcloud://` protocol handler (same technique as the `sc://` login handler)
 * that forwards the deep link to a local loopback callback server. The parsed
 * [MusicApiAuth] is delivered through [MusicImportCoordinator] so the import
 * screens can pick it up.
 */
object MusicApiAuthLauncher {
    private const val TAG = "MusicApiAuthLauncher"
    private const val AUTH_BASE_URL = "https://app.musicapi.com/soundcloud/"
    private const val RETURN_URL = "soundcloud://musicapi/auth"

    @Volatile
    private var server: HttpServer? = null
    @Volatile
    private var isRegistered = false
    private const val CALLBACK_PORT = 47389

    fun authUrl(platform: MusicApi): String =
        AUTH_BASE_URL + platform.providerName + "/auth?returnUrl=" + RETURN_URL

    /**
     * Opens the musicapi auth page in the browser. If a callback server is already
     * running (e.g. the user pressed "Reopen page"), it is reused so the registered
     * protocol handler keeps pointing at the right port.
     */
    fun launch(platform: MusicApi) {
        ensureServerAndHandler()
        openUrl(authUrl(platform))
    }

    private fun ensureServerAndHandler() {
        if (server == null) {
            startCallbackServer()
        }
        if (!isRegistered) {
            registerProtocolHandler()
        }
    }

    private fun startCallbackServer() {
        // Try fixed port first, fall back to any available port
        var httpServer: HttpServer? = null
        var actualPort = CALLBACK_PORT
        try {
            httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", CALLBACK_PORT), 0)
        } catch (e: Exception) {
            // Port busy – try ephemeral port
            try {
                httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                actualPort = (httpServer.address as InetSocketAddress).port
                println("$TAG: preferred port $CALLBACK_PORT busy, using $actualPort")
            } catch (e2: Exception) {
                println("$TAG: could not start callback server: ${e2.message}")
                return
            }
        }
        try {
            httpServer!!.executor = Executors.newCachedThreadPool()
            httpServer.createContext("/callback") { exchange ->
                val url = when (exchange.requestMethod.uppercase()) {
                    "GET" -> exchange.requestURI.toString()
                    "POST" -> String(exchange.requestBody.readBytes())
                    else -> {
                        exchange.sendResponseHeaders(405, -1)
                        exchange.close()
                        return@createContext
                    }
                }

                extractData64(url)?.let { data64 ->
                    MusicApiAuth.fromData64(data64)?.let { auth ->
                        MusicImportCoordinator.deliverAuth(auth)
                        scheduleStop()
                    }
                }

                val response = """
                    <!DOCTYPE html>
                    <html><body style="font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0">
                    <p style="font-size:20px;color:#333">Authentification réussie ! Vous pouvez fermer cette page.</p>
                    </body></html>
                """.trimIndent()
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            httpServer.start()
            server = httpServer
            // If actual port differs from fixed, re-register handler with new port
            if (actualPort != CALLBACK_PORT) {
                isRegistered = false
            }
            println("$TAG: callback server listening on http://127.0.0.1:$actualPort/callback")
        } catch (e: Exception) {
            println("$TAG: could not start callback server: ${e.message}")
        }
    }

    private fun extractData64(url: String): String? {
        if (!url.contains("data64=")) return null
        return try {
            val uri = URI(url.trim())
            val query = uri.query ?: return url.substringAfter("data64=").substringBefore("&")
            query.split("&")
                .mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0 && part.substring(0, idx) == "data64") part.substring(idx + 1) else null
                }
                .firstOrNull()
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        } catch (e: Exception) {
            url.substringAfter("data64=").substringBefore("&")
        }
    }

    private fun scheduleStop() {
        Executors.newSingleThreadScheduledExecutor().schedule({
            stopCallbackServer()
        }, 5, TimeUnit.SECONDS)
    }

    private fun stopCallbackServer() {
        try {
            server?.stop(0)
        } catch (_: Exception) {
        }
        server = null
        // Keep isRegistered=true so we don't re-write the .desktop file next time;
        // the handler script already points to the fixed port.
    }

    // --- Protocol handler registration (same technique as the sc:// login handler) ---

    private fun registerProtocolHandler() {
        if (isRegistered) return
        try {
            val os = System.getProperty("os.name").lowercase()
            val appDir = AppDirs.dataDir
            if (!appDir.exists()) appDir.mkdirs()

            when {
                os.contains("windows") -> registerWindowsHandler(appDir)
                os.contains("linux") -> registerLinuxHandler(appDir)
                os.contains("mac") -> registerMacosHandler(appDir)
                else -> return
            }
            isRegistered = true
        } catch (e: Exception) {
            println("$TAG: failed to register protocol handler: ${e.message}")
        }
    }

    private fun registerLinuxHandler(appDir: File) {
        val actualPort = (server?.address as? InetSocketAddress)?.port ?: CALLBACK_PORT
        val shellFile = File(appDir, "soundcloud_handler.sh")
        shellFile.writeText(buildString {
            append("#!/bin/bash\n")
            append("# Only forward the musicapi deep-link redirect (contains data64=).\n")
            append("# Other soundcloud:// links are intentionally ignored.\n")
            append("if [[ \"\$1\" == *\"data64=\"* ]]; then\n")
            append("  curl -s -X POST 'http://127.0.0.1:$actualPort/callback' -d \"\$1\"\n")
            append("fi\n")
        })
        shellFile.setExecutable(true)

        val mimeDir = File(System.getProperty("user.home"), ".local/share/applications")
        mimeDir.mkdirs()
        val targetDesktop = File(mimeDir, "kittytune-soundcloud.desktop")
        targetDesktop.writeText(buildString {
            append("[Desktop Entry]\n")
            append("Name=KittyTune SoundCloud Auth\n")
            append("Exec=${shellFile.absolutePath} %u\n")
            append("Type=Application\n")
            append("NoDisplay=true\n")
            append("MimeType=x-scheme-handler/soundcloud;\n")
            append("Terminal=false\n")
        })

        Runtime.getRuntime()
            .exec(arrayOf("xdg-mime", "default", "kittytune-soundcloud.desktop", "x-scheme-handler/soundcloud"))
            .waitFor()
        Runtime.getRuntime().exec(arrayOf("update-desktop-database", mimeDir.absolutePath)).waitFor()
        println("$TAG: registered soundcloud:// protocol handler for Linux (port=$actualPort)")
    }

    private fun registerWindowsHandler(appDir: File) {
        val actualPort = (server?.address as? InetSocketAddress)?.port ?: CALLBACK_PORT
        val vbsFile = File(appDir, "soundcloud_handler.vbs")
        val vbsContent = buildString {
            append("If WScript.Arguments.Count > 0 Then\r\n")
            append("  url = WScript.Arguments(0)\r\n")
            append("  If InStr(url, \"data64=\") > 0 Then\r\n")
            append("    Set req = CreateObject(\"MSXML2.ServerXMLHTTP.6.0\")\r\n")
            append("    req.open \"POST\", \"http://127.0.0.1:$actualPort/callback\", False\r\n")
            append("    req.setRequestHeader \"Content-Type\", \"text/plain\"\r\n")
            append("    req.send url\r\n")
            append("  End If\r\n")
            append("End If\r\n")
        }
        vbsFile.writeText(vbsContent)

        val handlerCmd = "wscript.exe \"${vbsFile.absolutePath.replace("\\", "\\\\")}\" \"%1\""

        val psScript = """
            New-Item -Path "HKCU:\\Software\\Classes\\soundcloud" -Force | Out-Null
            New-ItemProperty -Path "HKCU:\\Software\\Classes\\soundcloud" -Name "(default)" -Value "URL:soundcloud Protocol" -Force | Out-Null
            New-ItemProperty -Path "HKCU:\\Software\\Classes\\soundcloud" -Name "URL Protocol" -Value "" -Force | Out-Null
            New-Item -Path "HKCU:\\Software\\Classes\\soundcloud\\shell\\open\\command" -Force | Out-Null
            Set-ItemProperty -Path "HKCU:\\Software\\Classes\\soundcloud\\shell\\open\\command" -Name "(default)" -Value '$handlerCmd' -Force
        """.trimIndent()

        val encodedScript = java.util.Base64.getEncoder().encodeToString(psScript.toByteArray(Charsets.UTF_16LE))
        val psProc = Runtime.getRuntime().exec(
            arrayOf(
                "powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", encodedScript
            )
        )
        psProc.waitFor()
        println("$TAG: registered soundcloud:// protocol handler for Windows")
    }

    private fun registerMacosHandler(appDir: File) {
        val actualPort = (server?.address as? InetSocketAddress)?.port ?: CALLBACK_PORT

        // Build a minimal .app bundle that AppleScript's `open location` event can handle.
        val appBundle = File(appDir, "KittyTuneSoundCloud.app")
        val contentsDir = File(appBundle, "Contents")
        val macosDir = File(contentsDir, "MacOS")
        macosDir.mkdirs()
        File(contentsDir, "Resources").mkdirs()

        // AppleScript source: receives the URL via the 'open location' Apple Event
        // and forwards it to the local callback server.
        val appleScriptSource = buildString {
            append("on open location this_URL\n")
            append("    if this_URL contains \"data64=\" then\n")
            append("        do shell script \"curl -s -X POST 'http://127.0.0.1:$actualPort/callback' -d \" & quoted form of this_URL\n")
            append("    end if\n")
            append("end open location\n")
        }
        val scriptSrc = File(appDir, "soundcloud_handler.applescript")
        scriptSrc.writeText(appleScriptSource)

        // Compile the AppleScript into the .app bundle
        Runtime.getRuntime().exec(
            arrayOf("osacompile", "-o", appBundle.absolutePath, scriptSrc.absolutePath)
        ).waitFor()

        // Patch Info.plist to declare the soundcloud:// URL scheme and run in background
        val plistBuddy = "/usr/libexec/PlistBuddy"
        val plist = File(contentsDir, "Info.plist").absolutePath
        listOf(
            arrayOf(plistBuddy, "-c", "Add :CFBundleURLTypes array", plist),
            arrayOf(plistBuddy, "-c", "Add :CFBundleURLTypes:0 dict", plist),
            arrayOf(plistBuddy, "-c", "Add :CFBundleURLTypes:0:CFBundleURLName string SoundCloud Protocol", plist),
            arrayOf(plistBuddy, "-c", "Add :CFBundleURLTypes:0:CFBundleURLSchemes array", plist),
            arrayOf(plistBuddy, "-c", "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string soundcloud", plist),
            arrayOf(plistBuddy, "-c", "Add :LSBackgroundOnly bool true", plist)
        ).forEach { cmd -> Runtime.getRuntime().exec(cmd).waitFor() }

        // Register the bundle with Launch Services
        val lsregister = "/System/Library/Frameworks/CoreServices.framework/Versions/A/" +
            "Frameworks/LaunchServices.framework/Versions/A/Support/lsregister"
        Runtime.getRuntime().exec(arrayOf(lsregister, "-f", appBundle.absolutePath)).waitFor()

        println("$TAG: registered soundcloud:// protocol handler for macOS (port=$actualPort)")
    }
}
