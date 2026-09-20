package com.alananasss.kittytune.audio.providers.deezer

import com.alananasss.kittytune.utils.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import okhttp3.Request
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

object DeezerAudioProxy {
    private const val TAG = "DeezerAudioProxy"
    private var server: HttpServer? = null
    private var port: Int = 0

    @Synchronized
    fun start() {
        if (server != null) return
        try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.createContext("/deezer-stream", StreamHandler())
            s.executor = Executors.newCachedThreadPool { r ->
                Thread(r, "DeezerAudioProxy").apply { isDaemon = true }
            }
            s.start()
            server = s
            port = s.address.port
            Logger.i(TAG, "DeezerAudioProxy started on port $port")
        } catch (e: Exception) {
            Logger.e(TAG, "DeezerAudioProxy: Failed to start: ${e.message}", e)
        }
    }

    fun isDeezerUri(uri: String): Boolean =
        uri.startsWith("metrofuse-deezer://", ignoreCase = true)

    fun isDeezerProxyUrl(uri: String): Boolean =
        uri.startsWith("http://127.0.0.1:") && uri.contains("/deezer-stream")

    fun getPlayableUrl(
        mediaId: String,
        mediaUrl: String,
        trackId: String,
        contentLength: Long?,
        proxyUrl: String = DeezerAudioProvider.DEFAULT_PROXY_URL,
        format: String? = null,
    ): String {
        start()
        val p = port
        val encodedMediaUrl = URLEncoder.encode(mediaUrl, "UTF-8")
        val encodedProxyUrl = URLEncoder.encode(proxyUrl, "UTF-8")
        val encodedTrackId = URLEncoder.encode(trackId, "UTF-8")
        val encodedMediaId = URLEncoder.encode(mediaId, "UTF-8")
        val encodedFormat = format?.let { URLEncoder.encode(it, "UTF-8") } ?: "FLAC"
        val lengthParam = if (contentLength != null && contentLength > 0) "&len=$contentLength" else ""
        return "http://127.0.0.1:$p/deezer-stream?mid=$encodedMediaId&tid=$encodedTrackId&fmt=$encodedFormat&proxy=$encodedProxyUrl$lengthParam&url=$encodedMediaUrl"
    }

    fun getPlayableUrlFromDeezerUri(uriString: String): String {
        val params = parseDeezerCustomUri(uriString)
        return getPlayableUrl(
            mediaId = params["mediaId"] ?: "",
            mediaUrl = params["url"] ?: "",
            trackId = params["trackId"] ?: "",
            contentLength = params["contentLength"]?.toLongOrNull(),
            proxyUrl = params["proxyUrl"] ?: DeezerAudioProvider.DEFAULT_PROXY_URL,
            format = params["format"] ?: "FLAC",
        )
    }

    private fun parseDeezerCustomUri(uriString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val afterScheme = uriString.substringAfter("metrofuse-deezer://")
        val parts = afterScheme.split('?', limit = 2)
        val path = parts[0].trim('/')
        val segments = path.split('/')
        if (segments.size >= 2 && segments[0] == "stream") {
            map["mediaId"] = URLDecoder.decode(segments[1], "UTF-8")
        } else if (segments.isNotEmpty() && segments[0] != "stream") {
            map["mediaId"] = URLDecoder.decode(segments[0], "UTF-8")
        }
        if (parts.size > 1) {
            parts[1].split('&').forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                    map[key] = value
                }
            }
        }
        return map
    }

    private class StreamHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val method = exchange.requestMethod
            if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) {
                exchange.sendResponseHeaders(405, -1)
                return
            }

            val query = exchange.requestURI.rawQuery ?: ""
            val params = parseQueryParams(query)
            val mediaUrl = params["url"] ?: run {
                exchange.sendResponseHeaders(400, -1)
                return
            }
            val trackId = params["tid"] ?: ""
            val proxyUrl = params["proxy"] ?: ""
            val format = params["fmt"] ?: "FLAC"
            val totalLength = params["len"]?.toLongOrNull() ?: -1L

            val mimeType = if (format.contains("FLAC", ignoreCase = true)) "audio/flac" else "audio/mpeg"

            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val range = parseRange(rangeHeader, totalLength)

            val startPos = range?.first ?: 0L
            val endPos = range?.second ?: (if (totalLength > 0) totalLength - 1 else -1L)

            val alignedStart = startPos - (startPos % 2048)
            val dropBytes = (startPos - alignedStart).toInt()

            val upstreamUrl = DeezerAudioProvider.wrapMediaUrlForProxy(mediaUrl, proxyUrl)
            val requestBuilder = Request.Builder()
                .url(upstreamUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36")

            if (alignedStart > 0 || (endPos > 0 && totalLength > 0)) {
                val rangeVal = if (endPos > 0) "bytes=$alignedStart-$endPos" else "bytes=$alignedStart-"
                requestBuilder.header("Range", rangeVal)
            }

            val client = DeezerAudioProvider.clientForProxy(DeezerAudioProvider.defaultClient, proxyUrl)
            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    response.close()
                    exchange.sendResponseHeaders(response.code, -1)
                    return
                }

                val body = response.body
                val actualTotalLength = if (totalLength > 0) totalLength else {
                    response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: body.contentLength().takeIf { it > 0 } ?: -1L
                }

                val requestedLength = if (endPos >= startPos && startPos >= 0) {
                    endPos - startPos + 1
                } else if (actualTotalLength > 0) {
                    actualTotalLength - startPos
                } else {
                    -1L
                }

                exchange.responseHeaders.set("Content-Type", mimeType)
                exchange.responseHeaders.set("Accept-Ranges", "bytes")

                val isPartial = range != null && actualTotalLength > 0
                val statusCode = if (isPartial) 206 else 200

                if (isPartial) {
                    val contentEnd = if (endPos > 0) endPos else actualTotalLength - 1
                    exchange.responseHeaders.set("Content-Range", "bytes $startPos-$contentEnd/$actualTotalLength")
                }

                if (method.equals("HEAD", ignoreCase = true)) {
                    val responseLen = if (requestedLength > 0) requestedLength else 0L
                    exchange.sendResponseHeaders(statusCode, responseLen)
                    response.close()
                    return
                }

                val responseLen = if (requestedLength > 0) requestedLength else 0L
                exchange.sendResponseHeaders(statusCode, responseLen)

                val decryptingStream = DeezerDecryptingInputStream(
                    encrypted = body.byteStream(),
                    trackId = trackId,
                    blockIndex = alignedStart / 2048,
                    initialDropBytes = dropBytes,
                    requestedLength = requestedLength
                )

                decryptingStream.use { input ->
                    response.use {
                        exchange.responseBody.use { out ->
                            val buffer = ByteArray(16 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                            out.flush()
                        }
                    }
                }
            } catch (_: Exception) {
                // Connection closed or client disconnected
            }
        }

        private fun parseRange(header: String?, totalLength: Long): Pair<Long, Long>? {
            if (header == null || !header.startsWith("bytes=")) return null
            val value = header.removePrefix("bytes=").trim()
            val dash = value.indexOf('-')
            if (dash == -1) return null
            val startStr = value.substring(0, dash).trim()
            val endStr = value.substring(dash + 1).trim()
            val start = startStr.toLongOrNull() ?: 0L
            val end = endStr.toLongOrNull() ?: (if (totalLength > 0) totalLength - 1 else -1L)
            return Pair(start, end)
        }

        private fun parseQueryParams(query: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            query.split('&').forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                    map[key] = value
                }
            }
            return map
        }
    }
}
