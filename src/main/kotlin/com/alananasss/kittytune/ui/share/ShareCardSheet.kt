package com.alananasss.kittytune.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.player.ArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File

/**
 * Preview of the share card with the styles the artwork allows (desktop port of Android PR #83): the song or
 * the lyrics face, a line picker (up to five lines), the cover as a QR code, and on desktop the card is copied
 * as a picture or saved as a PNG, and the lyrics can be copied as text.
 */
@Composable
fun ShareCardSheet(
    artwork: BufferedImage?,
    title: String,
    artist: String,
    trackUrl: String?,
    lyrics: List<String> = emptyList(),
    allLyrics: List<String> = emptyList(),
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val artworkBitmap = remember(artwork) { artwork?.toComposeImageBitmap() }
    val styles = remember(artwork) { artwork.toShareCardStyles() }
    var selected by remember(styles) { mutableStateOf(styles.first()) }
    var busy by remember { mutableStateOf(false) }
    var asQr by remember { mutableStateOf(false) }
    var lyricsFace by remember(lyrics) { mutableStateOf(false) }
    var qrCover by remember(artwork, trackUrl) { mutableStateOf<ImageBitmap?>(null) }

    // Starts with the lines around the playhead; the user picks any others (capped, so the card stays a card).
    var selectedLineIndices by remember(allLyrics, lyrics) {
        mutableStateOf(
            if (allLyrics.isEmpty()) emptySet()
            else lyrics.map { snippet -> allLyrics.indexOfFirst { it == snippet } }.filter { it >= 0 }.toSet()
        )
    }
    val selectedLyrics = remember(allLyrics, selectedLineIndices) {
        allLyrics.filterIndexed { index, _ -> index in selectedLineIndices }
    }
    val cardLyrics = selectedLyrics.takeIf { lyricsFace && it.isNotEmpty() }
        ?: lyrics.takeIf { lyricsFace && allLyrics.isEmpty() && it.isNotEmpty() }

    // Rendered once per track, off the main thread: the encode and the per-module draw add up.
    LaunchedEffect(artwork, trackUrl) {
        qrCover = trackUrl?.takeIf { it.isNotBlank() }?.let { url ->
            withContext(Dispatchers.Default) { QrCoverRenderer.render(content = url, cover = artwork, sizePx = 1024) }
        }
    }

    val renderCard: suspend () -> BufferedImage = {
        val qr = qrCover.takeIf { asQr && !lyricsFace }
        val style = selected
        withContext(Dispatchers.Default) {
            renderShareCard(artworkBitmap, title, artist, style, qr, cardLyrics)
        }
    }

    KittyModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(CARD_WIDTH).clip(RoundedCornerShape(20.dp))) {
                ShareCard(
                    artwork = artworkBitmap,
                    title = title,
                    artist = artist,
                    style = selected,
                    modifier = Modifier.fillMaxWidth(),
                    qrCover = qrCover.takeIf { asQr && !lyricsFace },
                    lyrics = cardLyrics,
                )
            }

            if (lyrics.isNotEmpty() || allLyrics.isNotEmpty()) {
                ExpressiveConnectedButtonGroup(
                    options = listOf(false, true),
                    selectedOption = lyricsFace,
                    onOptionSelected = { lyricsFace = it },
                    modifier = Modifier.padding(top = 16.dp),
                    labelProvider = { showLyrics ->
                        Text(str(if (showLyrics) "share_card_face_lyrics" else "share_card_face_song"))
                    },
                )
            }

            if (styles.size > 1) {
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    styles.forEach { style ->
                        val isSelected = style == selected
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(style.background)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { selected = style }
                        )
                    }
                }
            }

            if (qrCover != null && !lyricsFace) {
                FilterChip(
                    selected = asQr,
                    onClick = { asQr = !asQr },
                    label = { Text(str("share_card_qr")) },
                    leadingIcon = { Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            // The line picker: the lyrics face is only as good as the lines on it.
            if (lyricsFace && allLyrics.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            str("share_card_pick_lines"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${selectedLineIndices.size}/$MAX_SHARE_LINES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Plain column inside the dialog's own scroll: a lazy list cannot nest in it.
                    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        allLyrics.forEachIndexed { index, line ->
                            val isSelected = index in selectedLineIndices
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedLineIndices = when {
                                            isSelected -> selectedLineIndices - index
                                            selectedLineIndices.size < MAX_SHARE_LINES -> selectedLineIndices + index
                                            else -> selectedLineIndices
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape).background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            if (lyricsFace && selectedLyrics.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val text = shareText(selectedLyrics, title, artist, trackUrl)
                        StringSelection(text).let { Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it) }
                        Toaster.show(str("share_card_text_copied"))
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Icon(Icons.Rounded.FormatQuote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("share_card_copy_text"))
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            val image = renderCard()
                            val saved = withContext(Dispatchers.IO) { saveCard(image, title) }
                            busy = false
                            if (saved != null) Toaster.show(str("share_card_saved", saved.name))
                        }
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("share_card_save_image"))
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            val image = renderCard()
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
                            busy = false
                            Toaster.show(str("artist_share_image_copied"))
                        }
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("artist_share_copy_image"))
                }
            }
        }
    }
}

