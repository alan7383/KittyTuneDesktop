@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.pet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.clearance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import java.awt.image.BufferedImage
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private sealed interface PhotoStep {
    data object Pick : PhotoStep
    data class Crop(val bitmap: BufferedImage) : PhotoStep
    data class Markers(val crop: BufferedImage) : PhotoStep
    data class Analyzing(val crop: BufferedImage) : PhotoStep
    data class Result(val crop: BufferedImage, val result: PetPhotoResult) : PhotoStep
    data class Edit(val crop: BufferedImage, val result: PetPhotoResult) : PhotoStep
}

/** Experimental: turn a photo of your cat into its pixel look (coat colours, pattern, eyes). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetPhotoScreen(
    settings: PetSettings,
    onApply: (PetSettings) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<PhotoStep>(PhotoStep.Pick) }
    var error by remember { mutableStateOf(false) }

    fun pick() {
        scope.launch {
            // on the UI thread: the dialog keeps the app's events flowing while it is open
            val file = com.alananasss.kittytune.core.NativeFileDialog.openFile(
                str("pet_desk_pick_photo"),
                com.alananasss.kittytune.core.NativeFileDialog.FileType(str("pet_desk_photo_filter"), listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")),
            ) ?: return@launch
            val bmp = withContext(Dispatchers.IO) { runCatching { decodeOriented(file) }.getOrNull() }
            if (bmp != null) { error = false; step = PhotoStep.Crop(bmp) } else error = true
        }
    }

    BackHandler {
        step = when (val s = step) {
            is PhotoStep.Crop, is PhotoStep.Markers, is PhotoStep.Result, is PhotoStep.Analyzing -> PhotoStep.Pick
            is PhotoStep.Edit -> PhotoStep.Result(s.crop, s.result)
            PhotoStep.Pick -> { onClose(); PhotoStep.Pick }
        }
    }

    // the pixel editor has its own top bar: show it full screen
    (step as? PhotoStep.Edit)?.let { s ->
        val look = settings.copy(colorId = PetColor.CUSTOM, customPalette = s.result.palette)
        PetCoatEditor(
            settings = look,
            initial = s.result.coat ?: PetCoat.fromPreset(s.result.looksLike),
            onSave = { coat -> step = PhotoStep.Result(s.crop, s.result.copy(coat = coat, pattern = PetPattern.CUSTOM)) },
            onClose = { step = PhotoStep.Result(s.crop, s.result) }
        )
        return
    }

    // The floating player bar is drawn over the page; every step (and its buttons) ends above it.
    val barOverlap = com.alananasss.kittytune.ui.common.rememberPlayerBarOverlap()
    val barClearance = barOverlap.clearance()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().then(barOverlap.modifier)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = barClearance)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = {
                    if (step is PhotoStep.Pick) onClose() else step = PhotoStep.Pick
                }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                }
                Text(
                    str("pet_photo_title"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        str("pet_experimental"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "photoStep",
                modifier = Modifier.weight(1f)
            ) { s ->
                when (s) {
                    PhotoStep.Pick -> PickStep(settings, error, onPick = ::pick)

                    is PhotoStep.Crop -> CropStep(s.bitmap, onRetake = { step = PhotoStep.Pick }) { crop ->
                        step = PhotoStep.Markers(crop)
                    }

                    is PhotoStep.Markers -> MarkersStep(s.crop, onBack = { step = PhotoStep.Pick }) { marks ->
                        step = PhotoStep.Analyzing(s.crop)
                        scope.launch {
                            val started = System.currentTimeMillis()
                            val result = withContext(Dispatchers.Default) { PetPhotoAnalyzer.analyze(s.crop, marks) }
                            delay((1_800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                            step = PhotoStep.Result(s.crop, result)
                        }
                    }

                    is PhotoStep.Edit -> Unit

                    is PhotoStep.Analyzing -> AnalyzingStep(s.crop, settings)

                    is PhotoStep.Result -> ResultStep(
                        crop = s.crop,
                        result = s.result,
                        settings = settings,
                        onRetry = { step = PhotoStep.Pick },
                        onEdit = { step = PhotoStep.Edit(s.crop, s.result) },
                        onApply = { onApply(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickStep(settings: PetSettings, error: Boolean, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PetSprite(PetActivity.WAVE, settings, Modifier.widthIn(max = 380.dp).fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text(
            str("pet_photo_headline"),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            str("pet_desk_photo_body"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 420.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pet_photo_tip_face", "pet_photo_tip_light", "pet_photo_tip_filter").forEach {
                    Text("•  " + str(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (error) {
            Spacer(Modifier.height(12.dp))
            Text(str("pet_photo_error"), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, shapes = ButtonDefaults.shapes(), modifier = Modifier.widthIn(min = 240.dp)) {
            Icon(Icons.Rounded.PhotoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text(str("pet_desk_pick_photo"))
        }
    }
}

/** Pan (drag) and zoom (mouse wheel, or pinch) the photo under a round guide; the visible square is what gets analysed. */
@Composable
private fun CropStep(bitmap: BufferedImage, onRetake: () -> Unit, onDone: (BufferedImage) -> Unit) {
    val image = remember(bitmap) { bitmap.toComposeImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxPx by remember { mutableFloatStateOf(1f) }

    fun baseScale() = max(boxPx / bitmap.width, boxPx / bitmap.height)
    fun clamp(o: Offset, z: Float): Offset {
        val s = baseScale() * z
        val maxX = max(0f, (bitmap.width * s - boxPx) / 2)
        val maxY = max(0f, (bitmap.height * s - boxPx) / 2)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            str("pet_desk_crop_hint"),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
        ) {
            val density = LocalDensity.current
            boxPx = with(density) { maxWidth.toPx() }
            val primary = MaterialTheme.colorScheme.primary
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            offset = clamp(offset + pan, zoom)
                        }
                    }
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) {
                            val z = (zoom * (1f - dy * 0.12f)).coerceIn(1f, 6f)
                            offset = clamp(offset * (z / zoom), z)
                            zoom = z
                        }
                    }
            ) {
                val s = baseScale() * zoom
                val w = bitmap.width * s
                val h = bitmap.height * s
                val left = size.width / 2 - w / 2 + offset.x
                val top = size.height / 2 - h / 2 + offset.y
                drawImage(image, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
                val circle = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(Offset(size.width * 0.06f, size.height * 0.06f), Size(size.width * 0.88f, size.height * 0.88f)))
                }
                clipPath(circle, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f)) }
                drawPath(circle, primary, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text(str("pet_photo_retake"))
            }
            Button(onClick = {
                val s = baseScale() * zoom
                val left = boxPx / 2 - bitmap.width * s / 2 + offset.x
                val top = boxPx / 2 - bitmap.height * s / 2 + offset.y
                val x = ((0 - left) / s).roundToInt().coerceIn(0, bitmap.width - 1)
                val y = ((0 - top) / s).roundToInt().coerceIn(0, bitmap.height - 1)
                val side = (boxPx / s).roundToInt().coerceAtMost(min(bitmap.width - x, bitmap.height - y)).coerceAtLeast(8)
                onDone(bitmap.getSubimage(x, y, side, side))
            }, shapes = ButtonDefaults.shapes()) {
                Text(str("pet_photo_next"))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
}

