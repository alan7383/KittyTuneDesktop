package com.alananasss.kittytune.data.drm

import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sample-level CENC decryption (ISO/IEC 23001-7) for the fragmented-MP4 audio SoundCloud serves as
 * `ctr-encrypted-hls`, plus the init-segment rewrite that turns a protected track back into a clear one.
 *
 * Ported from the decryptor shipped inside SoundCloud-Desktop (`decrypt-client`), whose shape is the same:
 * walk the boxes, read the per-sample IVs out of `senc`, AES-CTR the affected byte ranges, then drop the
 * encryption boxes so the demuxer below sees an ordinary `mp4a` fragment.
 *
 * Two things are easy to get wrong and are handled explicitly here:
 *  - `trun.data_offset` is relative to the enclosing `moof`. Removing `senc`/`saiz`/`saio` shrinks that
 *    `moof`, so the offset has to be lowered by exactly as much, or the demuxer reads ciphertext.
 *  - Removing `sinf` from the init segment shortens `enca`, and therefore every ancestor box up to
 *    `moov` as well. Each size is recomputed on the way out of the rewrite.
 *
 * Validated against FFmpeg's own `-decryption_key` path: decrypting a `cenc-aes-ctr` fragment with this
 * class yields sample bytes identical to the clear original (see `CencDecrypterTest`).
 */
object CencDecrypter {

    /** 8-byte UUID that identifies the Widevine key format in an HLS `#EXT-X-KEY` line. */
    const val WIDEVINE_KEY_FORMAT = "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"

    /** Scheme types (from `sinf/schm`) this decrypter can undo. */
    private const val SCHEME_CENC = "cenc"

    /** Boxes that carry the encryption description and must not reach the demuxer once samples are clear. */
    private val ENCRYPTION_BOXES = setOf("senc", "saiz", "saio")

    /**
     * How one sample's bytes are turned into plaintext, in place.
     *
     * The box walking, the sample boundaries and the rewriting are the same whichever component ends
     * up holding the key, so they are shared: a local AES-CTR implementation for tests and for a
     * key-holding CDM, and the native CDM for real playback.
     */
    fun interface SampleDecryptor {
        fun decrypt(
            buffer: ByteArray,
            offset: Int,
            size: Int,
            iv: ByteArray,
            subsamples: List<Pair<Int, Int>>?,
        )
    }

    class DrmFormatException(message: String) : Exception(message)

    class UnsupportedSchemeException(message: String) : Exception(message)

    /**
     * What the init segment says about the track's encryption.
     *
     * @param scheme `sinf/schm` scheme type, e.g. `cenc`.
     * @param ivSize `tenc` default per-sample IV size in bytes (0, 8 or 16).
     * @param keyId the `default_KID`, which is what a license is asked for.
     * @param constantIv used instead of per-sample IVs when [ivSize] is 0.
     */
    data class TrackEncryption(
        val scheme: String,
        val ivSize: Int,
        val keyId: ByteArray,
        val constantIv: ByteArray? = null,
    ) {
        val keyIdHex: String get() = IsoBmff.hex(keyId)

        override fun equals(other: Any?): Boolean =
            other is TrackEncryption &&
                scheme == other.scheme &&
                ivSize == other.ivSize &&
                keyId.contentEquals(other.keyId) &&
                (constantIv?.contentEquals(other.constantIv ?: ByteArray(0)) ?: (other.constantIv == null))

        override fun hashCode(): Int =
            ((scheme.hashCode() * 31 + ivSize) * 31 + keyId.contentHashCode()) * 31 +
                (constantIv?.contentHashCode() ?: 0)
    }

