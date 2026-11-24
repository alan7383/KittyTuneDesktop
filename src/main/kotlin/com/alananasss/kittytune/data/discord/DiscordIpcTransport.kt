package com.alananasss.kittytune.data.discord

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

/** A local byte stream. A zero-length transfer means the caller should wait and try again. */
internal interface DiscordIpcTransport : Closeable {
    fun read(target: ByteBuffer): Int
    fun write(source: ByteBuffer): Int
}

internal suspend fun openDiscordIpc(): DiscordIpcTransport? {
    if (Platform.isWindows()) return WindowsDiscordPipe.open()
    if (!Platform.isLinux() && !Platform.isMac()) return null
    for (path in discordSocketPaths()) {
        if (!Files.exists(path)) continue
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            channel.configureBlocking(false)
            if (channel.connect(UnixDomainSocketAddress.of(path)) || withTimeoutOrNull(250) {
                    while (!channel.finishConnect()) delay(10)
                    true
                } == true
            ) {
                return UnixDiscordSocket(channel)
            }
        } catch (_: IOException) {
            // A socket left behind by an older client should not hide the next one.
        } finally {
            if (!channel.isConnected) channel.close()
        }
    }
    return null
}

internal fun discordSocketPaths(env: (String) -> String? = System::getenv): List<Path> {
    val directories = buildList {
        listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNullTo(this) { name ->
            env(name)?.takeIf(String::isNotBlank)?.let(Path::of)?.takeIf(Path::isAbsolute)
        }
        env("XDG_RUNTIME_DIR")?.takeIf(String::isNotBlank)?.let(Path::of)?.takeIf(Path::isAbsolute)?.let { runtime ->
            for (app in listOf("com.discordapp.Discord", "com.discordapp.DiscordCanary", "com.discordapp.DiscordPTB")) {
                add(runtime.resolve("app").resolve(app))
            }
            add(runtime.resolve("snap.discord"))
        }
        add(Path.of("/tmp"))
    }
    return directories.distinct().flatMap { directory -> (0..9).map { directory.resolve("discord-ipc-$it") } }
}

private class UnixDiscordSocket(private val channel: SocketChannel) : DiscordIpcTransport {
    override fun read(target: ByteBuffer): Int = channel.read(target)
    override fun write(source: ByteBuffer): Int = channel.write(source)
    override fun close() = channel.close()
}

internal class WindowsDiscordPipe private constructor(private val handle: WinNT.HANDLE) : DiscordIpcTransport {
    private val bytes = ByteArray(8_192)
    private val transferred = IntByReference()

    override fun read(target: ByteBuffer): Int {
        val length = minOf(bytes.size, target.remaining())
        if (windows.ReadFile(handle, bytes, length, transferred, null)) {
            val count = transferred.value
            target.put(bytes, 0, count)
            return count
        }
        val error = windows.GetLastError()
        if (error == WinError.ERROR_NO_DATA) return 0
        throw DiscordIpcException("Named pipe read failed", error)
    }

    override fun write(source: ByteBuffer): Int {
        val start = source.position()
        val length = minOf(bytes.size, source.remaining())
        source.get(start, bytes, 0, length)
        if (!windows.WriteFile(handle, bytes, length, transferred, null)) {
            throw DiscordIpcException("Named pipe write failed", windows.GetLastError())
        }
        return transferred.value.also { source.position(start + it) }
    }

    override fun close() {
        windows.CloseHandle(handle)
    }

    companion object {
        private val windows: Kernel32 get() = Kernel32.INSTANCE

        fun open(indices: IntRange = 0..9): WindowsDiscordPipe? {
            var setupError: Int? = null
            for (index in indices) {
                val handle = windows.CreateFile(
                    "\\\\.\\pipe\\discord-ipc-$index",
                    WinNT.GENERIC_READ or WinNT.GENERIC_WRITE,
                    0, null, WinNT.OPEN_EXISTING, 0, null
                )
                if (handle == null || handle == WinBase.INVALID_HANDLE_VALUE) {
                    val error = windows.GetLastError()
                    if (error == WinError.ERROR_ACCESS_DENIED) setupError = error
                    continue
                }
                // PIPE_NOWAIT keeps both reads and writes cancellable by the IPC worker.
                val mode = IntByReference(WinBase.PIPE_READMODE_BYTE or WinBase.PIPE_NOWAIT)
                if (windows.SetNamedPipeHandleState(handle, mode, null, null)) return WindowsDiscordPipe(handle)
                setupError = windows.GetLastError()
                windows.CloseHandle(handle)
            }
            if (setupError != null) throw DiscordIpcException("Could not open a Discord named pipe", setupError)
            return null
        }
    }
}
