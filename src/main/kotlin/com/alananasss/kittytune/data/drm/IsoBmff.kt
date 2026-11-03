package com.alananasss.kittytune.data.drm

/**
 * Minimal ISOBMFF (ISO/IEC 14496-12) box walking, enough to reach the CENC boxes and to rewrite a
 * fragment without disturbing anything else.
 *
 * Only the pieces the DRM path needs are here: no sample tables, no edit lists, no codec parsing.
 * Every read is bounds-checked, because the input is a fragment fetched over the network and a
 * malformed size field must not turn into an [IndexOutOfBoundsException] deep inside the player.
 */
internal class IsoBox(val start: Int, val size: Int, val header: Int, val type: String) {
    val end: Int get() = start + size

    /** First byte after the box header, where its payload (or its children) begin. */
    val payload: Int get() = start + header
}

internal object IsoBmff {

    /** Containers whose payload is nothing but child boxes. */
    private val PLAIN_CONTAINERS = setOf(
        "moov", "trak", "mdia", "minf", "stbl", "mvex", "moof", "traf", "sinf", "schi", "edts", "dinf"
    )

    /** Width of an AudioSampleEntry header, which sits between `enca`/`mp4a` and their children. */
    private const val AUDIO_SAMPLE_ENTRY_HEADER = 28

    /** Width of a VisualSampleEntry header, between `encv`/`avc1` and their children. */
    private const val VIDEO_SAMPLE_ENTRY_HEADER = 78

    fun boxes(src: ByteArray, from: Int, to: Int): List<IsoBox> {
        if (from < 0 || to > src.size || from >= to) return emptyList()
        val out = ArrayList<IsoBox>(8)
        var off = from
        while (off + 8 <= to) {
            val box = boxAt(src, off, to) ?: break
            out.add(box)
            off = box.end
        }
        return out
    }

    fun boxAt(src: ByteArray, off: Int, to: Int): IsoBox? {
        if (off + 8 > to) return null
        var size = readUint32(src, off).toLong()
        val type = String(src, off + 4, 4, Charsets.US_ASCII)
        var header = 8
        if (size == 1L) {
            if (off + 16 > to) return null
            size = readUint64(src, off + 8)
            header = 16
        } else if (size == 0L) {
            size = (to - off).toLong()
        }
        // A size that underflows its own header, runs past the buffer, or exceeds Int range is
        // corrupt: stop rather than pretend the rest of the fragment is boxes.
        if (size < header || size > Int.MAX_VALUE || off + size > to) return null
        return IsoBox(off, size.toInt(), header, type)
    }

    /** Offset of the first child box inside [type]'s payload, or null when it holds payload bytes. */
    fun childStart(type: String, header: Int): Int? = when {
        type in PLAIN_CONTAINERS -> header
        type == "enca" -> header + AUDIO_SAMPLE_ENTRY_HEADER
        type == "encv" -> header + VIDEO_SAMPLE_ENTRY_HEADER
        // A SampleDescriptionBox carries a version/flags word and an entry count before its entries.
        type == "stsd" -> header + 8
        else -> null
    }

    fun readUint8(src: ByteArray, off: Int): Int = src[off].toInt() and 0xFF

    fun readUint16(src: ByteArray, off: Int): Int =
        ((src[off].toInt() and 0xFF) shl 8) or (src[off + 1].toInt() and 0xFF)

    fun readUint32(src: ByteArray, off: Int): Long =
        ((src[off].toLong() and 0xFF) shl 24) or
            ((src[off + 1].toLong() and 0xFF) shl 16) or
            ((src[off + 2].toLong() and 0xFF) shl 8) or
            (src[off + 3].toLong() and 0xFF)

    fun readInt32(src: ByteArray, off: Int): Int = readUint32(src, off).toInt()

    fun readUint64(src: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (src[off + i].toLong() and 0xFF)
        return v
    }

    fun putUint32(dst: ByteArray, off: Int, value: Int) {
        dst[off] = ((value ushr 24) and 0xFF).toByte()
        dst[off + 1] = ((value ushr 16) and 0xFF).toByte()
        dst[off + 2] = ((value ushr 8) and 0xFF).toByte()
        dst[off + 3] = (value and 0xFF).toByte()
    }

    fun putUint64(dst: ByteArray, off: Int, value: Long) {
        for (i in 0 until 8) dst[off + i] = ((value ushr (56 - 8 * i)) and 0xFF).toByte()
    }

    /** Writes [newSize] into a box header, honouring the 64-bit form when the box already uses it. */
    fun resize(dst: ByteArray, off: Int, header: Int, newSize: Int) {
        if (header == 16) {
            putUint32(dst, off, 1)
            putUint64(dst, off + 8, newSize.toLong())
        } else {
            putUint32(dst, off, newSize)
        }
    }

    fun hex(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): String {
        val sb = StringBuilder((to - from) * 2)
        for (i in from until to) sb.append(Character.forDigit((bytes[i].toInt() shr 4) and 0xF, 16))
            .append(Character.forDigit(bytes[i].toInt() and 0xF, 16))
        return sb.toString()
    }

    fun unhex(text: String): ByteArray? {
        val clean = text.removePrefix("0x").removePrefix("0X").filter { !it.isWhitespace() }
        if (clean.length % 2 != 0 || clean.isEmpty()) return null
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
