package com.alananasss.kittytune.audio

import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.util.Locale

/**
 * What the decoder is actually reading, for the codec badge and the audio-path diagnostics: the stream's own
 * codec, rate, depth, bitrate and channels, before the engine converts it to its 44.1 kHz stereo output.
 */
data class SourceAudioFormat(
    /** Display name of the codec: "AAC", "MP3", "OPUS", "FLAC"... */
    val codec: String,
    val sampleRateHz: Int,
    /** Bits per sample of a lossless stream; null for lossy codecs, which have no fixed depth. */
    val bitDepth: Int?,
    val bitrateBps: Long,
    val channels: Int,
) {
    /** "OPUS • 160 kbps • 48 kHz", as the badge shows it. */
    fun badgeText(): String = listOfNotNull(
        codec.takeIf { it.isNotBlank() },
        (bitrateBps / 1000).takeIf { it > 0 }?.let { "$it kbps" },
        sampleRateHz.takeIf { it > 0 }?.let { khzCompact(it) + " kHz" },
    ).joinToString(" • ")

    companion object {
        /** Reads the opened grabber's first audio stream; null when it has none. */
        fun of(grabber: FFmpegFrameGrabber): SourceAudioFormat? = runCatching {
            val fc = grabber.formatContext ?: return null
            for (i in 0 until fc.nb_streams()) {
                val par = fc.streams(i)?.codecpar() ?: continue
                if (par.codec_type() != avutil.AVMEDIA_TYPE_AUDIO) continue
                val name = avcodec.avcodec_get_name(par.codec_id())?.string ?: grabber.audioCodecName
                val depth = par.bits_per_raw_sample().takeIf { it > 0 && isLossless(name) }
                val bitrate = par.bit_rate().takeIf { it > 0 } ?: grabber.audioBitrate.toLong()
                return SourceAudioFormat(
                    codec = codecLabel(name),
                    sampleRateHz = par.sample_rate(),
                    bitDepth = depth,
                    bitrateBps = bitrate,
                    channels = par.ch_layout().nb_channels(),
                )
            }
            null
        }.getOrNull()

        internal fun codecLabel(ffmpegName: String?): String {
            val n = ffmpegName?.lowercase(Locale.US).orEmpty()
            return when {
                n.isEmpty() -> "PCM"
                n.startsWith("pcm_") -> "PCM"
                n == "mp3" || n == "mp3float" -> "MP3"
                n == "aac" || n == "aac_latm" -> "AAC"
                n == "ac3" -> "AC-3"
                n == "eac3" -> "E-AC-3"
                else -> n.uppercase(Locale.US)
            }
        }

        private fun isLossless(name: String?): Boolean {
            val n = name?.lowercase(Locale.US).orEmpty()
            return n.startsWith("pcm_") || n in setOf("flac", "alac", "wavpack", "ape", "tta", "mlp", "truehd")
        }

        internal fun khzCompact(rate: Int): String {
            val v = rate / 1000.0
            return if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
        }
    }
}
