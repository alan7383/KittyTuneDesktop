package com.alananasss.kittytune.data.together

import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * One connection to the relay, carrying the sealed messages of the shared playlists this app follows (issue #66).
 *
 * MQTT over TLS to a public broker: the protocol is made for small messages to many listeners over flaky networks,
 * the client reconnects by itself, and nothing needs running on our side. The broker only ever relays ciphertext;
 * see [TogetherWire]. Brokers are tried in order, the next one when the first will not take us.
 *
 * Connect, subscribe and publish all run sequentially on a single background worker so callers never block on network
 * I/O, and [Pending.Subscribe] awaits `SUBACK` before subsequent [Pending.Publish] (`SyncRequest`) messages leave.
 */
internal class TogetherRelay(private val clientId: String) {

    data class Incoming(val code: String, val message: TogetherMessage, val isSnapshot: Boolean = false)

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incoming: SharedFlow<Incoming> = _incoming

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val followedCodes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val codesByTopic = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val codesBySnapPrefix = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var client: MqttAsyncClient? = null

    /** One worker for connect/subscribe/publish, so messages leave in the order they were asked for. */
    private val workerDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)

    /** The queue of things to do on the wire. Bounded: presence and state are re-sent every few seconds anyway. */
    private val outbox = Channel<Pending>(capacity = OUTBOX_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private sealed interface Pending {
        data class Publish(
            val topic: String,
            val code: String,
            val message: TogetherMessage,
            val isRetained: Boolean = false,
        ) : Pending
        data class ClearSnapshots(val topics: List<String>) : Pending
        data class Subscribe(val topics: Array<String>) : Pending
        data class Unsubscribe(val topics: Array<String>) : Pending
        data object Disconnect : Pending
    }

    private var lastConnectAttemptAt = 0L
    @Suppress("unused")
    private val worker: Job = scope.launch(workerDispatcher) {
        for (work in outbox) {
            if (work is Pending.Disconnect) {
                val existing = client
                client = null
                lastConnectAttemptAt = 0L
                _isConnected.value = false
                runCatching { existing?.disconnect()?.waitForCompletion(2_000L) }
                runCatching { existing?.close() }
                continue
            }
            // Reactions are transient. Drop them when disconnected or delayed in the queue.
            if (work is Pending.Publish && work.message is TogetherMessage.Reaction) {
                if (client?.isConnected != true || System.currentTimeMillis() - work.message.sentAt > 3_000L) continue
            } else if (!ensureConnected()) continue
            when (work) {
                is Pending.Publish -> runCatching {
                    val mqttMessage = MqttMessage(TogetherWire.seal(work.code, work.message)).apply {
                        qos = work.message.deliveryQos
                        isRetained = work.isRetained
                    }
                    client?.publish(work.topic, mqttMessage)
                }.onFailure { Logger.w("TogetherRelay", "Send failed: ${it.message}") }
                is Pending.ClearSnapshots -> work.topics.forEach { topic ->
                    runCatching {
                        val clearMessage = MqttMessage(ByteArray(0)).apply {
                            qos = 1
                            isRetained = true
                        }
                        client?.publish(topic, clearMessage)
                    }.onFailure { Logger.w("TogetherRelay", "Clear snapshot failed: ${it.message}") }
                }
                is Pending.Subscribe -> runCatching {
                    client?.subscribe(work.topics, IntArray(work.topics.size) { 1 })?.waitForCompletion(SUBSCRIBE_TIMEOUT_MS)
                }.onFailure { Logger.w("TogetherRelay", "Subscribe failed: ${it.message}") }
                is Pending.Unsubscribe -> runCatching {
                    client?.unsubscribe(work.topics)
                }.onFailure { Logger.w("TogetherRelay", "Unsubscribe failed: ${it.message}") }
                Pending.Disconnect -> Unit
            }
        }
    }

    private suspend fun ensureConnected(): Boolean {
        val existing = client
        if (existing != null) {
            if (existing.isConnected) return true
            return withTimeoutOrNull(RECONNECT_WAIT_MS) {
                while (isActive && !existing.isConnected) delay(RECONNECT_POLL_MS)
                existing.isConnected
            } == true
        }
        val now = System.currentTimeMillis()
        if (now - lastConnectAttemptAt < RETRY_COOLDOWN_MS) return false
        lastConnectAttemptAt = now
        val brokers = System.getProperty("kittytune.together.testBroker")?.let(::listOf) ?: BROKERS
        for (broker in brokers) {
            val attempt = runCatching { connect(broker) }
            if (attempt.isSuccess && client?.isConnected == true) return true
            Logger.w("TogetherRelay", "Could not reach $broker: ${attempt.exceptionOrNull()?.message}")
        }
        return false
    }

    private fun connect(broker: String) {
        val mqtt = MqttAsyncClient(broker, clientId, MemoryPersistence())
        mqtt.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _isConnected.value = true
                // A reconnect starts with no subscriptions: take both live and snapshot topics again.
                followedCodes.forEach { code ->
                    val topics = arrayOf(
                        TogetherWire.topicFor(code),
                        TogetherWire.snapshotWildcardTopicFor(code),
                    )
                    runCatching { mqtt.subscribe(topics, intArrayOf(1, 1)) }
                }
            }

            override fun connectionLost(cause: Throwable?) {
                _isConnected.value = false
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                if (message.payload.isEmpty()) return
                val code = resolveCodeForTopic(topic) ?: return
                val opened = TogetherWire.open(code, message.payload) ?: return
                val isSnapshot = message.isRetained || topic.contains("/snap/")
                _incoming.tryEmit(Incoming(code, opened, isSnapshot = isSnapshot))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 5
            keepAliveInterval = 30
        }
        try {
            mqtt.connect(options).waitForCompletion(CONNECT_TIMEOUT_MS)
        } catch (t: Throwable) {
            runCatching { mqtt.close() }
            throw t
        }
        client = mqtt
        _isConnected.value = true
    }

    internal fun resolveCodeForTopic(topic: String): String? {
        codesByTopic[topic]?.let { return it }
        for ((prefix, code) in codesBySnapPrefix) {
            if (topic.startsWith(prefix)) return code
        }
        return null
    }

    fun follow(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.isEmpty()) return
        val mainTopic = TogetherWire.topicFor(normalized)
        val snapWildcard = TogetherWire.snapshotWildcardTopicFor(normalized)
        val snapPrefix = snapWildcard.removeSuffix("#")
        followedCodes.add(normalized)
        codesByTopic[mainTopic] = normalized
        codesByTopic[TogetherWire.stateSnapshotTopicFor(normalized)] = normalized
        codesByTopic[TogetherWire.queueSnapshotTopicFor(normalized)] = normalized
        codesBySnapPrefix[snapPrefix] = normalized
        outbox.trySend(Pending.Subscribe(arrayOf(mainTopic, snapWildcard)))
    }

    fun unfollow(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.isEmpty()) return
        val mainTopic = TogetherWire.topicFor(normalized)
        val snapWildcard = TogetherWire.snapshotWildcardTopicFor(normalized)
        val snapPrefix = snapWildcard.removeSuffix("#")
        followedCodes.remove(normalized)
        codesByTopic.remove(mainTopic)
        codesByTopic.remove(TogetherWire.stateSnapshotTopicFor(normalized))
        codesByTopic.remove(TogetherWire.queueSnapshotTopicFor(normalized))
        codesBySnapPrefix.remove(snapPrefix)
        outbox.trySend(Pending.Unsubscribe(arrayOf(mainTopic, snapWildcard)))
    }

    fun isFollowing(code: String): Boolean = followedCodes.contains(TogetherWire.normalizeCode(code))

    fun send(code: String, message: TogetherMessage) {
        val normalized = TogetherWire.normalizeCode(code)
        outbox.trySend(
            Pending.Publish(
                topic = TogetherWire.topicFor(normalized),
                code = normalized,
                message = message,
                isRetained = false,
            )
        )
    }

    fun sendSnapshot(topic: String, code: String, message: TogetherMessage) {
        val normalized = TogetherWire.normalizeCode(code)
        outbox.trySend(
            Pending.Publish(
                topic = topic,
                code = normalized,
                message = message,
                isRetained = true,
            )
        )
    }

    fun clearSnapshots(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        outbox.trySend(
            Pending.ClearSnapshots(
                listOf(
                    TogetherWire.stateSnapshotTopicFor(normalized),
                    TogetherWire.queueSnapshotTopicFor(normalized),
                )
            )
        )
    }

    fun close() {
        while (outbox.tryReceive().isSuccess) {
            // Drop stale queued work before disconnecting
        }
        outbox.trySend(Pending.Disconnect)
    }

    private companion object {
        val BROKERS = listOf("ssl://broker.emqx.io:8883", "ssl://broker.hivemq.com:8883")
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val SUBSCRIBE_TIMEOUT_MS = 1_500L
        const val RETRY_COOLDOWN_MS = 15_000L
        const val RECONNECT_WAIT_MS = 5_000L
        const val RECONNECT_POLL_MS = 200L
        const val OUTBOX_CAPACITY = 256
    }
}
