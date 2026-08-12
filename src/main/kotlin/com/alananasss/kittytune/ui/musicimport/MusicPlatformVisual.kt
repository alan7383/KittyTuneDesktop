package com.alananasss.kittytune.ui.musicimport

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.data.musicimport.MusicApi

data class MusicPlatformVisual(
    val icon: ImageVector,
    val logoRes: String,
    val likedArtworkRes: String,
    val color: Color
)

fun MusicApi.visual(): MusicPlatformVisual = when (this) {
    MusicApi.SPOTIFY -> MusicPlatformVisual(
        icon = Icons.Rounded.MusicNote,
        logoRes = "drawable/ic_logo_spotify.xml",
        likedArtworkRes = "drawable/ic_likes_spotify.xml",
        color = Color(0xFF1DB954)
    )
    MusicApi.APPLE_MUSIC -> MusicPlatformVisual(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        logoRes = "drawable/ic_logo_apple_music.xml",
        likedArtworkRes = "drawable/ic_likes_apple_music.xml",
        color = Color(0xFFFA243C)
    )
    MusicApi.YOUTUBE_MUSIC -> MusicPlatformVisual(
        icon = Icons.Rounded.PlayCircle,
        logoRes = "drawable/ic_logo_youtube_music.xml",
        likedArtworkRes = "drawable/ic_logo_youtube_music.xml",
        color = Color(0xFFFF0000)
    )
    MusicApi.DEEZER -> MusicPlatformVisual(
        icon = Icons.Rounded.Equalizer,
        logoRes = "drawable/ic_logo_deezer.xml",
        likedArtworkRes = "drawable/ic_likes_deezer.xml",
        color = Color(0xFFA238FF)
    )
    MusicApi.TIDAL -> MusicPlatformVisual(
        icon = Icons.Rounded.GraphicEq,
        logoRes = "drawable/ic_logo_tidal.xml",
        likedArtworkRes = "drawable/ic_likes_tidal.xml",
        color = Color(0xFF4E4E4E)
    )
    MusicApi.AMAZON_MUSIC -> MusicPlatformVisual(
        icon = Icons.Rounded.Cloud,
        logoRes = "drawable/ic_logo_amazon_music.xml",
        likedArtworkRes = "drawable/ic_likes_amazon_music.xml",
        color = Color(0xFF25D1DA)
    )
    MusicApi.BOOMPLAY -> MusicPlatformVisual(
        icon = Icons.Rounded.Album,
        logoRes = "drawable/ic_logo_boomplay.xml",
        likedArtworkRes = "drawable/ic_logo_boomplay.xml",
        color = Color(0xFF00B14F)
    )
}
