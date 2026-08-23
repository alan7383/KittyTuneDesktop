package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class MxmResponse<T>(val message: MxmMessage<T>)
data class MxmMessage<T>(val header: MxmHeader, val body: T?)
data class MxmHeader(@SerializedName("status_code") val statusCode: Int, val hint: String? = null)

data class MxmTokenBody(@SerializedName("user_token") val userToken: String)
data class MxmTrackListBody(@SerializedName("track_list") val trackList: List<MxmTrackWrapper>?)
data class MxmTrackWrapper(val track: MxmTrack)
data class MxmTrack(
    @SerializedName("track_id") val trackId: Long,
    @SerializedName("track_name") val trackName: String,
    @SerializedName("artist_name") val artistName: String,
    @SerializedName("album_name") val albumName: String?,
    @SerializedName("track_length") val trackLength: Int,
    @SerializedName("has_subtitles") val hasSubtitles: Int,
    @SerializedName("has_richsync") val hasRichSync: Int
)

data class MxmSubtitleBody(val subtitle: MxmSubtitleObj?)
data class MxmSubtitleObj(@SerializedName("subtitle_body") val subtitleBody: String)
data class MxmRichSyncBody(val richsync: MxmRichSyncObj?)
data class MxmRichSyncObj(@SerializedName("richsync_body") val richsyncBody: String)
data class MxmLyricsBody(val lyrics: MxmLyricsObj?)
data class MxmLyricsObj(@SerializedName("lyrics_body") val lyricsBody: String)

data class MxmSubtitleLine(val time: MxmTime? = null, val text: String? = null)
data class MxmTime(val total: Float = 0f)

data class MxmTranslationListBody(@SerializedName("translations_list") val translationsList: List<MxmTranslationWrapper>?)
data class MxmTranslationWrapper(val translation: MxmTranslation?)
data class MxmTranslation(
    val description: String, 
    @SerializedName("matched_line") val matchedLine: String
)

data class MxmRichSyncLine(
    val ts: Float = 0f,
    val te: Float = 0f,
    val l: List<MxmRichSyncWordItem>? = null,
    val x: String? = null
)
data class MxmRichSyncWordItem(
    val c: String? = null,
    val o: Float = 0f
)

interface MusixmatchApiService {
    @GET("token.get")
    suspend fun getToken(
        @Query("adv_id") advId: String,
        @Query("root") root: String = "0",
        @Query("sideloaded") sideloaded: String = "0",
        @Query("build_number") buildNumber: String = "2022090901",
        @Query("guid") guid: String,
        @Query("lang") lang: String = "en_US",
        @Query("model") model: String = "manufacturer/Google brand/Google model/Pixel 6",
        @Query("timestamp") timestamp: String
    ): MxmResponse<MxmTokenBody>

    @GET("track.search")
    suspend fun searchTrack(
        @Query("q_track_artist") query: String,
        @Query("s_track_rating") sort: String = "desc",
        @Query("page_size") limit: Int = 10,
        @Query("usertoken") token: String
    ): MxmResponse<MxmTrackListBody>

    @GET("track.subtitle.get")
    suspend fun getSubtitle(
        @Query("track_id") trackId: Long,
        @Query("subtitle_format") subtitleFormat: String = "mxm",
        @Query("usertoken") token: String
    ): MxmResponse<MxmSubtitleBody>

    @GET("track.richsync.get")
    suspend fun getRichSync(
        @Query("track_id") trackId: Long,
        @Query("usertoken") token: String
    ): MxmResponse<MxmRichSyncBody>

    @GET("track.lyrics.get")
    suspend fun getLyrics(
        @Query("track_id") trackId: Long,
        @Query("usertoken") token: String
    ): MxmResponse<MxmLyricsBody>

    @GET("crowd.track.translations.get")
    suspend fun getTranslations(
        @Query("track_id") trackId: Long,
        @Query("selected_language") lang: String,
        @Query("translation_fields_set") fieldsSet: String = "minimal",
        @Query("usertoken") token: String
    ): MxmResponse<MxmTranslationListBody>
}

object MusixmatchClient {
    private const val BASE_URL = "https://apic.musixmatch.com/ws/1.1/"
    private const val MXM_APP_ID = "android-player-v1.0"
    
    private val MXM_SECRET = "mNdca@6W7TeEcFn6*3.s97sJ*yPMd".toByteArray(Charsets.UTF_8)
    private val gson = Gson()

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        val urlBuilder = originalRequest.url.newBuilder()
            .addQueryParameter("app_id", MXM_APP_ID)
            .addQueryParameter("format", "json")