    /**
     * Reads the encryption description out of an init segment, or null when the track is not protected.
     *
     * Looks for the first sample entry that carries a `sinf` with a `tenc`, which is the one the license
     * applies to: SoundCloud's fragments hold a single audio track.
     */
    fun parseInit(init: ByteArray): TrackEncryption? {
        val moov = IsoBmff.boxes(init, 0, init.size).firstOrNull { it.type == "moov" } ?: return null
        val sampleEntry = findSampleEntry(init, moov) ?: return null
        // `enca`/`encv` keep 28 (audio) or 78 (video) bytes of sample-entry fields before their
        // children, so `sinf` is not at the box payload.
        val childStart = IsoBmff.childStart(sampleEntry.type, sampleEntry.header) ?: return null
        if (sampleEntry.start + childStart > sampleEntry.end) return null
        val sinf = IsoBmff.boxes(init, sampleEntry.start + childStart, sampleEntry.end)
            .firstOrNull { it.type == "sinf" } ?: return null

        val children = IsoBmff.boxes(init, sinf.payload, sinf.end)
        val scheme = children.firstOrNull { it.type == "schm" }
            ?.let { schm -> String(init, schm.payload + 4, 4, Charsets.US_ASCII) }
            ?: SCHEME_CENC

        val tenc = children.firstOrNull { it.type == "schi" }
            ?.let { schi -> IsoBmff.boxes(init, schi.payload, schi.end).firstOrNull { it.type == "tenc" } }
            ?: return null

        val payload = tenc.payload
        if (payload + 24 > tenc.end) throw DrmFormatException("tenc box is truncated")
        val isProtected = IsoBmff.readUint8(init, payload + 6)
        if (isProtected == 0) return null
        val ivSize = IsoBmff.readUint8(init, payload + 7)
        val keyId = init.copyOfRange(payload + 8, payload + 24)

        var constantIv: ByteArray? = null
        if (ivSize == 0 && payload + 25 <= tenc.end) {
            val constantIvSize = IsoBmff.readUint8(init, payload + 24)
            if (constantIvSize > 0 && payload + 25 + constantIvSize <= tenc.end) {
                constantIv = init.copyOfRange(payload + 25, payload + 25 + constantIvSize)
            }
        }
        return TrackEncryption(scheme.lowercase(), ivSize, keyId, constantIv)
    }

    /**
     * Rewrites an init segment so the track reads as clear: drops `sinf` from the encrypted sample entry and
     * renames it back to the format `frma` names (`enca` → `mp4a`), fixing every enclosing box size.
     *
     * Returns the input untouched when the track is not protected, so it is safe to call unconditionally.
     */
    fun clearInitSegment(init: ByteArray): ByteArray {
        val moov = IsoBmff.boxes(init, 0, init.size).firstOrNull { it.type == "moov" } ?: return init
        if (findSampleEntry(init, moov) == null) return init

        val out = ByteArrayOutputStream(init.size)
        var off = 0
        for (box in IsoBmff.boxes(init, 0, init.size)) {
            out.write(
                if (box.type == "moov") clearSampleEntry(init, box) else init.copyOfRange(box.start, box.end)
            )
            off = box.end
        }
        if (off < init.size) out.write(init, off, init.size - off)
        return out.toByteArray()
    }

    /** Decrypts every sample of a media segment with a locally held key, then strips its encryption boxes. */
    fun decryptSegment(segment: ByteArray, key: ByteArray, track: TrackEncryption): ByteArray {
        require(key.size == 16) { "content key must be 16 bytes, was ${key.size}" }
        return decryptSegment(segment, track) { buffer, offset, size, iv, subsamples ->
            ctrDecrypt(buffer, offset, size, key, iv, subsamples)
        }
    }

