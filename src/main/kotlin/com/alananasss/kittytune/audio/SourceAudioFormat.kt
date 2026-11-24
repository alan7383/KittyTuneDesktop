package com.alananasss.kittytune.audio

import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
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
        /** [fragmentedHlsBitrateBps] is zero for unmeasured fMP4 HLS, and null for other sources. */
        fun of(grabber: FFmpegFrameGrabber, fragmentedHlsBitrateBps: Long? = null): SourceAudioFormat? = runCatching {
            val fc = grabber.formatContext ?: return null
            for (i in 0 until fc.nb_streams()) {
                val par = fc.streams(i)?.codecpar() ?: continue
                if (par.codec_type() != avutil.AVMEDIA_TYPE_AUDIO) continue
                val name = avcodec.avcodec_get_name(par.codec_id())?.string ?: grabber.audioCodecName
                val depth = par.bits_per_raw_sample().takeIf { it > 0 && isLossless(name) }
                val bitrate = if (fragmentedHlsBitrateBps != null) {
                    // On incremental fMP4, FFmpeg may divide the bytes it probed by the end
                    // timestamp of a fragment or of the whole track. Use the fragment's duration.
                    fragmentedHlsBitrateBps.takeIf { fc.nb_streams() == 1 } ?: 0L
                } else {
                    val declared = par.bit_rate().takeIf { it > 0 } ?: grabber.audioBitrate.toLong()
                    val measured = if (fc.nb_streams() == 1 && !isLossless(name)) {
                        measuredBitrate(fc.pb()?.let { avformat.avio_size(it) } ?: -1L, fc.duration())
                    } else null
                    chooseBitrate(declared, measured)
                }
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

        /** Average bitrate of a file holding nothing but this stream: its size over its duration. */
        internal fun measuredBitrate(sizeBytes: Long, durationUs: Long): Long? {
            if (sizeBytes <= 0L || durationUs <= 0L) return null
            val bps = sizeBytes * 8L * 1_000_000L / durationUs
            return bps.takeIf { it in 8_000L..2_000_000L }
        }

        /**
         * The container's figure is what it claims, and for a fragmented MP4 (YouTube's audio, the stand-in for
         * a SoundCloud track that cannot stream) FFmpeg works it out as the first fragment's bytes over the
         * whole track's duration: "AAC 6 kbps" for a 128 kbps stream. Keep the declared nominal value while it
         * agrees with what the file actually weighs, and use the weight when it does not.
         */
        internal fun chooseBitrate(declared: Long, measured: Long?): Long = when {
            // No claim at all (Ogg): the weight also counts cover art kept in the tags, so say nothing.
            measured == null || declared <= 0L -> declared
            kotlin.math.abs(declared - measured) > measured / 4 -> measured
            else -> declared
        }

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
