package com.alananasss.kittytune

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import okhttp3.OkHttpClient
import okhttp3.CookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object TestDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host.contains("youtube.com")) {
                val existing = request.header("Cookie") ?: ""
                if (!existing.contains("CONSENT=")) {
                    val newReq = request.newBuilder().header("Cookie", if (existing.isEmpty()) "CONSENT=YES+cb" else "$existing; CONSENT=YES+cb").build()
                    return@addInterceptor chain.proceed(newReq)
                }
            }
            chain.proceed(request)
        }.build()

    override fun execute(request: Request): Response {
        val okReq = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (k, vals) -> vals.forEach { okReq.addHeader(k, it) } }
        val r = client.newCall(okReq.build()).execute()
        return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
    }
}

fun main() {
    NewPipe.init(TestDownloader)
    val youtubeService = ServiceList.YouTube
    val query = "Rick Astley Never Gonna Give You Up audio"
    val searchInfo = SearchInfo.getInfo(youtubeService, youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), ""))
    val videoResults = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()
    if (videoResults.isEmpty()) {
        println("No results")
        return
    }
    val first = videoResults.first().url
    println("Found video: $first")
    val extractor = youtubeService.getStreamExtractor(first)
    extractor.fetchPage()
    val audioStreams = extractor.audioStreams
    println("Audio streams found: ${audioStreams.size}")
    audioStreams.forEach { println(it.url) }
}