    /** Decrypts every sample of a media segment through [decrypt], then strips its encryption boxes. */
    fun decryptSegment(
        segment: ByteArray,
        track: TrackEncryption,
        decrypt: SampleDecryptor,
    ): ByteArray {
        if (track.scheme != SCHEME_CENC) {
            throw UnsupportedSchemeException(
                "CENC scheme '${track.scheme}' is not supported (only '$SCHEME_CENC'); " +
                    "SoundCloud's cbc-encrypted-hls is FairPlay, not Widevine"
            )
        }
        val buffer = segment.copyOf()
        val moofs = IsoBmff.boxes(buffer, 0, buffer.size).filter { it.type == "moof" }
        if (moofs.isEmpty()) throw DrmFormatException("segment has no moof box")

        var decrypted = 0
        for (moof in moofs) {
            for (traf in IsoBmff.boxes(buffer, moof.payload, moof.end).filter { it.type == "traf" }) {
                decrypted += decryptTraf(buffer, moof, traf, track, decrypt)
            }
        }
        if (decrypted == 0) throw DrmFormatException("no encrypted samples found in segment")

        val out = ByteArrayOutputStream(segment.size)
        var off = 0
        for (box in IsoBmff.boxes(buffer, 0, buffer.size)) {
            if (box.type == "moof") {
                val removed = countEncryptionBoxes(buffer, box)
                out.write(rebuild(buffer, box, removed))
            } else {
                out.write(buffer, box.start, box.size)
            }
            off = box.end
        }
        if (off < buffer.size) out.write(buffer, off, buffer.size - off)
        return out.toByteArray()
    }

    // --- init segment rewriting -------------------------------------------------------------------

    /** The encrypted sample entry (`enca`/`encv`) of the first track that has one. */
    private fun findSampleEntry(src: ByteArray, moov: IsoBox): IsoBox? {
        for (trak in IsoBmff.boxes(src, moov.payload, moov.end).filter { it.type == "trak" }) {
            val stbl = descending(src, trak, listOf("mdia", "minf", "stbl")) ?: continue
            val stsd = IsoBmff.boxes(src, stbl.payload, stbl.end).firstOrNull { it.type == "stsd" } ?: continue
            val entry = IsoBmff.boxes(src, stsd.payload + 8, stsd.end)
                .firstOrNull { it.type == "enca" || it.type == "encv" } ?: continue
            return entry
        }
        return null
    }

    /** The box reached by following [path] (types) from the children of [from]. */
    private fun descending(src: ByteArray, from: IsoBox, path: List<String>): IsoBox? {
        var current: IsoBox = from
        for (type in path) {
            current = IsoBmff.boxes(src, current.payload, current.end).firstOrNull { it.type == type }
                ?: return null
        }
        return current
    }

    /** Rebuilds `moov`, dropping `sinf` from its encrypted sample entry and renaming that entry. */
    private fun clearSampleEntry(src: ByteArray, moov: IsoBox): ByteArray {
        val out = ByteArrayOutputStream(moov.size)
        out.write(src, moov.start, moov.header)
        for (child in IsoBmff.boxes(src, moov.payload, moov.end)) {
            out.write(
                if (child.type == "trak") clearTrack(src, child) else src.copyOfRange(child.start, child.end)
            )
        }
        val bytes = out.toByteArray()
        IsoBmff.resize(bytes, 0, moov.header, bytes.size)
        return bytes
    }

    private fun clearTrack(src: ByteArray, trak: IsoBox): ByteArray {
        val out = ByteArrayOutputStream(trak.size)
        out.write(src, trak.start, trak.header)
        for (child in IsoBmff.boxes(src, trak.payload, trak.end)) {
            val rebuilt = when (child.type) {
                "mdia" -> rebuildPath(src, child, listOf("minf", "stbl", "stsd"), ::clearStsd)
                else -> src.copyOfRange(child.start, child.end)
            }
            out.write(rebuilt)
        }
        val bytes = out.toByteArray()
        IsoBmff.resize(bytes, 0, trak.header, bytes.size)
        return bytes
    }

    /**
     * Rebuilds the chain of single-child containers named by [path], handing the innermost box to [leaf].
     * Every container's size is recomputed, which is what makes the shrink propagate up to `moov`.
     */
    private fun rebuildPath(
        src: ByteArray,
        from: IsoBox,
        path: List<String>,
        leaf: (ByteArray, IsoBox) -> ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(from.size)
        out.write(src, from.start, from.header)
        val wanted = path.firstOrNull()
        for (child in IsoBmff.boxes(src, from.payload, from.end)) {
            val rebuilt = when {
                wanted == null || child.type != wanted -> src.copyOfRange(child.start, child.end)
                path.size == 1 -> leaf(src, child)
                else -> rebuildPath(src, child, path.drop(1), leaf)
            }
            out.write(rebuilt)
        }
        val bytes = out.toByteArray()
        IsoBmff.resize(bytes, 0, from.header, bytes.size)
        return bytes
    }

