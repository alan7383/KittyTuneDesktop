package com.alananasss.kittytune.data.discord

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.UUID

internal class DiscordIpcException(val detail: String, val code: Int? = null) : IOException(detail)

internal class DiscordIpc(private val transport: DiscordIpcTransport) : Closeable {
    private companion object {
        const val HANDSHAKE = 0
        const val FRAME = 1
        const val CLOSE = 2
        const val PING = 3
        const val PONG = 4
        const val HEADER_BYTES = 8
        const val MAX_FRAME_BYTES = 64 * 1024
        const val REPLY_TIMEOUT_MS = 3_000L
        const val IO_POLL_MS = 20L
    }

    private val header = ByteBuffer.allocate(HEADER_BYTES).order(LITTLE_ENDIAN)
    private var payload: ByteBuffer? = null
    private var opcode = 0

    suspend fun handshake(applicationId: String): JSONObject? {
        send(HANDSHAKE, JSONObject().put("v", 1).put("client_id", applicationId).toString().toByteArray())
        val reply = awaitReply { it.optString("evt") == "READY" || it.optString("evt") == "ERROR" }
        if (reply.optString("evt") == "ERROR") {
            throw DiscordIpcException("Discord rejected the IPC handshake", reply.errorCode())
        }
        return reply.optJSONObject("data")?.optJSONObject("user")
    }

    suspend fun setActivity(activity: String?) {
        val nonce = UUID.randomUUID().toString()
        val args = JSONObject().put("pid", ProcessHandle.current().pid())
            .put("activity", activity?.let(::JSONObject) ?: JSONObject.NULL)
        val request = JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", nonce)
        send(FRAME, request.toString().toByteArray())
        val reply = awaitReply { it.optString("nonce") == nonce }
        if (reply.optString("evt") == "ERROR") {
            throw DiscordActivityRejected(reply.errorCode())
        }
    }

    /** Keeps the pipe responsive to Discord pings and closes even when playback is idle. */
    suspend fun poll() {
        repeat(16) {
            if (!readFrame()) return
        }
    }

    private suspend fun awaitReply(matches: (JSONObject) -> Boolean): JSONObject =
        withTimeoutOrNull(REPLY_TIMEOUT_MS) {
            var result: JSONObject? = null
            do {
                currentCoroutineContext().ensureActive()
                val received = readFrame { if (matches(it)) result = it }
                if (!received) delay(IO_POLL_MS)
            } while (result == null)
            result
        } ?: throw DiscordIpcException("Discord did not reply on IPC")

    private suspend fun readFrame(onMessage: (JSONObject) -> Unit = {}): Boolean {
        val body = payload ?: run {
            if (!fill(header)) return false
            header.flip()
            opcode = header.int
            val size = header.int
            header.clear()
            if (size !in 0..MAX_FRAME_BYTES) throw DiscordIpcException("Invalid IPC frame length")
            ByteBuffer.allocate(size).also { payload = it }
        }
        if (!fill(body)) return false
        payload = null
        when (opcode) {
            FRAME -> onMessage(parse(body))
            PING -> send(PONG, body.array())
            PONG -> Unit
            CLOSE -> {
                val code = runCatching { parse(body).number("code") }.getOrNull()
                throw DiscordIpcException("Discord closed the IPC connection", code)
            }
            else -> throw DiscordIpcException("Unexpected IPC opcode")
        }
        return true
    }

    private fun fill(target: ByteBuffer): Boolean {
        while (target.hasRemaining()) {
            val read = transport.read(target)
            if (read < 0) throw DiscordIpcException("Discord closed the IPC stream")
            if (read == 0) return false
        }
        return true
    }

    private suspend fun send(opcode: Int, data: ByteArray) {
        val frame = ByteBuffer.allocate(HEADER_BYTES + data.size).order(LITTLE_ENDIAN)
            .putInt(opcode).putInt(data.size).put(data).flip()
        withTimeoutOrNull(REPLY_TIMEOUT_MS) {
            while (frame.hasRemaining()) {
                currentCoroutineContext().ensureActive()
                if (transport.write(frame) == 0) delay(IO_POLL_MS)
            }
            true
        } ?: throw DiscordIpcException("Discord IPC write timed out")
    }

    private fun parse(body: ByteBuffer): JSONObject = try {
        JSONObject(body.array().toString(Charsets.UTF_8))
    } catch (_: JSONException) {
        throw DiscordIpcException("Invalid JSON on Discord IPC")
    }

    private fun JSONObject.errorCode(): Int? = optJSONObject("data")?.number("code")
    private fun JSONObject.number(key: String): Int? = optInt(key, -1).takeIf { it >= 0 }

    override fun close() = transport.close()
}

internal class DiscordActivityRejected(val code: Int?) : IOException("Discord rejected the activity")
