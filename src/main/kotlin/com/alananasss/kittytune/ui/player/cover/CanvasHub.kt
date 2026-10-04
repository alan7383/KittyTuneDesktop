package com.alananasss.kittytune.ui.player.cover

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One decode per URL no matter how many places show it.
 *
 * The player bar, the fullscreen backdrop and the fullscreen card all show the current
 * track's canvas at once: without sharing, that is three FFmpeg decodes of the same
 * stream. Feeds are refcounted: the first collector starts the producer, the last
 * release stops it. A dead producer is dropped so the next collector starts fresh
 * instead of hanging onto a frozen frame forever.
 */
internal class SharedDecodeHub<T>(
    private val scope: CoroutineScope,
    private val produce: suspend CoroutineScope.(url: String, emit: (T?) -> Unit) -> Unit,
) {
    private data class Feed<T>(
        val flow: MutableStateFlow<T?>,
        var refs: Int,
        var job: Job?,
    )

    private val feeds = HashMap<String, Feed<T>>()

    @Synchronized
    fun flowFor(url: String): StateFlow<T?> {
        feeds[url]?.let { existing ->
            existing.refs++
            return existing.flow
        }
        val flow = MutableStateFlow<T?>(null)
        val feed = Feed(flow, 1, null)
        feeds[url] = feed
        feed.job = scope.launch {
            try {
                produce(url) { flow.value = it }
            } finally {
                synchronized(this@SharedDecodeHub) {
                    if (feeds[url] === feed) feeds.remove(url)
                }
            }
        }
        return flow
    }

    @Synchronized
    fun release(url: String) {
        val feed = feeds[url] ?: return
        feed.refs = (feed.refs - 1).coerceAtLeast(0)
        if (feed.refs == 0) {
            feeds.remove(url)
            feed.job?.cancel()
        }
    }

    @Synchronized
    fun refCountForTest(url: String): Int = feeds[url]?.refs ?: 0
}
