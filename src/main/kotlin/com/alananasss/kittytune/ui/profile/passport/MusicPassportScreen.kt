@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.alananasss.kittytune.ui.profile.passport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import java.awt.print.Printable
import java.awt.print.PrinterJob
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.MusicPassport
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.sin

object PassportViewerState {
    var visible by mutableStateOf(false)
    var bitmap by mutableStateOf<ImageBitmap?>(null)
    var image by mutableStateOf<BufferedImage?>(null)
    var passport by mutableStateOf<MusicPassport?>(null)

    fun show(bitmap: ImageBitmap, image: BufferedImage?, passport: MusicPassport) {
        this.bitmap = bitmap
        this.image = image
        this.passport = passport
        this.visible = true
    }

    fun hide() {
        visible = false
    }
}

@Composable
fun MusicPassportScreen(
    onBackClick: () -> Unit,
    currentUser: User? = null,
) {
    val viewModel: MusicPassportViewModel = viewModel { MusicPassportViewModel(currentUser) }
    val passport = viewModel.passport
    val bitmap = viewModel.bitmap
    val awtImage = viewModel.awtImage
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBackClick)

    DisposableEffect(Unit) {
        onDispose { PassportViewerState.hide() }
    }

    LaunchedEffect(passport, accentArgb) {
        if (passport != null) viewModel.renderIfNeeded(accentArgb)
    }

    LaunchedEffect(bitmap, awtImage, passport) {
        if (PassportViewerState.visible && bitmap != null && passport != null) {
            PassportViewerState.bitmap = bitmap
            PassportViewerState.image = awtImage
            PassportViewerState.passport = passport
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PassportTopBar(
            passport = passport,
            onBackClick = onBackClick,
        )

        if (passport == null || bitmap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator(Modifier.size(64.dp))
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 980.dp
                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left column: Interactive 3D Passport Document Preview + Quick Actions
                        Column(
                            modifier = Modifier
                                .widthIn(min = 380.dp, max = 480.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                PassportCard(
                                    bitmap = bitmap,
                                    onZoomClick = { PassportViewerState.show(bitmap, awtImage, passport) },
                                    modifier = Modifier.heightIn(max = (this@BoxWithConstraints.maxHeight - 84.dp).coerceAtLeast(360.dp)),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            PassportActionsRow(
                                image = awtImage,
                                passport = passport,
                                onSave = {
                                    awtImage?.let { img -> scope.launch { savePassportWithDialog(img, passport) } }
                                },
                            )
                        }

                        // Right column: Interactive Desktop Dossier Inspection Panel
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(end = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            IdentityDossierCard(passport)
                            FiguresGrid(passport)
                            CitizenshipsSection(passport)
                            VisasSection(passport)
                            StampsSection(passport)
                            MrzCard(passport)
                        }
                    }
                } else {
                    // Compact / medium window layout: big, readable passport card on top
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        PassportCard(
                            bitmap = bitmap,
                            onZoomClick = { PassportViewerState.show(bitmap, awtImage, passport) },
                            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                        )
                        PassportActionsRow(
                            image = awtImage,
                            passport = passport,
                            onSave = {
                                awtImage?.let { img -> scope.launch { savePassportWithDialog(img, passport) } }
                            },
                            modifier = Modifier.widthIn(max = 460.dp),
                        )
                        IdentityDossierCard(passport)
                        FiguresGrid(passport)
                        CitizenshipsSection(passport)
                        VisasSection(passport)
                        StampsSection(passport)
                        MrzCard(passport)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PassportTopBar(
    passport: MusicPassport?,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBackClick,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
        }
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    str("passport_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (passport != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            passport.passportNo,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(
                str("passport_tagline"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PassportCard(
    bitmap: ImageBitmap,
    onZoomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val animX by animateFloatAsState(
        targetValue = tiltX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "passportTiltX",
    )
    val animY by animateFloatAsState(
        targetValue = tiltY,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "passportTiltY",
    )
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .graphicsLayer {
                rotationX = animX
                rotationY = animY
                cameraDistance = 18f * density
            }
            .shadow(24.dp, shape, clip = false)
            .clip(shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onZoomClick,
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                val nx = ((pos.x / size.width.coerceAtLeast(1)) - 0.5f) * 2f
                                val ny = ((pos.y / size.height.coerceAtLeast(1)) - 0.5f) * 2f
                                tiltY = (nx * 8f).coerceIn(-10f, 10f)
                                tiltX = (-ny * 8f).coerceIn(-10f, 10f)
                            }
                            PointerEventType.Exit -> {
                                tiltX = 0f
                                tiltY = 0f
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        tiltX = 0f
                        tiltY = 0f
                    },
                    onDragCancel = {
                        tiltX = 0f
                        tiltY = 0f
                    },
                ) { change, drag ->
                    change.consume()
                    tiltY = (tiltY + drag.x * 0.06f).coerceIn(-12f, 12f)
                    tiltX = (tiltX - drag.y * 0.06f).coerceIn(-12f, 12f)
                }
            }
            .drawWithContent {
                drawContent()
                val strength = ((kotlin.math.abs(animX) + kotlin.math.abs(animY)) / 18f).coerceIn(0f, 1f)
                if (strength > 0.01f) {
                    val shift = (animY - animX) / 20f
                    val cx = size.width * (0.5f + shift * 0.45f)
                    val cy = size.height * (0.42f - shift * 0.35f)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                accent.copy(alpha = 0.08f * strength),
                                Color.White.copy(alpha = 0.12f * strength),
                                Color(0xFF4CE0D2).copy(alpha = 0.06f * strength),
                                Color.Transparent,
                            ),
                            start = Offset(cx - size.width * 0.55f, cy - size.height * 0.35f),
                            end = Offset(cx + size.width * 0.55f, cy + size.height * 0.35f),
                        ),
                    )
                }
            }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = str("passport_title"),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.48f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PassportActionsRow(
    image: BufferedImage?,
    passport: MusicPassport,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PassportCopySplitButton(image = image, passport = passport)
        Button(
            onClick = onSave,
            enabled = image != null,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(str("passport_save_image"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The same expressive split button as the cover viewer: the leading half copies the image, the
 * trailing half opens the other ways to send the passport somewhere (share, print, MRZ).
 */
@Composable
private fun PassportCopySplitButton(image: BufferedImage?, passport: MusicPassport) {
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        val splitColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { image?.let(::copyPassportToClipboard) },
                    enabled = image != null,
                    colors = splitColors,
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("cover_copy_image"), maxLines = 1)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuExpanded,
                    onCheckedChange = { menuExpanded = it },
                    enabled = image != null,
                    colors = splitColors,
                ) {
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = str("btn_share"),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(str("btn_share")) },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    menuExpanded = false
                    image?.let { img -> scope.launch { openPassportInViewer(img, passport) } }
                },
            )
            DropdownMenuItem(
                text = { Text(str("passport_print")) },
                leadingIcon = { Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    menuExpanded = false
                    image?.let { img -> scope.launch { printPassport(img) } }
                },
            )
            DropdownMenuItem(
                text = { Text(str("passport_open_folder")) },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    menuExpanded = false
                    image?.let { img -> scope.launch { revealPassportInFolder(img, passport) } }
                },
            )
            DropdownMenuItem(
                text = { Text(str("passport_copy_mrz")) },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    menuExpanded = false
                    val (line1, line2) = MusicPassportRender.mrzLines(passport)
                    val sel = StringSelection("$line1\n$line2")
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    Toaster.show(str("copied_to_clipboard"))
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityDossierCard(passport: MusicPassport) {
    val scheme = MaterialTheme.colorScheme
    // On the shared ambient pulse (see AmbientClock): a forty-eight second turn needs none of the display's
    // frames, and none at all while the window is hidden.
    val angle = com.alananasss.kittytune.ui.common.rememberAmbientLoop(
        periodMs = 48_000,
        from = 0f,
        to = 360f,
    )
    val issuedDate = remember(passport.issuedAtMs) {
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(passport.issuedAtMs).atZone(ZoneId.systemDefault()))
            .uppercase(Locale.ENGLISH)
    }
    val primaryGenre = passport.citizenships.firstOrNull()?.genre ?: "FREE LISTENER"

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Subtle guilloché wave lines inside the identity card
            Canvas(Modifier.matchParentSize()) {
                val waveColor = scheme.primary.copy(alpha = 0.05f)
                for (i in 0 until 6) {
                    val path = Path()
                    val baseY = 26f + i * 36f
                    path.moveTo(0f, baseY)
                    var x = 0f
                    while (x <= size.width) {
                        val y = baseY + sin(x * 0.015f + i * 0.6f) * 8f
                        path.lineTo(x, y)
                        x += 16f
                    }
                    drawPath(path, waveColor, style = Stroke(width = 1.4f))
                }
            }

            // Rotating expressive rosette watermark in the corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(110.dp)
                    .graphicsLayer { rotationZ = angle.value }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(scheme.primary.copy(alpha = 0.07f)),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DocumentMetaChip("TYPE", "P")
                    DocumentMetaChip("CODE", "KTN")
                    DocumentMetaChip("AUTHORITY", "KITTYTUNE")
                    DocumentMetaChip("PASSPORT NO.", passport.passportNo, highlight = true)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(scheme.primaryContainer)
                            .border(1.5.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!passport.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = passport.avatarUrl,
                                contentDescription = passport.holderName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                passport.holderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "\u266A",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimaryContainer,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "HOLDER / TITULAIRE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            passport.holderName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = scheme.primaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(MaterialShapes.Flower.toShape())
                                            .background(scheme.primary.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Explore,
                                            contentDescription = null,
                                            tint = scheme.onPrimaryContainer,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                    Text(
                                        passport.travellerClass,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = scheme.secondaryContainer,
                            ) {
                                Text(
                                    primaryGenre.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IdentityMetaField("DATE OF ISSUE", issuedDate)
                    IdentityMetaField("EXPIRES", "NEVER")
                    IdentityMetaField("NATIONALITY / GENRE", primaryGenre.uppercase(Locale.ROOT))
                }
            }
        }
    }
}

@Composable
private fun DocumentMetaChip(label: String, value: String, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) scheme.primaryContainer else scheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) scheme.onPrimaryContainer else scheme.onSurface,
            )
        }
    }
}

