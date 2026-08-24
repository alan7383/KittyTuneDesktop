package com.alananasss.kittytune.core

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Writers for the two native multi-resolution icon containers, so a variant PNG can be
 * turned into something Windows and macOS accept everywhere they show an icon.
 *
 * Both formats are containers of independently-sized images, and both platforms pick the
 * entry closest to the size they need. Shipping a single large entry and letting the OS
 * downscale is what makes an icon look muddy in a 16 px taskbar slot, so every entry is
 * rendered separately here, by progressive halving rather than one big jump.
 */
object IconEncoders {

    /** Entry sizes Windows looks for. 256 is the largest an ICO directory can name. */
    private val ICO_SIZES = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /**
     * ICNS chunk types, each of which must hold exactly one pixel size. The @2x types
     * (ic11..ic14) are what Retina displays read, so they are not optional.
     */
    private val ICNS_CHUNKS = listOf(
        "icp4" to 16,   // 16x16
        "icp5" to 32,   // 32x32
        "ic11" to 32,   // 16x16@2x
        "ic12" to 64,   // 32x32@2x
        "ic07" to 128,  // 128x128
        "ic13" to 256,  // 128x128@2x
        "ic08" to 256,  // 256x256
        "ic14" to 512,  // 256x256@2x
        "ic09" to 512,  // 512x512
    )

    fun decode(bytes: ByteArray): BufferedImage? =
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    fun png(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    /**
     * Renders [source] into a square [size] canvas, preserving aspect and centring.
     *
     * Halves repeatedly before the last step: a single bicubic jump from 512 to 16 aliases
     * badly, which is exactly what the small entries exist to avoid.
     */
    fun scaled(source: BufferedImage, size: Int): BufferedImage {
        var current = source
        while (current.width / 2 >= size && current.height / 2 >= size) {
            current = render(current, current.width / 2, current.height / 2, current.width / 2)
        }
        return render(current, size, size, size)
    }

    private fun render(source: BufferedImage, boxW: Int, boxH: Int, canvasSize: Int): BufferedImage {
        val scale = minOf(boxW.toDouble() / source.width, boxH.toDouble() / source.height)
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        val canvas = BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(source, (canvasSize - w) / 2, (canvasSize - h) / 2, w, h, null)
        g.dispose()
        return canvas
    }

    /**
     * Multi-resolution Windows ICO.
     *
     * Entries up to 128 px go in as uncompressed DIBs, which every shell version renders;
     * 256 px goes in as PNG, which Vista and later read and which keeps the file small.
     */
    fun ico(source: BufferedImage): ByteArray {
        val sizes = entrySizes(ICO_SIZES.toList(), source)
        val payloads = sizes.map { size ->
            val image = scaled(source, size)
            size to if (size >= 256) png(image) else dib(image)
        }

        val out = ByteArrayOutputStream()
        // ICONDIR: reserved, type 1 (icon), entry count
        le16(out, 0); le16(out, 1); le16(out, payloads.size)

        var offset = 6 + 16 * payloads.size
        for ((size, data) in payloads) {
            // 0 in the width/height byte means 256 — the field is one byte wide.
            out.write(if (size >= 256) 0 else size)
            out.write(if (size >= 256) 0 else size)
            out.write(0)          // palette size, 0 for true colour
            out.write(0)          // reserved
            le16(out, 1)          // colour planes
            le16(out, 32)         // bits per pixel
            le32(out, data.size)
            le32(out, offset)
            offset += data.size
        }
        payloads.forEach { out.write(it.second) }
        return out.toByteArray()
    }

    /** Apple ICNS: a magic, a big-endian total length, then one typed chunk per size. */
    fun icns(source: BufferedImage): ByteArray {
        val usable = ICNS_CHUNKS.filter { it.second <= longestSide(source) }
            .ifEmpty { listOf("icp5" to 32) }

        val body = ByteArrayOutputStream()
        val rendered = HashMap<Int, ByteArray>()
        for ((type, size) in usable) {
            val data = rendered.getOrPut(size) { png(scaled(source, size)) }
            body.write(type.toByteArray(Charsets.US_ASCII))
            be32(body, data.size + 8)   // chunk length counts its own 8-byte header
            body.write(data)
        }

        val out = ByteArrayOutputStream()
        out.write("icns".toByteArray(Charsets.US_ASCII))
        be32(out, 8 + body.size())
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    private fun longestSide(image: BufferedImage) = max(image.width, image.height)

    /** Never upscales: only sizes the source can actually fill, plus its own size. */
    private fun entrySizes(candidates: List<Int>, source: BufferedImage): List<Int> {
        val srcMax = longestSide(source)
        val fits = candidates.filter { it <= srcMax }
        if (fits.isNotEmpty()) return fits
        return listOf(srcMax.coerceAtMost(candidates.max()))
    }

    /**
     * The DIB payload of an ICO entry: a BITMAPINFOHEADER, bottom-up BGRA rows, then the
     * 1-bit AND mask. 32-bit entries are keyed off the alpha channel, so the mask is left
     * zeroed — but it has to be present, and biHeight has to be doubled to account for it,
     * or the entry is malformed.
     */
    private fun dib(image: BufferedImage): ByteArray {
        val w = image.width
        val h = image.height
        val maskStride = ((w + 31) / 32) * 4
        val pixels = w * h * 4
        val out = ByteArrayOutputStream(40 + pixels + maskStride * h)

        le32(out, 40)                       // header size
        le32(out, w)
        le32(out, h * 2)                    // colour data + mask
        le16(out, 1)                        // planes
        le16(out, 32)                       // bits per pixel
        le32(out, 0)                        // BI_RGB, uncompressed
        le32(out, pixels + maskStride * h)  // biSizeImage
        le32(out, 0); le32(out, 0)          // pixels per metre
        le32(out, 0); le32(out, 0)          // palette entries

        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val argb = image.getRGB(x, y)
                out.write(argb and 0xFF)             // blue
                out.write((argb ushr 8) and 0xFF)    // green
                out.write((argb ushr 16) and 0xFF)   // red
                out.write((argb ushr 24) and 0xFF)   // alpha
            }
        }
        repeat(maskStride * h) { out.write(0) }
        return out.toByteArray()
    }

    private fun le16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
    }

    private fun le32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
    }

    private fun be32(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
    }
}
