package com.alananasss.kittytune.audio

import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.utils.SignedUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * HLS streams opened ahead of time for the tracks up next: playlist read, header and first fragment in memory.
 *
 * Starting an HLS track costs a playlist request, an init segment and a first fragment, one after another, before
 * a sound is heard; for the next track in the queue that is paid while the current one plays, so a skip starts the
 * next track at once even a few seconds into this one (issue #66). Bounded to a couple of streams, and a stream
 * whose signature lapsed is dropped rather than handed over.
 */
object HlsHeadCache {

    private const val MAX_STREAMS = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = LinkedHashMap<String, HlsStreamAdapter>()
    private val warming = mutableSetOf<String>()

    /** Opens [playlistUrl] in the background, unless it is already open or opening. */
    fun warm(playlistUrl: String, headers: Map<String, String>) {
        if (!playlistUrl.contains(".m3u8") || SignedUrl.isExpired(playlistUrl)) return
        synchronized(this) {
            if (playlistUrl in ready || !warming.add(playlistUrl)) return
        }
        scope.launch {
            val adapter = runCatching {
                HlsStreamAdapter(playlistUrl, headers).also { it.prefetchOpening() }
            }.onFailure { Logger.w("HlsHeadCache", "Could not open ahead: ${it.message}") }.getOrNull()
            synchronized(this@HlsHeadCache) {
                warming.remove(playlistUrl)
                if (adapter == null) return@launch
                ready[playlistUrl] = adapter
                while (ready.size > MAX_STREAMS) ready.remove(ready.keys.first())
            }
        }
    }

    /** The stream opened ahead for [playlistUrl], handed over once, or null. */
    @Synchronized
    fun take(playlistUrl: String, refresher: (failedUrl: String) -> String?): HlsStreamAdapter? {
        val adapter = ready.remove(playlistUrl) ?: return null
        if (SignedUrl.isExpired(playlistUrl)) return null
        adapter.refreshPlaylistUrl = refresher
        return adapter
    }
}
