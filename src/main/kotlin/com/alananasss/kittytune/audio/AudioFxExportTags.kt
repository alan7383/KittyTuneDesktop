package com.alananasss.kittytune.audio

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Gives an exported file what a downloaded one has: title, artist and cover, so it shows up properly in
 * any player. Best effort: a missing cover or an unwritable tag leaves the audio as it is.
 */
internal object AudioFxExportTags {

    fun write(file: File, track: Track, effects: AudioEffectsState) {
        if (file.extension.equals("wav", ignoreCase = true)) return
        runCatching {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            track.title?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TITLE, it) }
            track.displayArtist.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ARTIST, it) }
            tag.setField(FieldKey.COMMENT, "KittyTune FX · ${speedLabel(effects)}")
            artwork(track)?.let { bytes ->
                val art = ArtworkFactory.getNew().apply {
                    binaryData = bytes
                    mimeType = if (bytes.size > 3 && bytes[0] == 0x89.toByte()) "image/png" else "image/jpeg"
                    pictureType = 3
                }
                tag.deleteArtworkField()
                tag.setField(art)
            }
            audio.commit()
        }
    }

    private fun speedLabel(effects: AudioEffectsState): String =
        "${"%.2f".format(java.util.Locale.US, effects.speed).trimEnd('0').trimEnd('.')}x" +
            if (effects.isPitchEnabled) "" else " (pitch kept)"

    private fun artwork(track: Track): ByteArray? = runCatching {
        val url = track.fullResArtwork.takeIf { it.isNotBlank() } ?: track.artworkUrl ?: return null
        val local = File(url.removePrefix("file://"))
        if (local.isFile) return local.readBytes()
        if (!url.startsWith("http")) return null
        val client = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient().newBuilder()
            .callTimeout(10, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()?.takeIf { it.size in 1..8_000_000 }
        }
    }.getOrNull()
}
