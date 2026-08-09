package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.network.RetrofitClient
import okhttp3.Request
import java.io.InputStream
import java.net.URL

class HlsStreamAdapter(
    val playlistUrl: String,
    val headers: Map<String, String>
) {

    class Segment(val url: String, val durationMs: Long, val startTimeMs: Long)

    var initSegmentUrl: String? = null
    var initSegmentData: ByteArray? = null
    val segments = mutableListOf<Segment>()
    var totalDurationMs = 0L

    init {
        loadPlaylist()
    }

    companion object {
        private val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun loadPlaylist() {
        val requestBuilder = Request.Builder().url(playlistUrl)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("Failed to load playlist: ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty playlist")

        var currentStartTime = 0L
        var nextDurationMs = 0L

        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:URI=")) {
                var uri = trimmed.substringAfter("URI=\"").substringBefore("\"")
                if (!uri.startsWith("http")) uri = resolveUrl(playlistUrl, uri)
                initSegmentUrl = uri
            } else if (trimmed.startsWith("#EXTINF:")) {
                val durStr = trimmed.substringAfter("#EXTINF:").substringBefore(",")
                val durSec = durStr.toDoubleOrNull() ?: 0.0
                nextDurationMs = (durSec * 1000).toLong()
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                var segUrl = trimmed
                if (!segUrl.startsWith("http")) segUrl = resolveUrl(playlistUrl, segUrl)
                segments.add(Segment(segUrl, nextDurationMs, currentStartTime))
                currentStartTime += nextDurationMs
                nextDurationMs = 0L
            }
        }
        totalDurationMs = currentStartTime

        initSegmentUrl?.let { url ->
            try {
                val req = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
                val initResponse = client.newCall(req).execute()
                if (initResponse.isSuccessful) {
                    initSegmentData = initResponse.body?.bytes()
                }
                initResponse.close()
            } catch (e: Exception) {
                com.alananasss.kittytune.utils.Logger.e("HlsStreamAdapter", "Failed to cache init segment", e)
            }
        }
    }

    private fun resolveUrl(base: String, rel: String): String {
        return URL(URL(base), rel).toString()
    }

    fun getInputStream(startPositionMs: Long): InputStream {
        return HlsInputStream(startPositionMs)
    }

    inner class HlsInputStream(startPositionMs: Long) : InputStream() {
        private var currentSegmentIndex = 0
        private var currentStream: InputStream? = null
        private var isClosed = false
        private var emittedInit = false

        init {
            currentSegmentIndex = segments.indexOfFirst {
                it.startTimeMs + it.durationMs > startPositionMs
            }.coerceAtLeast(0)
        }

        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n == -1) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (isClosed) return -1

            while (true) {
                if (currentStream == null) {
                    if (!emittedInit && initSegmentUrl != null) {
                        currentStream = if (initSegmentData != null) {
                            java.io.ByteArrayInputStream(initSegmentData)
                        } else {
                            openHttpStream(initSegmentUrl!!)
                        }
                        emittedInit = true
                    } else if (currentSegmentIndex < segments.size) {
                        currentStream = openHttpStream(segments[currentSegmentIndex].url)
                        currentSegmentIndex++
                    } else {
                        return -1 // EOF
                    }
                }

                if (currentStream != null) {
                    val read = currentStream!!.read(b, off, len)
                    if (read == -1) {
                        currentStream?.close()
                        currentStream = null
                    } else {
                        return read
                    }
                }
            }
        }

        private fun openHttpStream(url: String): InputStream {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            val t0 = System.currentTimeMillis()
            com.alananasss.kittytune.utils.Logger.e("HlsStreamAdapter", "Fetching segment: $url")
            val response = client.newCall(req).execute()
            com.alananasss.kittytune.utils.Logger.e(
                "HlsStreamAdapter",
                "Response received in ${System.currentTimeMillis() - t0}ms"
            )
            if (!response.isSuccessful) {
                response.close()
                throw java.io.IOException("HTTP ${response.code} for segment")
            }
            return response.body?.byteStream() ?: throw java.io.IOException("Empty segment body")
        }

        override fun close() {
            isClosed = true
            currentStream?.close()
            currentStream = null
        }
    }
}
