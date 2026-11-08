package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.drm.HlsEncryptionInfo
import com.alananasss.kittytune.data.drm.PlayReadyInfo
import com.alananasss.kittytune.data.network.ProxyManager
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Prepares encrypted fMP4 for Windows' own demuxer. Never acquires keys or decrypts media. */
internal object WindowsProtectedMedia {
    private val client by lazy { ProxyManager.getOkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build() }

    fun prepare(directory: File, url: String): JSONObject = prepare(directory, url, emptyMap())

    fun prepare(directory: File, url: String, headers: Map<String, String>): JSONObject {
        fun fetch(address: String, maximum: Int): ByteArray {
            val request = Request.Builder().url(address)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://soundcloud.com").header("Referer", "https://soundcloud.com/")
            headers.forEach { (key, value) -> request.header(key, value) }
            client.newCall(request.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Protected media HTTP ${response.code}")
                val bytes = response.body.byteStream().use { it.readNBytes(maximum + 1) }
                if (bytes.size > maximum) throw IOException("Protected media response exceeds size limit")
                return bytes
            }
        }
        var base = url
        var playlist = fetch(base, 2 * 1024 * 1024).toString(Charsets.UTF_8)
        repeat(3) {
            if (playlist.contains("#EXT-X-STREAM-INF:")) {
                val variant = playlist.lineSequence().map(String::trim).firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                    ?: throw IOException("Protected HLS variant is missing")
                base = URI(base).resolve(variant).toString()
                playlist = fetch(base, 2 * 1024 * 1024).toString(Charsets.UTF_8)
            }
        }
        val info = HlsEncryptionInfo.parsePlayReady(playlist) ?: throw IOException("PlayReady header is missing")
        val init = Regex("#EXT-X-MAP:[^\r\n]*URI=\"([^\"]+)\"").find(playlist)?.groupValues?.get(1)
            ?: throw IOException("Protected fMP4 initialization is missing")
        if (playlist.contains("#EXT-X-BYTERANGE") || playlist.contains("#EXT-X-DISCONTINUITY")) {
            throw IOException("Unsupported protected HLS fragment layout")
        }
        val segments = playlist.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        val duration = Regex("#EXTINF:([0-9.]+)").findAll(playlist).sumOf { (it.groupValues[1].toDouble() * 1000).toLong() }
        if (segments.isEmpty() || segments.size > 10000 || duration <= 0) throw IOException("Invalid protected playlist")
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        val filename = "media-$hash.mp4"
        val temporary = File(directory, "$filename.part")
        val scratch = File(directory, "fragments-${UUID.randomUUID()}").apply { mkdirs() }
        val executor = Executors.newFixedThreadPool(6) { task -> Thread(task, "KittyTune-encrypted-fragment").apply { isDaemon = true } }
        val total = AtomicLong()
        try {
            val files = (listOf(init) + segments).mapIndexed { index, relative ->
                val address = URI(base).resolve(relative).toString()
                executor.submit<File> {
                    val bytes = fetch(address, 16 * 1024 * 1024)
                    if (total.addAndGet(bytes.size.toLong()) > 512L * 1024 * 1024) throw IOException("Protected media cache limit exceeded")
                    File(scratch, index.toString()).apply { writeBytes(bytes) }
                }
            }
            temporary.outputStream().use { output -> files.forEach { pending ->
                pending.get().inputStream().use { it.copyTo(output) }
            } }
            File(directory, "initData.bin").writeBytes(initializationData(info))
            Files.move(temporary.toPath(), File(directory, filename).toPath(), StandardCopyOption.REPLACE_EXISTING)
            return JSONObject().put("mediaFile", filename).put("durationMs", duration)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(35, TimeUnit.SECONDS)
            scratch.deleteRecursively()
            temporary.delete()
        }
    }

    internal fun initializationData(info: PlayReadyInfo): ByteArray {
        require(info.algorithm == "AESCTR" && info.keyIdHex?.matches(Regex("[0-9a-f]{32}")) == true) { "Unsupported PlayReady header" }
        fun guidBase64(hex: String): String {
            val network = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val little = byteArrayOf(network[3], network[2], network[1], network[0], network[5], network[4], network[7], network[6]) + network.copyOfRange(8, 16)
            return Base64.getEncoder().encodeToString(little)
        }
        val domain = info.domainServiceId?.replace("-", "")?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }
            ?.let { "<DS_ID>${guidBase64(it)}</DS_ID>" }.orEmpty()
        // Standard EME cenc initialization, using the playlist's KID/domain. The licence server
        // still supplies and enforces all usage/output policies inside Windows' protected process.
        val xml = ("<WRMHEADER xmlns=\"http://schemas.microsoft.com/DRM/2007/03/PlayReadyHeader\" version=\"4.0.0.0\"><DATA>" +
            "<PROTECTINFO><KEYLEN>16</KEYLEN><ALGID>AESCTR</ALGID></PROTECTINFO><KID>${guidBase64(info.keyIdHex)}</KID>" +
            "<LA_URL>https://license.media-streaming.soundcloud.cloud/playback/playready</LA_URL>$domain</DATA></WRMHEADER>").toByteArray(Charsets.UTF_16LE)
        val pro = ByteBuffer.allocate(10 + xml.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(10 + xml.size).putShort(1).putShort(1).putShort(xml.size.toShort()).put(xml).array()
        val system = "9a04f07998404286ab92e65be0885f95".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ByteBuffer.allocate(32 + pro.size).putInt(32 + pro.size).put("pssh".toByteArray()).putInt(0).put(system).putInt(pro.size).put(pro).array()
    }
}
