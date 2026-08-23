package com.alananasss.kittytune.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class LrcLibResponse(
    val id: Long,
    val name: String,
    @SerializedName("artistName") val artistName: String,
    @SerializedName("albumName") val albumName: String?,
    @SerializedName("duration") val duration: Double,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?,
    @SerializedName("lyricsfile") val lyricsfile: String?
)

interface LrcLibApiService {
    @GET("get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") duration: Long
    ): LrcLibResponse

    @GET("search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibResponse>
}

object LrcLibClient {
    private const val BASE_URL = "https://lrclib.net/api/"

    private val baseHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "KittyTuneWindows/1.0 (https://github.com/alan7383/KittyTuneDesktop)")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val okHttpClient: okhttp3.OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseHttpClient.newBuilder()).build()

    val api: LrcLibApiService
        get() = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApiService::class.java)
}