/** Five lines is the most a 4:5 card carries while staying a card. */
private const val MAX_SHARE_LINES = 5

/** The card's width in the preview and in the export, so both are the same composition. */
private val CARD_WIDTH = 400.dp

/** Export density: a 400 dp card becomes 1080 x 1350 px. */
private const val EXPORT_DENSITY = 2.7f

/** The lyrics, then "Title — Artist" and the link, as the text a listener pastes. */
internal fun shareText(lines: List<String>, title: String, artist: String, trackUrl: String?): String = buildString {
    lines.forEach { appendLine(it) }
    appendLine()
    append(title)
    append(" — ")
    append(artist)
    if (!trackUrl.isNullOrBlank()) {
        appendLine()
        append(trackUrl)
    }
}

/** Renders the card offscreen at export size: exactly what the preview shows, at a higher density. */
internal fun renderShareCard(
    artwork: ImageBitmap?,
    title: String,
    artist: String,
    style: ShareCardStyle,
    qrCover: ImageBitmap?,
    lyrics: List<String>?,
): BufferedImage {
    val widthPx = (CARD_WIDTH.value * EXPORT_DENSITY).toInt()
    val heightPx = (widthPx * 5f / 4f).toInt()
    val scene = ImageComposeScene(width = widthPx, height = heightPx, density = Density(EXPORT_DENSITY)) {
        Box(Modifier.width(CARD_WIDTH)) {
            ShareCard(artwork, title, artist, style, Modifier.fillMaxWidth(), qrCover, lyrics)
        }
    }
    try {
        scene.render(0L).close()
        // A second frame so the type's shrink-to-fit pass has settled.
        return scene.render(1_000_000_000L).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                javax.imageio.ImageIO.read(data.bytes.inputStream())
            }
        }
    } finally {
        scene.close()
    }
}

/** Asks where to save the card and writes it as a PNG; null when the listener cancels. */
private fun saveCard(image: BufferedImage, title: String): File? {
    val safeName = title.replace(Regex("""[\\/:*?"<>|]"""), "").trim().ifBlank { "KittyTune" }
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, str("share_card_save_image"), java.awt.FileDialog.SAVE)
    dialog.file = "$safeName.png"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val file = File(dialog.directory, if (name.endsWith(".png", ignoreCase = true)) name else "$name.png")
    javax.imageio.ImageIO.write(image, "png", file)
    return file
}

private class ImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor?) = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any =
        if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
}

/**
 * The styles the artwork offers: its darkest shade first (a cover usually sits best on it), then the brighter
 * ones; text is dark or light per shade by luminance.
 */
private fun BufferedImage?.toShareCardStyles(): List<ShareCardStyle> {
    val image = this ?: return listOf(ShareCardStyle.Neutral)
    val shades = runCatching { ArtworkPalette.meshPalette(image, count = 4) }.getOrDefault(emptyList())
    if (shades.isEmpty()) return listOf(ShareCardStyle.Neutral)
    return shades.asReversed()
        .map { color -> ShareCardStyle(background = color, onBackground = if (color.isLight()) Color(0xFF101010) else Color.White) }
        .distinctBy { it.background.value }
}

/** Whether text on this colour should be dark (Rec. 709 luma). */
private fun Color.isLight(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) > 0.6f
