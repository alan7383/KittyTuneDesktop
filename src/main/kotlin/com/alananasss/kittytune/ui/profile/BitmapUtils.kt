package com.alananasss.kittytune.ui.profile

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

data class ImageState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

object BitmapUtils {

    /**
     * Crops the BufferedImage based on the visual transformation applied in the UI.
     *
     * @param source The original BufferedImage.
     * @param cropRect The visible cropping area in container coordinates (pixels).
     * @param imageState Current transformation state (zoom, pan X, pan Y).
     * @param viewWidth Width of the container display area.
     * @param viewHeight Height of the container display area.
     * @param targetWidth Output image width (default 1000px).
     * @param targetHeight Output image height (default 1000px).
     */
    fun cropBitmap(
        source: BufferedImage,
        cropRect: CropRect,
        imageState: ImageState,
        viewWidth: Float,
        viewHeight: Float,
        targetWidth: Int = 1000,
        targetHeight: Int = 1000
    ): BufferedImage {
        val imageWidth = source.width.toFloat()
        val imageHeight = source.height.toFloat()

        val cx = viewWidth / 2f
        val cy = viewHeight / 2f

        val baseScale = max(cropRect.width / imageWidth, cropRect.height / imageHeight)
        val displayedWidth = imageWidth * baseScale * imageState.scale
        val displayedHeight = imageHeight * baseScale * imageState.scale

        val srcLeft = ((cropRect.left - imageState.offsetX - cx) / displayedWidth + 0.5f) * imageWidth
        val srcTop = ((cropRect.top - imageState.offsetY - cy) / displayedHeight + 0.5f) * imageHeight
        val srcRight = ((cropRect.right - imageState.offsetX - cx) / displayedWidth + 0.5f) * imageWidth
        val srcBottom = ((cropRect.bottom - imageState.offsetY - cy) / displayedHeight + 0.5f) * imageHeight

        val clampedLeft = max(0f, min(srcLeft, srcRight)).coerceIn(0f, imageWidth - 1f)
        val clampedTop = max(0f, min(srcTop, srcBottom)).coerceIn(0f, imageHeight - 1f)
        val clampedRight = min(imageWidth, max(srcLeft, srcRight)).coerceIn(clampedLeft + 1f, imageWidth)
        val clampedBottom = min(imageHeight, max(srcTop, srcBottom)).coerceIn(clampedTop + 1f, imageHeight)

        val cropW = (clampedRight - clampedLeft).toInt().coerceIn(1, source.width - clampedLeft.toInt())
        val cropH = (clampedBottom - clampedTop).toInt().coerceIn(1, source.height - clampedTop.toInt())

        val subImage = source.getSubimage(clampedLeft.toInt(), clampedTop.toInt(), cropW, cropH)

        val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(subImage, 0, 0, targetWidth, targetHeight, null)
        g.dispose()

        return scaled
    }
}
