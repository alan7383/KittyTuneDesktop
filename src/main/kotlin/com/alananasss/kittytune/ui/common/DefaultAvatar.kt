package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import org.xml.sax.InputSource

/**
 * The round "no avatar" artwork, parsed once for the whole app.
 *
 * `painterResource(path)` only remembers per call site *instance*, so every artist card scrolled into
 * view opened the resource and parsed its XML on the UI thread — twice, as `error` and as `fallback`.
 * That is work done exactly while a list is scrolling, which is when a dropped frame shows.
 */
private val defaultAvatarVector: ImageVector by lazy {
    val stream = checkNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream(DEFAULT_AVATAR_RESOURCE)
    ) { "missing $DEFAULT_AVATAR_RESOURCE" }
    stream.use { loadXmlImageVector(InputSource(it), Density(1f)) }
}

private const val DEFAULT_AVATAR_RESOURCE = "drawable/ic_default_user_artwork_placeholder_round.xml"

@Composable
fun rememberDefaultAvatarPainter(): Painter = rememberVectorPainter(defaultAvatarVector)
