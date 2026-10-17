package com.alananasss.kittytune.data

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.util.concurrent.Executors

/** Uses the bundled app itself: no generated scripts, helper install, or persisted OAuth secrets. */
internal object MacSoundCloudOAuth {
    private const val BUNDLE_ID = "com.alananasss.kittytune"
    @Volatile private var installed = false
    @Volatile private var target: Target? = null
    private data class Target(val state: String, val port: Int)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SoundCloud-URL-forwarder").apply { isDaemon = true }
    }

    fun installUriHandler() {
        if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
        runCatching {
            val desktop = Desktop.getDesktop()
            check(desktop.isSupported(Desktop.Action.APP_OPEN_URI))
            desktop.setOpenURIHandler { event ->
                val active = target ?: return@setOpenURIHandler
                val value = event.uri.toASCIIString()
                // Ignore sc://auth probes and stale callbacks before opening a connection.
                if (SoundCloudOAuthCallback.parse(value, active.state) == null) return@setOpenURIHandler
                worker.execute {
                    if (target !== active) return@execute
                    runCatching {
                        val connection = URI("http://127.0.0.1:${active.port}/callback")
                            .toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
                        try {
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 2000
                            connection.readTimeout = 2000
                            connection.instanceFollowRedirects = false
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                            connection.outputStream.use { it.write(value.toByteArray(Charsets.UTF_8)) }
                            check(connection.responseCode == 200)
                        } finally {
                            connection.disconnect()
                        }
                    }.onFailure { println("SoundCloud callback forwarding failed; retry sign-in.") }
                }
            }
            installed = true
        }.onFailure { println("macOS URL events unavailable; SoundCloud sign-in requires the packaged app.") }
    }

    fun prepare(state: String, port: Int): AutoCloseable {
        check(installed) { "macOS URL events unavailable. Please use the packaged KittyTune.app." }
        // jpackage sets this to the launcher path; do not guess a user's Applications/home directory.
        val launcher = System.getProperty("jpackage.app-path")
            ?: error("Please sign in using the packaged KittyTune.app, rather than Gradle run.")
        val bundle = generateSequence(File(launcher).absoluteFile) { it.parentFile }
            .firstOrNull { it.extension == "app" && File(it, "Contents/Info.plist").isFile }
            ?: error("Could not locate the KittyTune app bundle.")
        val cf = Native.load("CoreFoundation", CoreFoundation::class.java)
        val ls = Native.load("CoreServices", LaunchServices::class.java)
        val bytes = bundle.absolutePath.toByteArray(Charsets.UTF_8)
        val url = cf.CFURLCreateFromFileSystemRepresentation(null, bytes, bytes.size.toLong(), 1)
            ?: error("Could not locate the KittyTune app bundle.")
        val scheme = cf.CFStringCreateWithCString(null, "sc", 0x08000100)
        val identifier = cf.CFStringCreateWithCString(null, BUNDLE_ID, 0x08000100)
        try {
            check(ls.LSRegisterURL(url, 1) == 0) { "Could not register KittyTune with macOS." }
            check(ls.LSSetDefaultHandlerForURLScheme(scheme, identifier) == 0) {
                "Could not select KittyTune for SoundCloud links. Check macOS permissions and retry."
            }
        } finally {
            cf.CFRelease(url)
            cf.CFRelease(scheme)
            cf.CFRelease(identifier)
        }
        val active = Target(state, port)
        target = active
        return AutoCloseable { if (target === active) target = null }
    }

    private interface CoreFoundation : Library {
        fun CFURLCreateFromFileSystemRepresentation(allocator: Pointer?, bytes: ByteArray, length: Long, directory: Byte): Pointer?
        fun CFStringCreateWithCString(allocator: Pointer?, value: String, encoding: Int): Pointer
        fun CFRelease(value: Pointer)
    }

    private interface LaunchServices : Library {
        fun LSRegisterURL(url: Pointer, update: Byte): Int
        fun LSSetDefaultHandlerForURLScheme(scheme: Pointer, handler: Pointer): Int
    }
}