    /** Clears the sample entry inside a rebuilt `stsd`. */
    private fun clearStsd(src: ByteArray, stsd: IsoBox): ByteArray {
        val out = ByteArrayOutputStream(stsd.size)
        out.write(src, stsd.start, stsd.header + 8) // header + version/flags + entry_count
        for (entry in IsoBmff.boxes(src, stsd.payload + 8, stsd.end)) {
            out.write(
                if (entry.type == "enca" || entry.type == "encv") clearEntry(src, entry)
                else src.copyOfRange(entry.start, entry.end)
            )
        }
        val bytes = out.toByteArray()
        IsoBmff.resize(bytes, 0, stsd.header, bytes.size)
        return bytes
    }

    /** Turns `enca` into `mp4a` (or `encv` into its `frma` format) and drops the `sinf` box. */
    private fun clearEntry(src: ByteArray, entry: IsoBox): ByteArray {
        val childStart = IsoBmff.childStart(entry.type, entry.header)
            ?: throw DrmFormatException("${entry.type} box is too short for a sample entry")
        if (entry.start + childStart > entry.end) throw DrmFormatException("${entry.type} box is truncated")

        val children = IsoBmff.boxes(src, entry.start + childStart, entry.end)
        val sinf = children.firstOrNull { it.type == "sinf" }
        val originalFormat = sinf?.let { box ->
            IsoBmff.boxes(src, box.payload, box.end).firstOrNull { it.type == "frma" }
                ?.let { frma -> String(src, frma.payload, 4, Charsets.US_ASCII) }
        } ?: if (entry.type == "enca") "mp4a" else "avc1"

        val out = ByteArrayOutputStream(entry.size)
        out.write(src, entry.start, childStart)
        for (child in children) {
            if (child.type == "sinf") continue
            out.write(src, child.start, child.size)
        }
        val bytes = out.toByteArray()
        bytes[4] = originalFormat[0].code.toByte()
        bytes[5] = originalFormat[1].code.toByte()
        bytes[6] = originalFormat[2].code.toByte()
        bytes[7] = originalFormat[3].code.toByte()
        IsoBmff.resize(bytes, 0, entry.header, bytes.size)
        return bytes
    }

    // --- media segment decryption -----------------------------------------------------------------

    private class SampleRun(
        val dataStart: Int,
        val sizes: IntArray,
        val ivs: List<ByteArray?>,
        val subsamples: List<List<Pair<Int, Int>>?>,
    )