/** Drag the two eye rings and the nose dot onto the cat: the sprite head is laid over them. */
@Composable
private fun MarkersStep(crop: BufferedImage, onBack: () -> Unit, onDone: (PetLandmarks) -> Unit) {
    val image = remember(crop) { crop.toComposeImageBitmap() }
    val d = PetLandmarks.DEFAULT
    val points = remember { mutableStateListOf(Offset(d.leftEyeX, d.leftEyeY), Offset(d.rightEyeX, d.rightEyeY), Offset(d.noseX, d.noseY)) }
    var dragging by remember { mutableIntStateOf(-1) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            str("pet_photo_markers_hint"),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        val eyeColor = Color(0xFF7CC45A)
        val noseColor = Color(0xFFFF6F9C)
        Box(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
        ) {
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o ->
                                val rel = Offset(o.x / size.width, o.y / size.height)
                                dragging = points.indices.minBy { (points[it] - rel).getDistance() }
                            },
                            onDragEnd = { dragging = -1 },
                            onDrag = { change, amount ->
                                if (dragging >= 0) {
                                    val p = points[dragging] + Offset(amount.x / size.width, amount.y / size.height)
                                    points[dragging] = Offset(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))
                                }
                                change.consume()
                            }
                        )
                    }
            ) {
                val r = size.width * 0.06f
                points.forEachIndexed { i, p ->
                    val c = Offset(p.x * size.width, p.y * size.height)
                    val color = if (i < 2) eyeColor else noseColor
                    val radius = if (i < 2) r else r * 0.6f
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius + 3f, center = c, style = Stroke(width = 9f))
                    drawCircle(color, radius = radius, center = c, style = Stroke(width = 5f))
                    drawCircle(color, radius = 5f, center = c)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(eyeColor, str("pet_photo_marker_eyes"))
            LegendDot(noseColor, str("pet_photo_marker_nose"))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text(str("pet_photo_retake"))
            }
            Button(onClick = {
                val (l, r) = if (points[0].x <= points[1].x) points[0] to points[1] else points[1] to points[0]
                onDone(PetLandmarks(l.x, l.y, r.x, r.y, points[2].x, points[2].y))
            }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text(str("pet_photo_make"))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .border(3.dp, color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnalyzingStep(crop: BufferedImage, settings: PetSettings) {
    val image = remember(crop) { crop.toComposeImageBitmap() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(200.dp))
            Image(
                image, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            str("pet_photo_analyzing", settings.name),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultStep(
    crop: BufferedImage,
    result: PetPhotoResult,
    settings: PetSettings,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onApply: (PetSettings) -> Unit,
) {
    val image = remember(crop) { crop.toComposeImageBitmap() }
    val look = settings.copy(
        colorId = PetColor.CUSTOM,
        customPalette = result.palette,
        pattern = result.pattern,
        coat = result.coat ?: settings.coat,
        eyeColor = result.eyeColor ?: settings.eyeColor,
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 460.dp)) {
            Image(
                image, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.padding(horizontal = 8.dp))
            PetSprite(PetActivity.LOVE, look, Modifier.weight(1f), corner = 20)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            str("pet_photo_result"),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetectedRow(str("pet_settings_pattern"), str(result.looksLike.label))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str("pet_photo_colors"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    listOf(result.palette.fur, result.palette.shade, result.palette.mark, result.palette.white).forEach {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(it))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str("pet_settings_eyes"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (result.eyeColor != null) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(result.eyeColor))
                        )
                    } else {
                        Text(str("pet_photo_eyes_unknown"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            str("pet_photo_tweak"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Brush, null)
            Spacer(Modifier.width(6.dp))
            Text(str("pet_photo_edit"))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text(str("pet_photo_retry"))
            }
            Button(onClick = { onApply(look) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(6.dp))
                Text(str("pet_photo_apply"))
            }
        }
    }
}

@Composable
private fun DetectedRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
    }
}

/** Decodes [file] at a sensible size (<= 2048 px) with its EXIF rotation applied (photos from phones). */
private fun decodeOriented(file: File): BufferedImage? {
    val bytes = file.readBytes()
    val decoded = runCatching { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) }.getOrNull()
        ?: runCatching {
            // ImageIO knows no WebP: Skia does
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap().let { img ->
                val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
                val px = IntArray(img.width * img.height)
                img.readPixels(px)
                out.setRGB(0, 0, img.width, img.height, px, 0, img.width)
                out
            }
        }.getOrNull()
        ?: return null
    var bmp = decoded
    val longest = max(bmp.width, bmp.height)
    if (longest > 2048) {
        val k = 2048f / longest
        bmp = PetPhotoAnalyzer.scaled(bmp, (bmp.width * k).roundToInt().coerceAtLeast(1), (bmp.height * k).roundToInt().coerceAtLeast(1))
    } else if (bmp.type != BufferedImage.TYPE_INT_ARGB) {
        bmp = PetPhotoAnalyzer.scaled(bmp, bmp.width, bmp.height)
    }
    val rotation = jpegOrientation(bytes)
    if (rotation == 0) return bmp
    val w = bmp.width
    val h = bmp.height
    val out = if (rotation == 180) BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB) else BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) for (x in 0 until w) {
        val c = bmp.getRGB(x, y)
        when (rotation) {
            90 -> out.setRGB(h - 1 - y, x, c)
            180 -> out.setRGB(w - 1 - x, h - 1 - y, c)
            else -> out.setRGB(y, w - 1 - x, c)
        }
    }
    return out
}

