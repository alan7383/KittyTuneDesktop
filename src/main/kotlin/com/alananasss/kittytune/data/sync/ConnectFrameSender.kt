package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Bounded FIFO keeps state-before-ACK ordering; JSON/gzip/crypto never run on the caller's UI thread. */
internal class ConnectFrameSender(
    scope: CoroutineScope,
    private val encode: (ConnectMessage, Boolean) -> String,
    private val send: (String) -> Boolean,
    private val failed: () -> Unit,
    capacity: Int = 32,
) {
    private data class Frame(val message: ConnectMessage, val compress: Boolean)
    private val frames = Channel<Frame>(capacity)
    private val worker: Job = scope.launch(Dispatchers.IO) {
        try {
            for (frame in frames) {
                check(send(encode(frame.message, frame.compress))) { "Transport rejected the frame" }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed()
        } finally { frames.cancel() }
    }

    fun enqueue(message: ConnectMessage, compress: Boolean): Boolean = frames.trySend(Frame(message, compress)).isSuccess
    fun close() { frames.cancel(); worker.cancel() }
}