    /** Decrypts one `traf`'s samples in place, returning how many samples were handled. */
    private fun decryptTraf(
        buffer: ByteArray,
        moof: IsoBox,
        traf: IsoBox,
        track: TrackEncryption,
        decrypt: SampleDecryptor,
    ): Int {
        val children = IsoBmff.boxes(buffer, traf.payload, traf.end)
        val tfhd = children.firstOrNull { it.type == "tfhd" }
            ?: throw DrmFormatException("traf without tfhd")
        val truns = children.filter { it.type == "trun" }
        if (truns.isEmpty()) throw DrmFormatException("traf without trun")

        val tfhdFlags = IsoBmff.readUint32(buffer, tfhd.payload).toInt() and 0xFFFFFF
        var p = tfhd.payload + 4 + 4 // version/flags + track_ID
        if (p > tfhd.end) throw DrmFormatException("tfhd box is truncated")
        var baseDataOffset = -1L
        if (tfhdFlags and 0x000001 != 0) {
            if (p + 8 > tfhd.end) throw DrmFormatException("tfhd box is truncated")
            baseDataOffset = IsoBmff.readUint64(buffer, p)
            p += 8
        }
        if (tfhdFlags and 0x000002 != 0) p += 4
        var defaultSampleSize = 0
        if (tfhdFlags and 0x000010 != 0) {
            if (p + 4 > tfhd.end) throw DrmFormatException("tfhd box is truncated")
            defaultSampleSize = IsoBmff.readUint32(buffer, p).toInt()
            p += 4
        }

        val senc = children.firstOrNull { it.type == "senc" }
            ?: throw DrmFormatException("protected traf has no senc box")
        val (ivs, subsamples) = parseSenc(buffer, senc, track)

        // Samples of all track runs in this traf share one senc entry list, in order.
        var sampleIndex = 0
        var runningEnd = -1
        var decrypted = 0
        for (trun in truns) {
            val flags = IsoBmff.readUint32(buffer, trun.payload).toInt() and 0xFFFFFF
            if (trun.payload + 8 > trun.end) throw DrmFormatException("trun box is truncated")
            val count = IsoBmff.readUint32(buffer, trun.payload + 4).toInt()
            var q = trun.payload + 8
            var dataOffset = 0
            if (flags and 0x000001 != 0) {
                if (q + 4 > trun.end) throw DrmFormatException("trun box is truncated")
                dataOffset = IsoBmff.readInt32(buffer, q)
                q += 4
            }
            if (flags and 0x000004 != 0) q += 4 // first_sample_flags

            val base = if (baseDataOffset >= 0) baseDataOffset.toInt() else moof.start
            var cursor = when {
                flags and 0x000001 != 0 -> base + dataOffset
                runningEnd >= 0 -> runningEnd
                else -> base
            }

            for (i in 0 until count) {
                if (flags and 0x000100 != 0) q += 4 // sample_duration
                val size = if (flags and 0x000200 != 0) {
                    if (q + 4 > trun.end) throw DrmFormatException("trun box is truncated")
                    val v = IsoBmff.readUint32(buffer, q).toInt()
                    q += 4
                    v
                } else {
                    defaultSampleSize
                }
                if (flags and 0x000400 != 0) q += 4 // sample_flags
                if (flags and 0x000800 != 0) q += 4 // sample_composition_time_offset

                if (size < 0 || cursor < 0 || cursor + size > buffer.size) {
                    throw DrmFormatException(
                        "sample $sampleIndex falls outside the segment (offset=$cursor size=$size)"
                    )
                }
                if (sampleIndex >= ivs.size) {
                    throw DrmFormatException("senc has ${ivs.size} entries but the traf has more samples")
                }
                decrypt.decrypt(
                    buffer,
                    cursor,
                    size,
                    ivs[sampleIndex] ?: track.constantIv ?: ByteArray(0),
                    subsamples[sampleIndex],
                )
                cursor += size
                sampleIndex++
                decrypted++
            }
            runningEnd = cursor
        }
        return decrypted
    }

    /** Reads `senc`: per-sample IVs and, when the flags say so, the subsample ranges. */
    private fun parseSenc(
        src: ByteArray,
        senc: IsoBox,
        track: TrackEncryption,
    ): Pair<List<ByteArray?>, List<List<Pair<Int, Int>>?>> {
        if (senc.payload + 8 > senc.end) throw DrmFormatException("senc box is truncated")
        val flags = IsoBmff.readUint32(src, senc.payload).toInt() and 0xFFFFFF
        val count = IsoBmff.readUint32(src, senc.payload + 4).toInt()
        val hasSubsamples = flags and 0x000002 != 0
        val ivSize = track.ivSize.coerceIn(0, 16)

        var p = senc.payload + 8
        val ivs = ArrayList<ByteArray?>(count)
        val subsamples = ArrayList<List<Pair<Int, Int>>?>(count)
        for (i in 0 until count) {
            if (ivSize == 0) {
                ivs.add(null)
            } else {
                if (p + ivSize > senc.end) throw DrmFormatException("senc box is truncated")
                ivs.add(src.copyOfRange(p, p + ivSize))
                p += ivSize
            }
            if (hasSubsamples) {
                if (p + 2 > senc.end) throw DrmFormatException("senc box is truncated")
                val entries = IsoBmff.readUint16(src, p)
                p += 2
                val ranges = ArrayList<Pair<Int, Int>>(entries)
                for (e in 0 until entries) {
                    if (p + 6 > senc.end) throw DrmFormatException("senc box is truncated")
                    val clear = IsoBmff.readUint16(src, p)
                    val protected = IsoBmff.readUint32(src, p + 2).toInt()
                    p += 6
                    ranges.add(clear to protected)
                }
                subsamples.add(ranges)
            } else {
                subsamples.add(null)
            }
        }
        return ivs to subsamples
    }

