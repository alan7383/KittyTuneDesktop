package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One worker per session; command bursts cannot create an unbounded set of waiting coroutines. */
internal class ConnectCommandQueue(
    scope: CoroutineScope,
    execute: suspend (ConnectMessage) -> Unit,
    failed: () -> Unit,
    capacity: Int = 16,
) {
    private val queue = Channel<ConnectMessage>(capacity)
    private val worker = scope.launch(Dispatchers.IO) {
        try {
            for (command in queue) execute(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed()
        } finally { queue.cancel() }
    }

    fun enqueue(message: ConnectMessage): Boolean = queue.trySend(message).isSuccess
    fun close() { queue.cancel(); worker.cancel() }
}