/** Clockwise rotation from a JPEG's EXIF orientation tag (0x0112), 0 when absent or not a JPEG. */
internal fun jpegOrientation(b: ByteArray): Int {
    fun u16(i: Int, le: Boolean) =
        if (le) (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        else ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    fun u32(i: Int, le: Boolean) = if (le) u16(i, true) or (u16(i + 2, true) shl 16) else (u16(i, false) shl 16) or u16(i + 2, false)
    if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return 0
    var i = 2
    while (i + 4 < b.size) {
        if ((b[i].toInt() and 0xFF) != 0xFF) return 0
        val marker = b[i + 1].toInt() and 0xFF
        val len = u16(i + 2, false)
        if (marker == 0xE1 && i + 10 < b.size && String(b, i + 4, 4, Charsets.US_ASCII) == "Exif") {
            val tiff = i + 10
            if (tiff + 8 > b.size) return 0
            val le = b[tiff].toInt() == 'I'.code
            val ifd = tiff + u32(tiff + 4, le)
            if (ifd + 2 > b.size) return 0
            val entries = u16(ifd, le)
            for (e in 0 until entries) {
                val at = ifd + 2 + e * 12
                if (at + 12 > b.size) return 0
                if (u16(at, le) == 0x0112) {
                    return when (u16(at + 8, le)) {
                        3 -> 180
                        6 -> 90
                        8 -> 270
                        else -> 0
                    }
                }
            }
            return 0
        }
        if (marker == 0xDA) return 0
        i += 2 + len
    }
    return 0
}
