package com.alananasss.kittytune.ui.profile

import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

/**
 * Desktop port of the Android BitmapUtils (avatar/banner croppers).
 * android.graphics.Bitmap/Matrix/RectF -> BufferedImage/AffineTransform/Rectangle2D.
 */
object BitmapUtils {

    /**
     * Crops the image based on the visual transformation applied in the UI.
     *
     * @param source The original image.
     * @param cropRect The visible cropping area in screen coordinates (pixels).
     * @param imageState Current transformation state (zoom, pan X, pan Y).
     * @param viewWidth Width of the image display area.
     * @param viewHeight Height of the image display area.
     */
    fun cropBitmap(
        source: BufferedImage,
        cropRect: Rectangle2D.Float,
        imageState: ImageState,
        viewWidth: Float,
        viewHeight: Float
    ): BufferedImage {
        val transform = AffineTransform()

        val imageWidth = source.width.toFloat()
        val imageHeight = source.height.toFloat()

        // 1. Replicate UI display logic (ContentScale.Fit + Center).
        // AffineTransform pre-multiplies, so operations are applied in reverse
        // order compared to Android Matrix.post*: last-added runs first.
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        val baseScale = min(scaleX, scaleY) // Initial "Fit" scale

        // Apply user transformations (Zoom/Pan) from center
        transform.translate(imageState.offsetX.toDouble(), imageState.offsetY.toDouble())
        transform.translate((viewWidth / 2f).toDouble(), (viewHeight / 2f).toDouble())
        transform.scale(imageState.scale.toDouble(), imageState.scale.toDouble())
        transform.translate((-viewWidth / 2f).toDouble(), (-viewHeight / 2f).toDouble())

        // Apply base scale from center
        transform.translate((viewWidth / 2f).toDouble(), (viewHeight / 2f).toDouble())
        transform.scale(baseScale.toDouble(), baseScale.toDouble())
        transform.translate((-viewWidth / 2f).toDouble(), (-viewHeight / 2f).toDouble())

        // Initial centering
        transform.translate(
            ((viewWidth - imageWidth) / 2f).toDouble(),
            ((viewHeight - imageHeight) / 2f).toDouble(),
        )

        // 2. Invert to map screen cropRect back to source image coordinates
        val inverse = transform.createInverse()
        val srcRect = inverse.createTransformedShape(cropRect).bounds2D

        // 3. Clamp bounds to avoid out-of-bounds exceptions
        val left = max(0.0, srcRect.x)
        val top = max(0.0, srcRect.y)
        val width = min(srcRect.width, imageWidth - left)
        val height = min(srcRect.height, imageHeight - top)

        // If selection is invalid, return a safe fallback (scaled original)
        if (width <= 0 || height <= 0) return scaled(source, 1000, 1000)

        // 4. Crop
        val cropped = source.getSubimage(left.toInt(), top.toInt(), width.toInt(), height.toInt())

        // 5. Resize to 1000x1000 (SoundCloud standard)
        return scaled(cropped, 1000, 1000)
    }

    private fun scaled(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }
}

data class ImageState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)