    /** AES-CTR decrypts one sample (or its subsample ranges) in place. */
    private fun ctrDecrypt(
        buffer: ByteArray,
        offset: Int,
        size: Int,
        key: ByteArray,
        iv: ByteArray,
        subsamples: List<Pair<Int, Int>>?,
    ) {
        val counter = ByteArray(16)
        System.arraycopy(iv, 0, counter, 0, minOf(iv.size, 16))
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(counter))

        if (subsamples == null) {
            val plain = cipher.doFinal(buffer, offset, size)
            System.arraycopy(plain, 0, buffer, offset, size)
            return
        }
        // The counter runs continuously across a sample's encrypted ranges; clear bytes do not consume
        // keystream, so they are skipped rather than passed to the cipher.
        var cursor = offset
        val end = offset + size
        for ((clear, encrypted) in subsamples) {
            cursor += clear
            if (encrypted <= 0) continue
            if (cursor + encrypted > end) throw DrmFormatException("subsample runs past the sample")
            val plain = cipher.update(buffer, cursor, encrypted)
            if (plain != null) System.arraycopy(plain, 0, buffer, cursor, plain.size)
            cursor += encrypted
        }
        cipher.doFinal()
    }

    // --- segment rewriting ------------------------------------------------------------------------

    /** Bytes taken by the encryption boxes under [parent], which the rewrite removes. */
    private fun countEncryptionBoxes(src: ByteArray, parent: IsoBox): Int {
        var removed = 0
        for (child in IsoBmff.boxes(src, parent.payload, parent.end)) {
            if (child.type in ENCRYPTION_BOXES) {
                removed += child.size
            } else if (IsoBmff.childStart(child.type, child.header) != null) {
                removed += countEncryptionBoxes(src, child)
            }
        }
        return removed
    }

    /**
     * Rebuilds [box] without the encryption boxes, correcting the size of every container on the way and
     * lowering each `trun` data offset by [removed] (the data they point at moved that much earlier).
     */
    private fun rebuild(src: ByteArray, box: IsoBox, removed: Int): ByteArray {
        if (box.type == "trun") {
            val copy = src.copyOfRange(box.start, box.end)
            val flags = IsoBmff.readUint32(copy, box.header).toInt() and 0xFFFFFF
            if (flags and 0x000001 != 0 && removed != 0) {
                val offset = IsoBmff.readInt32(copy, box.header + 8)
                IsoBmff.putUint32(copy, box.header + 8, offset - removed)
            }
            return copy
        }
        val childStart = IsoBmff.childStart(box.type, box.header)
            ?: return src.copyOfRange(box.start, box.end)
        if (box.start + childStart > box.end) return src.copyOfRange(box.start, box.end)

        val out = ByteArrayOutputStream(box.size)
        out.write(src, box.start, childStart)
        for (child in IsoBmff.boxes(src, box.start + childStart, box.end)) {
            if (child.type in ENCRYPTION_BOXES) continue
            out.write(rebuild(src, child, removed))
        }
        val bytes = out.toByteArray()
        IsoBmff.resize(bytes, 0, box.header, bytes.size)
        return bytes
    }
}
