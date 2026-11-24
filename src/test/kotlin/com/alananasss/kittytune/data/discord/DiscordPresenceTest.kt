package com.alananasss.kittytune.data.discord

import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.DiscordActivityParty
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscordPresenceTest {
    @Test
    fun freshProfileEnablesPresenceAndMigratedProfileKeepsOptOut() {
        val previous = Prefs.snapshot()
        try {
            Prefs.restore(emptyMap())
            assertTrue(PlayerPreferences().getDiscordRpcEnabled())
            Prefs.restore(mapOf("discord_rpc_enabled" to JsonPrimitive(false), "discord_token" to JsonPrimitive("old-login")))
            assertFalse(PlayerPreferences().getDiscordRpcEnabled())
            assertFalse(Prefs.contains("discord_token"))
        } finally {
            Prefs.restore(previous)
        }
    }

    @Test
    fun publishesToADesktopClientThatStartsLaterAndClearsWhenDisabled() = runBlocking {
        val enabled = MutableStateFlow(true)
        var nextPeer: LocalPeer? = null
        val rpc = DiscordRPC(connect = { nextPeer.also { nextPeer = null } }, retryDelayMs = 25)
        val worker = launch { rpc.run(enabled) }
        try {
            val activity = JSONObject().put("type", 2).put("details", "Private test track")
            rpc.setActivity(activity)
            waitUntil { rpc.status.value == DiscordPresenceStatus.WAITING_FOR_DISCORD }

            val first = LocalPeer()
            nextPeer = first
            waitUntil { first.updates.size == 1 && rpc.copyLog().contains("Activity published") }
            assertEquals(DiscordPresenceStatus.READY, rpc.status.value)
            assertEquals("1473071817693331540", first.applicationId)
            assertTrue(first.repliedToPing)
            assertEquals("Private test track", first.updates.single()?.getString("details"))
            assertTrue(rpc.avatar.value?.startsWith("https://cdn.discordapp.com/avatars/") == true)

            val second = LocalPeer()
            first.disconnected = true
            nextPeer = second
            waitUntil { second.updates.size == 1 }
            assertEquals("Private test track", second.updates.first()?.getString("details"))

            enabled.value = false
            waitUntil { second.closed && rpc.status.value == DiscordPresenceStatus.DISABLED }
            assertEquals(2, second.updates.size)
            assertNull(second.updates.last())
            assertNull(rpc.avatar.value)
            val log = rpc.copyLog()
            assertTrue(log.contains("Activity published") && log.contains("Activity cleared"))
            assertFalse(log.contains("Private test track") || log.contains("123456789012345678"))
        } finally {
            worker.cancelAndJoin()
        }
    }

    @Test
    fun encodesListeningPaceArtworkButtonsAndDisplayModes() {
        val track = Track(
            id = 1, title = "Track title", artworkUrl = "https://example.org/cover.png", durationMs = 180_000,
            user = User(id = 2, username = "The artist", avatarUrl = null), permalinkUrl = "https://example.org/song",
        )
        val activity = discordActivity(
            track, "Playlist", true, 30_000, 180_000, DiscordStatusDisplay.ACTIVITY,
            liveInviteUrl = "https://example.org/live",
            party = DiscordActivityParty("party-id", listOf(3, 16)),
            nowMs = 1_700_000_000_000,
        )
        assertEquals(2, activity.getInt("type"))
        assertEquals("KittyTune", activity.getString("name"))
        assertEquals("Track title", activity.getString("details"))
        assertEquals("by The artist", activity.getString("state"))
        assertEquals(0, activity.getInt("status_display_type"))
        assertEquals(1_699_999_970L, activity.getJSONObject("timestamps").getLong("start"))
        assertEquals(1_700_000_150L, activity.getJSONObject("timestamps").getLong("end"))
        assertEquals("https://example.org/cover.png", activity.getJSONObject("assets").getString("large_image"))
        assertEquals(listOf(3, 16), activity.getJSONObject("party").getJSONArray("size").toList())
        assertEquals("https://example.org/live", activity.getJSONArray("buttons").getJSONObject(0).getString("url"))
        assertEquals("https://example.org/song", activity.getJSONArray("buttons").getJSONObject(1).getString("url"))

        val paused = discordActivity(track.copy(sharing = "private"), null, false, 0, 180_000, DiscordStatusDisplay.ARTIST)
        assertEquals("The artist", paused.getString("state"))
        assertEquals(1, paused.getInt("status_display_type"))
        assertEquals("logo", paused.getJSONObject("assets").getString("large_image"))
        assertFalse(paused.has("timestamps") || paused.has("buttons") || paused.has("party"))

        val song = discordActivity(track, null, false, 0, 180_000, DiscordStatusDisplay.SONG)
        assertEquals(2, song.getInt("status_display_type"))
        val soundCloud = discordActivity(track, null, false, 0, 180_000, DiscordStatusDisplay.SOUNDCLOUD)
        assertEquals("SoundCloud", soundCloud.getString("name"))
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    private class LocalPeer : DiscordIpcTransport {
        val updates = mutableListOf<JSONObject?>()
        var applicationId: String? = null
        var repliedToPing = false
        var disconnected = false
        var closed = false
        private var pauseRead = false
        private val pending = ArrayDeque<ByteBuffer>()

        override fun read(target: ByteBuffer): Int {
            if (disconnected || closed) return -1
            if (pauseRead) {
                pauseRead = false
                return 0
            }
            val frame = pending.firstOrNull() ?: return 0
            val count = minOf(13, target.remaining(), frame.remaining())
            repeat(count) { target.put(frame.get()) }
            if (!frame.hasRemaining()) pending.removeFirst()
            pauseRead = true
            return count
        }

        override fun write(source: ByteBuffer): Int {
            if (disconnected || closed) throw IOException("Peer closed")
            val count = source.remaining()
            val opcode = source.int
            val payload = ByteArray(source.int)
            source.get(payload)
            when (opcode) {
                0 -> {
                    val request = JSONObject(payload.toString(Charsets.UTF_8))
                    assertEquals(1, request.getInt("v"))
                    applicationId = request.getString("client_id")
                    enqueue(3, "ping")
                    enqueue(1, """{"cmd":"DISPATCH","evt":"READY","data":{"user":{
                        "id":"123456789012345678","avatar":"0123456789abcdef0123456789abcdef"
                    }}}""")
                }
                1 -> {
                    val request = JSONObject(payload.toString(Charsets.UTF_8))
                    assertEquals("SET_ACTIVITY", request.getString("cmd"))
                    val args = request.getJSONObject("args")
                    assertEquals(ProcessHandle.current().pid(), args.getLong("pid"))
                    updates += args.optJSONObject("activity")
                    enqueue(1, JSONObject().put("cmd", "SET_ACTIVITY").put("nonce", request.getString("nonce")).toString())
                }
                4 -> {
                    assertEquals("ping", payload.toString(Charsets.UTF_8))
                    repliedToPing = true
                }
                else -> error("Unexpected opcode: $opcode")
            }
            return count
        }

        private fun enqueue(opcode: Int, body: String) {
            val bytes = body.toByteArray()
            pending.add(ByteBuffer.allocate(8 + bytes.size).order(LITTLE_ENDIAN)
                .putInt(opcode).putInt(bytes.size).put(bytes).flip())
        }

        override fun close() {
            closed = true
        }
    }
}