        val urlToSign = urlBuilder.build().toString()

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val today = dateFormat.format(Date())
        val dataToSign = urlToSign + today

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(MXM_SECRET, "HmacSHA1"))
        val signatureBytes = mac.doFinal(dataToSign.toByteArray(Charsets.UTF_8))
        
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes) + "\n"

        val finalUrl = urlBuilder
            .addQueryParameter("signature", signatureBase64)
            .addQueryParameter("signature_protocol", "sha1")
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(finalUrl)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 6 Build/T3B2.230316.003)")
            .header("Accept", "application/json")
            .header("Connection", "keep-alive")
            .build()

        chain.proceed(newRequest)
    }

    private val baseHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val httpClient: OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseHttpClient.newBuilder()).build()

    val api: MusixmatchApiService
        get() = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusixmatchApiService::class.java)

    private fun generateGuid(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }

    private fun getRfc3339Timestamp(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return df.format(Date())
    }

    suspend fun getValidToken(): String {
        var token = Prefs.getString("mxm_user_token", null)
        
        if (token == null || token == "invalid_token") {
            try {
                val response = api.getToken(
                    advId = UUID.randomUUID().toString(),
                    guid = generateGuid(),
                    timestamp = getRfc3339Timestamp()
                )
                
                token = response.message.body?.userToken
                if (!token.isNullOrEmpty()) {
                    Prefs.putString("mxm_user_token", token)
                }
            } catch (e: Exception) { 
                println("Failed to get Musixmatch token: ${e.message}")
            }
        }
        return token ?: "invalid_token"
    }

    suspend fun getLyricsData(trackId: Long, durationMs: Long, targetLang: String? = null, wantsRomanization: Boolean = false): Pair<List<LyricLine>, String?> = withContext(Dispatchers.IO) {
        var token = getValidToken()

        var subtitleRes = try { api.getSubtitle(trackId = trackId, token = token) } catch (e: Exception) { null }

        if (subtitleRes?.message?.header?.statusCode == 401 && subtitleRes.message.header.hint == "renew") {
            Prefs.remove("mxm_user_token")
            token = getValidToken()
            subtitleRes = try { api.getSubtitle(trackId = trackId, token = token) } catch (e: Exception) { null }
        }

        val richSyncRes = try { api.getRichSync(trackId, token) } catch (e: Exception) { null }
        val plainRes = try { api.getLyrics(trackId, token) } catch (e: Exception) { null }

        val plainText = plainRes?.message?.body?.lyrics?.lyricsBody?.replace("******* This Lyrics is NOT for Commercial use *******", "")?.trim()
        val subtitleJson = subtitleRes?.message?.body?.subtitle?.subtitleBody
        val richSyncJson = richSyncRes?.message?.body?.richsync?.richsyncBody

        val lines = mutableListOf<LyricLine>()

        val mxmLines: List<MxmSubtitleLine> = try {
            if (!subtitleJson.isNullOrBlank() && subtitleJson.trim().startsWith("[")) {
                gson.fromJson(subtitleJson, object : TypeToken<List<MxmSubtitleLine>>() {}.type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        val mxmRichLines: List<MxmRichSyncLine> = try {
            if (!richSyncJson.isNullOrBlank() && richSyncJson.trim().startsWith("[")) {
                gson.fromJson(richSyncJson, object : TypeToken<List<MxmRichSyncLine>>() {}.type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        val translationMap = mutableMapOf<String, String>()
        val romanizationMap = mutableMapOf<String, String>()

        val originalLines = mutableListOf<String>()
        if (mxmRichLines.isNotEmpty()) {
            originalLines.addAll(mxmRichLines.mapNotNull { it.x }.filter { it.isNotBlank() })
        } else if (mxmLines.isNotEmpty()) {
            originalLines.addAll(mxmLines.mapNotNull { it.text }.filter { it.isNotBlank() })
        }
        val uniqueOriginalLines = originalLines.distinct()

        if (targetLang != null) {
            try {
                val translationsRes = api.getTranslations(trackId = trackId, lang = targetLang, token = token)
                translationsRes.message.body?.translationsList?.forEach { wrapper ->
                    wrapper.translation?.let { t ->
                        translationMap[t.matchedLine.trim()] = t.description.trim()
                    }
                }
            } catch (e: Exception) { }

            val missingLines = uniqueOriginalLines.filter { !translationMap.containsKey(it.trim()) }
            if (missingLines.isNotEmpty()) {
                val machineTranslations = FreeTranslator.translateMissing(missingLines, targetLang)
                translationMap.putAll(machineTranslations)
            }
        }

        if (wantsRomanization && uniqueOriginalLines.isNotEmpty()) {
            val rom = FreeTranslator.getRomanization(uniqueOriginalLines)
            romanizationMap.putAll(rom)
        }

        if (mxmRichLines.isNotEmpty()) {
            for (rLine in mxmRichLines) {
                val lineText = rLine.x ?: ""
                if (lineText.isBlank()) continue

                val startMs = (rLine.ts * 1000).toLong()
                val endMs = (rLine.te * 1000).toLong()

                val words = rLine.l?.mapIndexed { index, w ->
                    val wordStartMs = startMs + (w.o * 1000).toLong()
                    val wordEndMs = if (index < rLine.l.size - 1) startMs + (rLine.l[index + 1].o * 1000).toLong() else endMs
                    LyricWord(w.c ?: "", wordStartMs, wordEndMs)
                } ?: emptyList()

                val translationText = translationMap[lineText.trim()]
                val romanizationText = romanizationMap[lineText.trim()]
                lines.add(LyricLine(lineText, startMs, endMs, words, translationText, romanizationText))
            }
        } else if (mxmLines.isNotEmpty()) {
            for (i in mxmLines.indices) {
                val sub = mxmLines[i]
                val lineText = sub.text ?: ""
                if (lineText.isBlank()) continue

                val startMs = ((sub.time?.total ?: 0f) * 1000).toLong()
                val endMs = if (i < mxmLines.size - 1) ((mxmLines[i + 1].time?.total ?: 0f) * 1000).toLong() else durationMs

                val translationText = translationMap[lineText.trim()]
                val romanizationText = romanizationMap[lineText.trim()]
                lines.add(LyricLine(lineText, startMs, endMs, emptyList(), translationText, romanizationText))
            }
        }

        return@withContext Pair(lines, plainText)
    }

    suspend fun search(query: String): List<MxmTrack> {
        var token = getValidToken()
        return try {
            var response = api.searchTrack(query = query, token = token)
            
            // Renouvellement de token si expiré
            if (response.message.header.statusCode == 401 && response.message.header.hint == "renew") {
                Prefs.remove("mxm_user_token")
                token = getValidToken()
                response = api.searchTrack(query = query, token = token)
            }
            
            response.message.body?.trackList?.map { it.track } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

object FreeTranslator {
    private val client: okhttp3.OkHttpClient
        get() = ProxyManager.getOkHttpClient()

    suspend fun translateMissing(
        linesToTranslate: List<String>,
        targetLang: String
    ): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (linesToTranslate.isEmpty()) return@withContext emptyMap()

        val resultMap = mutableMapOf<String, String>()
        
        val combinedText = linesToTranslate.joinToString("\n")

        val requestBody = okhttp3.FormBody.Builder()
            .add("q", combinedText)
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t")
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                
                val rootArray = com.google.gson.JsonParser.parseString(body).asJsonArray
                val textBlocks = rootArray.get(0).asJsonArray

                val translatedFull = java.lang.StringBuilder()
                for (i in 0 until textBlocks.size()) {
                    translatedFull.append(textBlocks.get(i).asJsonArray.get(0).asString)
                }

                val translatedLines = translatedFull.toString().split("\n")

                for (i in 0 until minOf(linesToTranslate.size, translatedLines.size)) {
                    val original = linesToTranslate[i].trim()
                    val translated = translatedLines[i].trim()
                    if (original.isNotEmpty() && translated.isNotEmpty()) {
                        resultMap[original] = translated
                    }
                }
            }
        } catch (e: Exception) {
            println("Google Translate Error: ${e.message}")
        }
        return@withContext resultMap
    }

    suspend fun getRomanization(
        linesToTranslate: List<String>
    ): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (linesToTranslate.isEmpty()) return@withContext emptyMap()
        val resultMap = java.util.concurrent.ConcurrentHashMap<String, String>()
        
        val deferreds = linesToTranslate.map { originalLine ->
            async {
                val trimmed = originalLine.trim()
                if (trimmed.isBlank()) return@async
                try {
                    val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=rm&q=" + 
                        java.net.URLEncoder.encode(trimmed, "UTF-8")
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@async
                        val rootArray = com.google.gson.JsonParser.parseString(body).asJsonArray
                        if (rootArray.size() > 0 && rootArray.get(0).isJsonArray) {
                            val textBlocks = rootArray.get(0).asJsonArray
                            var lineRomanized = ""
                            for (i in 0 until textBlocks.size()) {
                                if (!textBlocks.get(i).isJsonArray) continue
                                val block = textBlocks.get(i).asJsonArray
                                if (block.size() > 2 && !block.get(2).isJsonNull && block.get(2).isJsonPrimitive) {
                                    lineRomanized += block.get(2).asString
                                } else if (block.size() > 3 && !block.get(3).isJsonNull && block.get(3).isJsonPrimitive) {
                                    lineRomanized += block.get(3).asString
                                }
                            }
                            val rom = lineRomanized.trim()
                            if (rom.isNotEmpty() && trimmed.lowercase() != rom.lowercase()) {
                                resultMap[trimmed] = rom
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Romanization line error: ${e.message}")
                }
            }
        }
        deferreds.awaitAll()
        return@withContext resultMap
    }
}