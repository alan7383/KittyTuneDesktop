package com.alananasss.kittytune.data.lyrics.clients

import com.github.houbb.opencc4j.util.ZhConverterUtil
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Android's KuGou lookup, originally derived from ViMusic through InnerTune. */
internal object KuGouClient {
    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            install(ContentNegotiation) {
                val parser = Json { ignoreUnknownKeys = true }
                json(parser)
                json(parser, ContentType.Text.Html)
                json(parser, ContentType.Text.Plain)
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
        }
    }

    suspend fun getLyrics(title: String, artist: String, durationSeconds: Int): Result<String> {
        val song = title.replace(titleAsides, "").trim()
        val performer = artist.replace(", ", "、").replace(" & ", "、").replace("和", "、")
            .replace(".", "").replace(artistAsides, "").trim()
        val keyword = "$song - $performer".encodeURLParameter(spaceToPlus = false)
        return try {
            val candidate = searchSongs(keyword).firstNotNullOfOrNull { hit ->
                if (durationSeconds <= 0 || abs(hit.duration - durationSeconds) <= 8) {
                    findCandidate(hash = hit.hash)
                } else null
            } ?: findCandidate(keyword = keyword, durationSeconds = durationSeconds)
                ?: error("No KuGou lyrics candidate")
            val raw = download(candidate).decodeBase64String()
            val lines = normalize(raw, song, performer) ?: error("No synced KuGou lyrics")
            Result.success(lines)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun searchSongs(keyword: String): List<SongHit> =
        client.get("https://mobileservice.kugou.com/api/v3/search/song") {
            parameter("version", 9108)
            parameter("plat", 0)
            parameter("pagesize", 8)
            parameter("showtype", 0)
            url.encodedParameters.append("keyword", keyword)
        }.body<SongSearch>().data.info

    private suspend fun findCandidate(hash: String? = null, keyword: String? = null, durationSeconds: Int = 0): Candidate? =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            if (hash != null) parameter("hash", hash)
            if (keyword != null) url.encodedParameters.append("keyword", keyword)
            if (durationSeconds > 0) parameter("duration", durationSeconds * 1_000L)
        }.body<LyricsSearch>().candidates.firstOrNull()

    private suspend fun download(candidate: Candidate): String =
        client.get("https://lyrics.kugou.com/download") {
            parameter("fmt", "lrc")
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", candidate.id)
            parameter("accesskey", candidate.accesskey)
        }.body<LyricsDownload>().content

    private fun normalize(raw: String, title: String, artist: String): String? {
        val timed = raw.replace("&apos;", "'").lines().filter { it matches timedLine }
        val header = timed.take(31).indexOfLast { it matches creditLine }
        val afterHeader = timed.drop(header + 1)
        val footer = (29 until afterHeader.size).firstOrNull { afterHeader[it] matches creditLine }
        val lines = footer?.let(afterHeader::take) ?: afterHeader
        val first = lines.firstOrNull()?.takeUnless { "纯音乐，请欣赏" in it } ?: return null
        val heading = ZhConverterUtil.toSimple(first)
        val credits = listOf(title) + artist.split('、')
        val lyrics = if (credits.any { it.isNotBlank() && ZhConverterUtil.toSimple(it) in heading }) {
            lines.drop(1)
        } else lines
        return lyrics.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private val titleAsides = Regex("""\(.*\)|（.*）|「.*」|『.*』|<.*>|《.*》|〈.*〉|＜.*＞""")
    private val artistAsides = Regex("""\(.*\)|（.*）""")
    private val timedLine = Regex("""\[\d\d:\d\d\.\d{2,3}\].*""")
    private val creditLine = Regex(""".+].+[:：].+""")

    @Serializable private data class SongSearch(val data: SongData = SongData())
    @Serializable private data class SongData(val info: List<SongHit> = emptyList())
    @Serializable private data class SongHit(val duration: Int, val hash: String)
    @Serializable private data class LyricsSearch(val candidates: List<Candidate> = emptyList())
    @Serializable private data class Candidate(val id: Long, val accesskey: String)
    @Serializable private data class LyricsDownload(val content: String)
}
