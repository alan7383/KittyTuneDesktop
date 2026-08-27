package com.alananasss.kittytune.ui.upload

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackArtworkCropDialog(
    bitmap: BufferedImage?,
    titleKey: String = "upload_crop_artwork_title",
    descKey: String = "upload_crop_artwork_desc",
    onDismiss: () -> Unit,
    onSave: (BufferedImage) -> Unit
) {
    if (bitmap == null) {
        onDismiss()
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .width(480.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = str(titleKey),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = str(descKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(18.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .onGloballyPositioned { coordinates ->
                            containerSize = coordinates.size.toSize()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale

                                    val imageWidth = bitmap.width.toFloat()
                                    val imageHeight = bitmap.height.toFloat()
                                    val canvasWidth = containerSize.width
                                    val canvasHeight = containerSize.height

                                    val cropSize = min(canvasWidth, canvasHeight) - 32f
                                    val baseScale = max(cropSize / imageWidth, cropSize / imageHeight)
                                    val baseWidth = imageWidth * baseScale
                                    val baseHeight = imageHeight * baseScale

                                    val scaledWidth = baseWidth * scale
                                    val scaledHeight = baseHeight * scale

                                    val maxOffsetX = (scaledWidth - cropSize) / 2f
                                    val maxOffsetY = (scaledHeight - cropSize) / 2f

                                    val limitX = max(0f, maxOffsetX)
                                    val limitY = max(0f, maxOffsetY)

                                    offsetX = (offsetX + pan.x).coerceIn(-limitX, limitX)
                                    offsetY = (offsetY + pan.y).coerceIn(-limitY, limitY)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Scroll) {
                                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (scrollDelta != 0f) {
                                            val zoomFactor = if (scrollDelta < 0) 1.1f else 0.9f
                                            scale = (scale * zoomFactor).coerceIn(1f, 5f)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val imageBitmap = remember(bitmap) { bitmap.toComposeImageBitmap() }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val cropSize = min(canvasWidth, canvasHeight) - 32f

                        val imageWidth = imageBitmap.width.toFloat()
                        val imageHeight = imageBitmap.height.toFloat()

                        val baseScale = max(cropSize / imageWidth, cropSize / imageHeight)
                        val currentScale = baseScale * scale

                        val centerX = canvasWidth / 2f
                        val centerY = canvasHeight / 2f

                        withTransform({
                            translate(centerX + offsetX, centerY + offsetY)
                            scale(currentScale, currentScale, Offset.Zero)
                        }) {
                            drawImage(
                                image = imageBitmap,
                                topLeft = Offset(-imageWidth / 2f, -imageHeight / 2f)
                            )
                        }

                        // Dark overlay with square cutout
                        val fullPath = Path().apply {
                            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                        }
                        val cropRect = Rect(
                            centerX - cropSize / 2f,
                            centerY - cropSize / 2f,
                            centerX + cropSize / 2f,
                            centerY + cropSize / 2f
                        )
                        val cropPath = Path().apply {
                            addRoundRect(RoundRect(cropRect, androidx.compose.ui.geometry.CornerRadius(16f, 16f)))
                        }

                        val overlayPath = Path.combine(PathOperation.Difference, fullPath, cropPath)
                        drawPath(overlayPath, color = Color.Black.copy(alpha = 0.6f))

                        // Outline
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scale = (scale - 0.2f).coerceIn(1f, 5f) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Zoom out")
                    }

                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..5f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    IconButton(
                        onClick = { scale = (scale + 0.2f).coerceIn(1f, 5f) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Zoom in")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(str("upload_btn_cancel"))
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            scope.launch {
                                val cropped = withContext(Dispatchers.Default) {
                                    cropBufferedImage(
                                        src = bitmap,
                                        scale = scale,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        containerSize = containerSize
                                    )
                                }
                                onSave(cropped)
                                isSaving = false
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(str("btn_save"))
                        }
                    }
                }
            }
        }
    }
}

private fun cropBufferedImage(
    src: BufferedImage,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    containerSize: Size
): BufferedImage {
    val targetSize = 1400
    val output = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB)
    val g = output.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)

    val canvasWidth = containerSize.width.coerceAtLeast(1f)
    val canvasHeight = containerSize.height.coerceAtLeast(1f)
    val cropSize = (min(canvasWidth, canvasHeight) - 32f).coerceAtLeast(1f)

    val imageWidth = src.width.toFloat()
    val imageHeight = src.height.toFloat()

    val baseScale = max(cropSize / imageWidth, cropSize / imageHeight)
    val currentScale = baseScale * scale

    // Map coordinates to target output canvas
    val outputScale = targetSize.toFloat() / cropSize
    val finalScale = currentScale * outputScale

    val centerOutput = targetSize / 2.0
    val tx = centerOutput + (offsetX * outputScale)
    val ty = centerOutput + (offsetY * outputScale)

    val transform = AffineTransform().apply {
        translate(tx, ty)
        scale(finalScale.toDouble(), finalScale.toDouble())
        translate(-imageWidth / 2.0, -imageHeight / 2.0)
    }

    g.drawImage(src, transform, null)
    g.dispose()
    return output
}
