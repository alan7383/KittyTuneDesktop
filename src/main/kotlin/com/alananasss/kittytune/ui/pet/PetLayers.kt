package com.alananasss.kittytune.ui.pet

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The pixel sprites, from the atlas built by scripts/pet/import_android_pet.py out of the Android
 * vectors. Every sprite is decoded to ARGB pixels on first use and kept: a frame is ~6 KB and
 * only the ones actually shown are ever decoded.
 */
internal object PetSprites {
    const val W = 46
    const val H = 32

    class Sprite(val width: Int, val height: Int, val pixels: IntArray)

    private val bytes: ByteBuffer by lazy {
        val raw = PetSprites::class.java.getResourceAsStream("/pet/pet_sprites.bin")!!.use { it.readBytes() }
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    }

    /** Sprite name -> offset of its header in [bytes]. */
    private val index: Map<String, Int> by lazy {
        val b = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { b.get(it) }.toString(Charsets.US_ASCII)
        check(magic == "KPS1") { "bad pet atlas" }
        val count = b.short.toInt() and 0xFFFF
        val out = HashMap<String, Int>(count * 2)
        repeat(count) {
            val nameLen = b.get().toInt() and 0xFF
            val name = ByteArray(nameLen).also { b.get(it) }.toString(Charsets.US_ASCII)
            out[name] = b.position()
            b.get(); b.get() // w, h
            val paths = b.short.toInt() and 0xFFFF
            repeat(paths) {
                b.int
                val rects = b.short.toInt() and 0xFFFF
                b.position(b.position() + rects * 4)
            }
        }
        out
    }

    private val decoded = java.util.concurrent.ConcurrentHashMap<String, Sprite>()

    fun has(name: String) = name in index

    operator fun get(name: String?): Sprite? {
        if (name == null) return null
        decoded[name]?.let { return it }
        val offset = index[name] ?: return null
        val b = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        b.position(offset)
        val w = b.get().toInt() and 0xFF
        val h = b.get().toInt() and 0xFF
        val px = IntArray(w * h)
        val paths = b.short.toInt() and 0xFFFF
        repeat(paths) {
            val argb = b.int
            val rects = b.short.toInt() and 0xFFFF
            repeat(rects) {
                val x = b.get().toInt() and 0xFF
                val y = b.get().toInt() and 0xFF
                val rw = b.get().toInt() and 0xFF
                val rh = b.get().toInt() and 0xFF
                for (yy in y until minOf(h, y + rh)) for (xx in x until minOf(w, x + rw)) {
                    px[yy * w + xx] = over(argb, px[yy * w + xx])
                }
            }
        }
        return Sprite(w, h, px).also { decoded[name] = it }
    }

