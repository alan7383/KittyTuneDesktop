package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws [content] as a QR code, one canvas rect per module.
 *
 * The matrix is encoded at its intrinsic size (one cell per module) and scaled here, so the
 * result stays sharp at any layout size and follows the theme colors instead of baking a
 * bitmap. Renders nothing when the content can't be encoded.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    foreground: Color = Color.Black,
    background: Color = Color.White
) {
    val matrix = remember(content) { encodeQr(content) } ?: return

    Canvas(modifier) {
        val cell = floor(min(size.width, size.height) / matrix.width)
        if (cell < 1f) return@Canvas
        val drawn = cell * matrix.width
        val originX = (size.width - drawn) / 2f
        val originY = (size.height - drawn) / 2f

        drawRect(color = background, topLeft = Offset(originX, originY), size = Size(drawn, drawn))

        val cellSize = Size(cell, cell)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(originX + x * cell, originY + y * cell),
                        size = cellSize
                    )
                }
            }
        }
    }
}

private fun encodeQr(content: String): BitMatrix? {
    if (content.isBlank()) return null
    return runCatching {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            // Zero means "intrinsic": one cell per module plus the quiet zone.
            0,
            0,
            mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    }.getOrNull()
}
