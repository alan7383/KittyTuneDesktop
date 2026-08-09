package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class WindowsSmtcService(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) : Closeable {

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)
    var isConnected: Boolean = false
        private set

    init {
        try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                val exeFile = prepareExecutable()
                if (exeFile != null && exeFile.exists()) {
                    val pb = ProcessBuilder(exeFile.absolutePath)
                    pb.redirectErrorStream(true)
                    val p = pb.start()
                    process = p
                    writer = PrintWriter(OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8), true)

                    thread(isDaemon = true, name = "WindowsSmtc-Reader") {
                        try {
                            val reader = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val msg = line?.trim() ?: continue
                                if (msg == "READY") {
                                    isConnected = true
                                    println("Windows SMTC bridge initialized successfully.")
                                } else if (msg.startsWith("CMD:")) {
                                    val action = msg.removePrefix("CMD:")
                                    mainScope.launch {
                                        when (action) {
                                            "PLAY" -> onPlay()
                                            "PAUSE" -> onPause()
                                            "TOGGLE" -> onPlayPause()
                                            "NEXT" -> onNext()
                                            "PREV" -> onPrevious()
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Windows SMTC initialization skipped/failed: ${e.message}")
            process = null
            writer = null
            isConnected = false
        }
    }

    private fun prepareExecutable(): File? {
        val exeName = "WindowsSmtcBridge.exe"
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kittytune_smtc")
        if (!tempDir.exists()) tempDir.mkdirs()
        val targetFile = File(tempDir, "KittyTune.exe")

        val devFile = File("src/main/resources/native/$exeName")
        if (devFile.exists()) {
            runCatching { devFile.copyTo(targetFile, overwrite = true) }
            if (targetFile.exists()) return targetFile
        }

        val resourceStream = javaClass.getResourceAsStream("/native/$exeName")
        if (resourceStream != null) {
            runCatching {
                targetFile.outputStream().use { out -> resourceStream.copyTo(out) }
            }
            if (targetFile.exists()) return targetFile
        }

        return if (targetFile.exists()) targetFile else null
    }

    fun updateMedia(track: Track?, isPlaying: Boolean) {
        if (writer == null || process == null) return
        try {
            val title = (track?.title ?: "Unknown Title").replace("|", " ")
            val artist = (track?.user?.username ?: "Unknown Artist").replace("|", " ")
            val artUrl = (track?.fullResArtwork ?: "").replace("|", " ")
            val playingFlag = if (isPlaying) "1" else "0"

            val cmd = "UPDATE|$title|$artist|$artUrl|$playingFlag"
            writer?.println(cmd)
        } catch (e: Exception) {
            println("Windows SMTC updateMedia failed: ${e.message}")
        }
    }

    override fun close() {
        try {
            writer?.println("QUIT")
            writer?.flush()
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        writer = null
        isConnected = false
    }
}