    /** [src] drawn over [dst] (SrcOver, non premultiplied ARGB). */
    fun over(src: Int, dst: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        if (da == 0) return src
        val outA = sa + da * (255 - sa) / 255
        fun ch(shift: Int): Int {
            val s = src shr shift and 0xFF
            val d = dst shr shift and 0xFF
            return ((s * sa + d * da * (255 - sa) / 255) / outA.coerceAtLeast(1)).coerceIn(0, 255)
        }
        return (outA shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/**
 * A layer of a pet frame: a sprite, or a pixel mask for custom coats, and the tint to apply to it
 * (null = draw as is). No sprite and no mask means "nothing on this layer".
 */
internal data class PetLayer(val sprite: String?, val tint: Int?, val mask: IntArray? = null) {
    override fun equals(other: Any?) = other is PetLayer && other.sprite == sprite && other.tint == tint && other.mask === mask
    override fun hashCode() = (sprite?.hashCode() ?: 0) * 31 + (tint ?: 0) + System.identityHashCode(mask)
}

internal object PetLayers {
    const val COUNT = 8

    /** The 8 stacked layers of [frame] for this look: fur, shade, marks, white, eyes, ink, hat, neck. */
    fun of(frame: String, s: PetSettings): List<PetLayer> {
        val f = PetFrames.frames.getValue(frame)
        val p = s.palette
        // the user's accessory always wins; otherwise the season may lend one (beanie, Santa hat…)
        val hatId = if (s.hat != PetHat.NONE) s.hat.id else PetBrain.seasonalHat(s)
        val neckId = if (s.neck != PetNeck.NONE) s.neck.id else PetBrain.seasonalNeck(s)
        val hat = if (f.hatOk && hatId != null) PetFrames.hats[hatId]?.getOrNull(f.accKey) else null
        val neck = if (f.neckOk && neckId != null) PetFrames.necks[neckId]?.getOrNull(f.accKey) else null
        val markLayer: PetLayer
        val whiteLayer: PetLayer
        if (s.pattern == PetPattern.CUSTOM && s.coat != null) {
            val (m, w) = PetCoatRenderer.layers(frame, s.coat)
            markLayer = PetLayer(null, p.mark, m)
            whiteLayer = PetLayer(null, p.white, w)
        } else {
            val pi = s.pattern.ordinal.coerceAtMost(f.marks.size - 1)
            markLayer = PetLayer(f.marks[pi], p.mark)
            whiteLayer = PetLayer(f.whites[pi], p.white)
        }
        return listOf(
            PetLayer(f.fur, p.fur),
            PetLayer(f.shade, p.shade),
            markLayer,
            whiteLayer,
            PetLayer(f.eye, s.eyeColor),
            PetLayer(f.ink, null),
            PetLayer(hat, null),
            PetLayer(neck, null),
        )
    }

    /** Wall colour behind the room for [room], honouring the user's wall style (null = Material You). */
    fun wallColor(room: String, s: PetSettings): Long? = when (s.wall) {
        PetWall.AUTO -> PetFrames.wallColors.getValue(room)
        PetWall.MATERIAL -> null
        else -> s.wall.color
    }

    fun isDark(color: Long): Boolean {
        val r = (color shr 16 and 0xFF) / 255.0
        val g = (color shr 8 and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.45
    }
}

/**
 * Turns layers into one 46×32 picture. Coat layers are white and tinted at draw time (SrcAtop, as
 * the Android ColorFilter did), everything else is drawn as is, all composed with SrcOver.
 */
internal object PetComposer {

    private val cache = object : LinkedHashMap<Any, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, ImageBitmap>?) = size > 160
    }

    private fun drawLayer(out: IntArray, pixels: IntArray?, tint: Int?) {
        if (pixels == null) return
        for (i in out.indices) {
            val src = pixels.getOrNull(i) ?: break
            val a = src ushr 24
            if (a == 0) continue
            val c = if (tint != null) (a shl 24) or (tint and 0xFFFFFF) else src
            out[i] = PetSprites.over(c, out[i])
        }
    }

    /** The cat alone (transparent around it), for [frame] and this look. */
    fun cat(frame: String, s: PetSettings): IntArray {
        val out = IntArray(PetSprites.W * PetSprites.H)
        for (layer in PetLayers.of(frame, s)) {
            drawLayer(out, layer.mask ?: PetSprites[layer.sprite]?.pixels, layer.tint)
        }
        return out
    }

    /** Room + seasonal decor + cat, as an image ready to scale up with nearest-neighbour filtering. */
    @Synchronized
    fun scene(frame: String, s: PetSettings, room: String?, decor: String?): ImageBitmap {
        val key = listOf(frame, s, room, decor)
        cache[key]?.let { return it }
        val out = IntArray(PetSprites.W * PetSprites.H)
        if (room != null) drawLayer(out, PetSprites[PetFrames.rooms[room]]?.pixels, null)
        if (decor != null) drawLayer(out, PetSprites[PetFrames.decor[decor]]?.pixels, null)
        val cat = cat(frame, s)
        for (i in out.indices) if (cat[i] ushr 24 != 0) out[i] = PetSprites.over(cat[i], out[i])
        return toImage(out, PetSprites.W, PetSprites.H).also { cache[key] = it }
    }

    /** A small pixel icon (hearts, sparkle) tinted with [tint]. */
    @Synchronized
    fun icon(name: String, tint: Int): ImageBitmap? {
        val key = "icon|$name|$tint"
        cache[key]?.let { return it }
        val sprite = PetSprites[name] ?: return null
        val out = IntArray(sprite.pixels.size)
        drawLayer(out, sprite.pixels, tint)
        return toImage(out, sprite.width, sprite.height).also { cache[key] = it }
    }

    fun toImage(pixels: IntArray, w: Int, h: Int): ImageBitmap {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, pixels, 0, w)
        return img.toComposeImageBitmap()
    }
}
