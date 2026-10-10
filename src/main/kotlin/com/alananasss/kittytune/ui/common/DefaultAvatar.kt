package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import coil3.compose.AsyncImage
import com.alananasss.kittytune.domain.getHighResAvatarUrl
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

/**
 * A user's picture, or SoundCloud's grey silhouette when there is none or it fails to load.
 *
 * SoundCloud gives users without a picture `default_avatar_large.png`, and the usual
 * "large" -> "t500x500" upgrade turns that into a file that does not exist, so those avatars used to
 * stay an empty circle (issue #66). Default avatars skip the request and draw the silhouette locally.
 */
@Composable
fun UserAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val silhouette = rememberDefaultAvatarPainter()
    AsyncImage(
        model = url.getHighResAvatarUrl(),
        contentDescription = contentDescription,
        error = silhouette,
        fallback = silhouette,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
    )
}
