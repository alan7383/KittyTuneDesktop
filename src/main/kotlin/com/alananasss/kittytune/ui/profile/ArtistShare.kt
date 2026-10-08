package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import com.alananasss.kittytune.domain.isDefaultAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO

/** What the share card shows about an artist. */
internal class ArtistShareCard(
    val name: String,
    val followers: String?,
    val avatarUrl: String?,
    val topTracks: List<String>,
    val accentArgb: Int,
    val link: String,
)

private const val CARD_W = 900
private const val CARD_H = 560

/**
 * The share window of an artist's page: a card with the portrait, the name, the followers and the three most played
 * songs, which can be copied as a picture, and the plain link underneath.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtistShareDialog(card: ArtistShareCard, onDismiss: () -> Unit) {
    var image by remember { mutableStateOf<BufferedImage?>(null) }
    LaunchedEffect(card) { image = withContext(Dispatchers.IO) { renderArtistCard(card) } }
    val bitmap = remember(image) { image?.toComposeImageBitmap() }

    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("artist_share_title"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val shape = RoundedCornerShape(20.dp)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(shape),
                    )
                } else {
                    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                    }
                }
                Button(
                    onClick = {
                        image?.let {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(it), null)
                            Toaster.show(str("artist_share_image_copied"))
                        }
                    },
                    enabled = image != null,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(str("artist_share_copy_image")) }
                FilledTonalButton(
                    onClick = {
                        StringSelection(card.link).let { Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it) }
                        Toaster.show(str("artist_link_copied"))
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(card.link, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(1.dp))
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(str("btn_close")) } },
    )
}

private class ImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor?) = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any =
        if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
}

/** Paints the card with plain Java2D: it needs no window, and what is copied is exactly what the dialog shows. */
internal fun renderArtistCard(card: ArtistShareCard): BufferedImage {
    val accent = AwtColor(card.accentArgb, false)
    val dark = AwtColor(16, 16, 20)
    val out = BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.clip = RoundRectangle2D.Float(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), 48f, 48f)
        g.paint = GradientPaint(0f, 0f, mix(dark, accent, 0.55f), CARD_W.toFloat(), CARD_H.toFloat(), mix(dark, accent, 0.12f))
        g.fillRect(0, 0, CARD_W, CARD_H)

        val avatar = card.avatarUrl?.let(::loadImage)
        val size = 220
        val ax = 56
        val ay = 56
        g.clip = Ellipse2D.Float(ax.toFloat(), ay.toFloat(), size.toFloat(), size.toFloat())
        if (avatar != null) g.drawImage(avatar, ax, ay, size, size, null)
        else { g.color = mix(dark, accent, 0.5f); g.fillRect(ax, ay, size, size) }
        g.clip = null

        val textX = ax + size + 40
        g.color = AwtColor.WHITE
        g.font = Font("SansSerif", Font.BOLD, 54)
        g.drawString(fit(g, card.name, CARD_W - textX - 48), textX, ay + 104)
        card.followers?.let {
            g.color = AwtColor(255, 255, 255, 190)
            g.font = Font("SansSerif", Font.PLAIN, 28)
            g.drawString(it, textX, ay + 160)
        }

        var y = 350
        g.color = accent.brighter()
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString(card.topTracks.takeIf { it.isNotEmpty() }?.let { "TOP" } ?: "", ax, y - 24)
        card.topTracks.take(3).forEachIndexed { i, title ->
            g.color = accent
            g.font = Font("SansSerif", Font.BOLD, 34)
            g.drawString("${i + 1}", ax, y + 28)
            g.color = AwtColor.WHITE
            g.font = Font("SansSerif", Font.PLAIN, 34)
            g.drawString(fit(g, title, CARD_W - ax - 48 - 56), ax + 56, y + 28)
            y += 60
        }
        g.color = AwtColor(255, 255, 255, 120)
        g.font = Font("SansSerif", Font.BOLD, 20)
        g.drawString("KittyTune", CARD_W - 150, CARD_H - 28)
    } finally {
        g.dispose()
    }
    return out
}

private fun mix(a: AwtColor, b: AwtColor, t: Float) = AwtColor(
    (a.red + (b.red - a.red) * t).toInt(),
    (a.green + (b.green - a.green) * t).toInt(),
    (a.blue + (b.blue - a.blue) * t).toInt(),
)

/** [text] cut with an ellipsis so it fits in [maxWidth] pixels in the graphics' current font. */
private fun fit(g: java.awt.Graphics2D, text: String, maxWidth: Int): String {
    val metrics = g.fontMetrics
    if (metrics.stringWidth(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && metrics.stringWidth(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end).trimEnd() + "…"
}

private fun loadImage(url: String): BufferedImage? = runCatching {
    val connection = URI(url).toURL().openConnection().apply {
        setRequestProperty("User-Agent", "Mozilla/5.0")
        connectTimeout = 10_000
        readTimeout = 15_000
    }
    connection.getInputStream().use { ImageIO.read(it) }
}.getOrNull()

/** The card for [user], with the songs the page already lists and the link the old share button copied. */
@Composable
internal fun rememberArtistShareCard(user: com.alananasss.kittytune.domain.User, profileViewModel: ProfileViewModel): ArtistShareCard {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val top = profileViewModel.popularTracks.ifEmpty { profileViewModel.allTracks }.mapNotNull { it.title }.take(3)
    val followers = user.followersCount.takeIf { it > 0 && !profileViewModel.isSpotifyProfile }?.let {
        "${java.text.NumberFormat.getIntegerInstance(com.alananasss.kittytune.core.Strings.locale()).format(it)} ${str("profile_followers")}"
    }
    val portrait = user.avatarUrl?.takeIf { !it.isDefaultAvatar() }?.getHighResAvatarUrl()
    val link = user.permalinkUrl ?: if (profileViewModel.isSpotifyProfile) {
        "https://open.spotify.com/artist/${user.permalink}"
    } else {
        "https://soundcloud.com/${user.permalink ?: user.username?.replace(" ", "")?.lowercase() ?: "user"}"
    }
    return ArtistShareCard(user.username ?: str("unknown_artist"), followers, portrait, top, accent, link)
}
