package com.alananasss.kittytune.data.discord

import com.alananasss.kittytune.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

internal enum class DiscordPresenceStatus { DISABLED, WAITING_FOR_DISCORD, READY }

/** One worker owns the IPC connection and publishes the latest activity after each reconnect. */
internal class DiscordRPC(
    private val connect: suspend () -> DiscordIpcTransport? = ::openDiscordIpc,
    private val retryDelayMs: Long = 5_000,
) {
    private companion object {
        const val APPLICATION_ID = "1473071817693331540"
        const val POLL_MS = 200L
        const val MAX_LOG_LINES = 160
    }

    private val desired = MutableStateFlow<String?>(null)
    private val _status = MutableStateFlow(DiscordPresenceStatus.DISABLED)
    private val _avatar = MutableStateFlow<String?>(null)
    private val events = ArrayDeque<String>()

    val status = _status.asStateFlow()
    val avatar = _avatar.asStateFlow()

    fun setActivity(activity: JSONObject?) {
        desired.value = activity?.toString()
    }

    /** Run on the IO dispatcher for the lifetime of the player. */
    suspend fun run(enabled: Flow<Boolean>) {
        enabled.distinctUntilChanged().collectLatest { active ->
            if (active) runWhileEnabled() else setStatus(DiscordPresenceStatus.DISABLED)
        }
    }

    private suspend fun runWhileEnabled() {
        setStatus(DiscordPresenceStatus.WAITING_FOR_DISCORD)
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val transport = connect()
                    if (transport != null) DiscordIpc(transport).use { runSession(it) }
                } catch (e: DiscordIpcException) {
                    record(e.detail, e.code)
                } catch (e: IOException) {
                    record("Discord IPC I/O error (${e.javaClass.simpleName})")
                }
                setStatus(DiscordPresenceStatus.WAITING_FOR_DISCORD)
                delay(retryDelayMs)
            }
        } finally {
            setStatus(DiscordPresenceStatus.DISABLED)
        }
    }

    private suspend fun runSession(ipc: DiscordIpc) {
        _avatar.value = discordUserAvatarUrl(ipc.handshake(APPLICATION_ID))
        setStatus(DiscordPresenceStatus.READY)
        var lastRequested: String? = null
        var mayHaveActivity = false
        try {
            while (currentCoroutineContext().isActive) {
                val activity = desired.value
                if (activity != lastRequested) {
                    lastRequested = activity
                    if (activity != null) mayHaveActivity = true
                    try {
                        ipc.setActivity(activity)
                        mayHaveActivity = activity != null
                        record(if (mayHaveActivity) "Activity published" else "Activity cleared")
                    } catch (e: DiscordActivityRejected) {
                        record("Discord rejected the activity", e.code)
                    }
                }
                ipc.poll()
                delay(POLL_MS)
            }
        } finally {
            _avatar.value = null
            if (mayHaveActivity) withContext(NonCancellable) {
                // Give a healthy client time to clear the old status even when the UI cancels us.
                try {
                    withTimeoutOrNull(750) {
                        ipc.setActivity(null)
                        record("Activity cleared")
                    }
                } catch (_: IOException) {
                    // A disconnected client already discarded this connection's activity.
                }
            }
        }
    }

    private fun setStatus(status: DiscordPresenceStatus) {
        if (_status.value == status) return
        record(when (status) {
            DiscordPresenceStatus.DISABLED -> "Rich Presence disabled"
            DiscordPresenceStatus.WAITING_FOR_DISCORD -> "Waiting for Discord Desktop"
            DiscordPresenceStatus.READY -> "Connected to Discord Desktop"
        })
        _status.value = status
    }

    @Synchronized
    private fun record(event: String, code: Int? = null) {
        if (events.size == MAX_LOG_LINES) events.removeFirst()
        val suffix = code?.let { " (code $it)" }.orEmpty()
        events.addLast("${Instant.now()} $event$suffix")
    }

    @Synchronized
    fun copyLog(): String = buildString {
        appendLine("KittyTune ${BuildConfig.VERSION_NAME} — Discord Rich Presence")
        appendLine("${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        appendLine()
        events.forEach { appendLine(it) }
    }
}

private val discordAvatarHash = Regex("(?:a_)?[a-fA-F0-9]{32}")

internal fun discordUserAvatarUrl(user: JSONObject?): String? {
    val id = user?.optString("id")?.takeIf { it.length in 1..20 && it.all { ch -> ch in '0'..'9' } } ?: return null
    val hash = user.optString("avatar").takeIf(discordAvatarHash::matches) ?: return null
    return "https://cdn.discordapp.com/avatars/$id/$hash.png?size=128"
}