@Composable
private fun IdentityMetaField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.9.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FiguresGrid(passport: MusicPassport) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    val hoursSub = remember(passport.totalMinutes) {
        val hours = passport.totalMinutes / 60
        if (hours > 0) "${nf.format(hours)} h" else "${passport.totalMinutes} min"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FigureTile(
            icon = Icons.Rounded.Schedule,
            label = "MINUTES",
            value = nf.format(passport.totalMinutes),
            subtitle = hoursSub,
            highlight = true,
            modifier = Modifier.weight(1f),
        )
        FigureTile(
            icon = Icons.Rounded.MusicNote,
            label = "TRACKS",
            value = nf.format(passport.tracks),
            subtitle = null,
            highlight = false,
            modifier = Modifier.weight(1f),
        )
        FigureTile(
            icon = Icons.Rounded.Person,
            label = "ARTISTS",
            value = nf.format(passport.artists),
            subtitle = null,
            highlight = false,
            modifier = Modifier.weight(1f),
        )
        FigureTile(
            icon = Icons.Rounded.LocalFireDepartment,
            label = "DAY STREAK",
            value = nf.format(passport.longestStreakDays),
            subtitle = null,
            highlight = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FigureTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String?,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = if (highlight) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.0.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) scheme.onPrimaryContainer.copy(alpha = 0.78f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (highlight) scheme.onPrimaryContainer else scheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlight) scheme.onPrimaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlight) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CitizenshipsSection(passport: MusicPassport) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DossierSectionHeader(
                icon = Icons.Rounded.Public,
                title = "CITIZENSHIPS \u00B7 TOP GENRES",
            )
            if (passport.citizenships.isEmpty()) {
                Text(
                    "Listening across genres builds your citizenships.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                val rows = passport.citizenships.take(4).chunked(2)
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { citizenship ->
                            CitizenshipTile(citizenship, Modifier.weight(1f))
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CitizenshipTile(citizenship: MusicPassport.Citizenship, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (c1, c2, c3) = remember(citizenship.genre) { MusicPassportRender.genreStripeColors(citizenship.genre) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 3-stripe generated genre flag
                Row(
                    modifier = Modifier
                        .size(width = 42.dp, height = 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(c1)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(c2)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(c3)))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        citizenship.genre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${citizenship.sharePercent}% OF LISTENING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((citizenship.sharePercent.coerceIn(4, 100)) / 100f)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
    }
}

@Composable
private fun VisasSection(passport: MusicPassport) {
    if (passport.visas.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val nf = remember { NumberFormat.getIntegerInstance() }
    val fallbackArgb = scheme.primary.toArgb()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DossierSectionHeader(
                icon = Icons.Rounded.Album,
                title = "MULTI-ENTRY VISAS \u00B7 PLATFORMS",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                passport.visas.take(3).forEach { visa ->
                    val stampColor = Color(MusicPassportRender.visaColor(visa.platform, fallbackArgb))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = stampColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, stampColor.copy(alpha = 0.55f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "MULTI-ENTRY VISA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.8.sp,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = stampColor,
                                )
                                Text("\u2605", color = stampColor, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                visa.platform,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            HorizontalDivider(
                                color = stampColor.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Text(
                                "${nf.format(visa.discoveries)} TRACKS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            Text(
                                "SINCE ${visa.since}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StampsSection(passport: MusicPassport) {
    if (passport.stamps.isEmpty()) return
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DossierSectionHeader(
                icon = Icons.Rounded.Verified,
                title = "ENTRY STAMPS \u00B7 ACHIEVEMENTS",
            )
            val rows = passport.stamps.take(4).chunked(2)
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEachIndexed { idx, stamp ->
                        val isCrimson = idx % 2 == 1
                        val stampTint = if (isCrimson) Color(0xFFB83A4B) else scheme.primary
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = scheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, stampTint.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(MaterialShapes.Cookie12Sided.toShape())
                                        .background(stampTint.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = stampTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stamp.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        stamp.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = stampTint,
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MrzCard(passport: MusicPassport) {
    val scheme = MaterialTheme.colorScheme
    val (line1, line2) = remember(passport) { MusicPassportRender.mrzLines(passport) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "ICAO 9303 MACHINE-READABLE ZONE (TD3)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                        ),
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        line1,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        line2,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = {
                    val sel = StringSelection("$line1\n$line2")
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    Toaster.show(str("copied_to_clipboard"))
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = str("copied_to_clipboard"), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DossierSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.2.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Full-screen view of the passport, built identically to [com.alananasss.kittytune.ui.common.CoverViewerOverlay]:
 * a dark scrim that closes on click without any ripple, the document scaled to the window height,
 * the top-right close button, and the expressive split-button + save actions underneath.
 */
@Composable
fun PassportViewerOverlay() {
    val visible = PassportViewerState.visible
    val bitmap = PassportViewerState.bitmap
    val image = PassportViewerState.image
    val passport = PassportViewerState.passport
    val scope = rememberCoroutineScope()

    if (visible) {
        BackHandler {
            PassportViewerState.hide()
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { PassportViewerState.hide() },
                )
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        PassportViewerState.hide()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.9f),
                exit = scaleOut(targetScale = 0.9f),
            ) {
                if (bitmap != null && passport != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 64.dp, horizontal = 32.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = passport.holderName,
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PassportCopySplitButton(image = image, passport = passport)
                            Button(
                                onClick = {
                                    image?.let { img ->
                                        scope.launch { savePassportWithDialog(img, passport) }
                                    }
                                },
                                enabled = image != null,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(str("passport_save_image"))
                            }
                        }
                    }
                }
            }

            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { PassportViewerState.hide() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = str("btn_close"), tint = Color.White)
            }
        }
    }
}

private suspend fun printPassport(image: BufferedImage) {
    withContext(Dispatchers.IO) {
        runCatching {
            val job = PrinterJob.getPrinterJob()
            job.jobName = "KittyTune Music Passport"
            job.setPrintable { graphics, pageFormat, pageIndex ->
                if (pageIndex > 0) return@setPrintable Printable.NO_SUCH_PAGE
                val g2 = graphics as java.awt.Graphics2D
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                val scale = minOf(pageFormat.imageableWidth / image.width, pageFormat.imageableHeight / image.height)
                val w = image.width * scale
                val h = image.height * scale
                val x = pageFormat.imageableX + (pageFormat.imageableWidth - w) / 2.0
                val y = pageFormat.imageableY + (pageFormat.imageableHeight - h) / 2.0
                g2.drawImage(image, x.toInt(), y.toInt(), w.toInt(), h.toInt(), null)
                Printable.PAGE_EXISTS
            }
            if (job.printDialog()) job.print()
        }.onFailure {
            withContext(Dispatchers.Main) { Toaster.show(str("passport_print_failed")) }
        }
    }
}

private suspend fun revealPassportInFolder(image: BufferedImage, passport: MusicPassport) {
    withContext(Dispatchers.IO) {
        runCatching {
            val file = writePassportTemp(image, passport)
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            when {
                isWindows -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                desktop?.isSupported(Desktop.Action.BROWSE_FILE_DIR) == true -> desktop.browseFileDirectory(file)
                else -> desktop?.open(file.parentFile)
            }
        }
    }
}

private fun writePassportTemp(image: BufferedImage, passport: MusicPassport): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "kittytune-passport").apply { mkdirs() }
    val safeNo = passport.passportNo.replace("·", "-").lowercase(Locale.ROOT)
    val file = File(dir, "kittytune-passport-$safeNo.png")
    file.outputStream().use { out -> ImageIO.write(image, "png", out) }
    return file
}

private class PassportImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any =
        if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
}

private fun copyPassportToClipboard(image: BufferedImage) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(PassportImageTransferable(image), null)
        Toaster.show(str("cover_image_copied"))
    }.onFailure {
        Toaster.show(str("passport_save_failed"))
    }
}

private suspend fun savePassportWithDialog(image: BufferedImage, passport: MusicPassport) {
    val safeNo = passport.passportNo.replace("·", "-").lowercase(Locale.ROOT)
    val defaultName = "kittytune-passport-$safeNo.png"
    val dialog = FileDialog(null as Frame?, str("passport_save_image"), FileDialog.SAVE).apply {
        file = defaultName
        isVisible = true
    }
    val chosen = dialog.files.firstOrNull() ?: return
    val target = if (chosen.name.endsWith(".png", ignoreCase = true)) chosen else File(chosen.parentFile, "${chosen.name}.png")
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            target.outputStream().use { out -> ImageIO.write(image, "png", out) }
            true
        }.getOrDefault(false)
    }
    Toaster.show(str(if (ok) "passport_saved" else "passport_save_failed"))
}

private suspend fun openPassportInViewer(image: BufferedImage, passport: MusicPassport) {
    withContext(Dispatchers.IO) {
        runCatching {
            val file = writePassportTemp(image, passport)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(PassportImageTransferable(image), null)
            if (Desktop.isDesktopSupported() && file.exists()) {
                runCatching { Desktop.getDesktop().open(file) }
            }
        }
    }
}
