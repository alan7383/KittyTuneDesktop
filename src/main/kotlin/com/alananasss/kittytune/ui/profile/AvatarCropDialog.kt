@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

@Composable
fun AvatarCropDialog(
    bitmap: BufferedImage?,
    username: String = "",
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
            modifier = Modifier.width(460.dp).padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                if (username.isNotEmpty()) {
                    Text(
                        text = username,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = str("profile_avatar_upload_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                // Crop Area (Canvas)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
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

                                    val circleRadius = min(canvasWidth, canvasHeight) / 2f - 20f
                                    val circleDiameter = circleRadius * 2f

                                    val baseScale = max(circleDiameter / imageWidth, circleDiameter / imageHeight)
                                    val baseWidth = imageWidth * baseScale
                                    val baseHeight = imageHeight * baseScale

                                    val scaledWidth = baseWidth * scale
                                    val scaledHeight = baseHeight * scale

                                    val maxOffsetX = (scaledWidth - circleDiameter) / 2f
                                    val maxOffsetY = (scaledHeight - circleDiameter) / 2f

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
                        val circleRadius = min(canvasWidth, canvasHeight) / 2f - 20f
                        val circleDiameter = circleRadius * 2f

                        val imageWidth = imageBitmap.width.toFloat()
                        val imageHeight = imageBitmap.height.toFloat()

                        val baseScale = max(circleDiameter / imageWidth, circleDiameter / imageHeight)
                        val baseWidth = imageWidth * baseScale
                        val baseHeight = imageHeight * baseScale

                        // 1. Draw Image (transformed)
                        withTransform({
                            val px = canvasWidth / 2f
                            val py = canvasHeight / 2f
                            translate(left = offsetX, top = offsetY)
                            scale(scale, scale, pivot = Offset(px, py))
                        }) {
                            val left = (canvasWidth - baseWidth) / 2f
                            val top = (canvasHeight - baseHeight) / 2f

                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(left.toInt(), top.toInt()),
                                dstSize = IntSize(baseWidth.toInt(), baseHeight.toInt())
                            )
                        }

                        // 2. Draw Mask Overlay (semi-transparent outside circle)
                        val overlayPath = Path().apply {
                            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                        }
                        val circlePath = Path().apply {
                            addOval(Rect(center = center, radius = circleRadius))
                        }

                        val finalPath = Path.combine(
                            operation = PathOperation.Difference,
                            path1 = overlayPath,
                            path2 = circlePath
                        )

                        drawPath(path = finalPath, color = Color.Black.copy(alpha = 0.6f))

                        // 3. Draw White Circle Guide Outline
                        drawPath(
                            path = circlePath,
                            color = Color.White,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Zoom Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { scale = (scale - 0.1f).coerceAtLeast(1f) }
                    ) {
                        Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { scale = (scale + 0.1f).coerceAtMost(5f) }
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(str("btn_cancel"))
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                scope.launch(Dispatchers.Default) {
                                    val size = containerSize
                                    val radius = min(size.width, size.height) / 2f - 20f

                                    val cropRect = CropRect(
                                        left = size.width / 2f - radius,
                                        top = size.height / 2f - radius,
                                        right = size.width / 2f + radius,
                                        bottom = size.height / 2f + radius
                                    )

                                    val state = ImageState(scale, offsetX, offsetY)

                                    val result = BitmapUtils.cropBitmap(
                                        source = bitmap,
                                        cropRect = cropRect,
                                        imageState = state,
                                        viewWidth = size.width,
                                        viewHeight = size.height,
                                        targetWidth = 2048,
                                        targetHeight = 2048
                                    )

                                    withContext(Dispatchers.Main) {
                                        onSave(result)
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
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
