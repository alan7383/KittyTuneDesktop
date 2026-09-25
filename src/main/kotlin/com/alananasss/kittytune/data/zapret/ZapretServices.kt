package com.alananasss.kittytune.data.zapret

/**
 * The services KittyTune talks to, each with a URL to probe and the domains zapret has to handle for it.
 *
 * Domains are the registrable ones: zapret's host lists match subdomains, so `sndcdn.com` covers every
 * `i1.`, `cf-hls-media.` and so on.
 */
data class ZapretService(
    val id: String,
    val name: String,
    /** Any HTTP answer from here, even an error status, means the service is reachable. */
    val probeUrl: String,
    val domains: List<String>,
)

object ZapretServices {
    val ALL = listOf(
        ZapretService(
            "soundcloud", "SoundCloud", "https://api-v2.soundcloud.com/",
            listOf("soundcloud.com", "sndcdn.com", "soundcloud.cloud"),
        ),
        ZapretService(
            "youtube", "YouTube Music", "https://music.youtube.com/",
            listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com", "youtubei.googleapis.com"),
        ),
        ZapretService(
            "spotify", "Spotify", "https://open.spotify.com/",
            listOf("spotify.com", "scdn.co", "spotifycdn.com"),
        ),
        ZapretService(
            "apple", "Apple Music", "https://music.apple.com/",
            listOf("music.apple.com", "amp-api.music.apple.com", "mzstatic.com"),
        ),
        ZapretService("deezer", "Deezer", "https://api.deezer.com/", listOf("deezer.com", "dzcdn.net")),
        ZapretService("tidal", "TIDAL", "https://tidal.com/", listOf("tidal.com", "tidalhifi.com")),
        ZapretService("qobuz", "Qobuz", "https://www.qobuz.com/", listOf("qobuz.com")),
        ZapretService(
            "lyrics", "Lyrics (LRCLIB, Musixmatch, Genius…)", "https://lrclib.net/",
            listOf("lrclib.net", "musixmatch.com", "genius.com", "paxsenix.org", "boidu.dev", "megalobiz.com", "simpmusic.org"),
        ),
        ZapretService(
            "helpers", "Helper APIs (Hugging Face, Render, Vercel)", "https://huggingface.co/",
            listOf("hf.space", "huggingface.co", "onrender.com", "vercel.app", "workers.dev"),
        ),
        ZapretService("discord", "Discord", "https://discord.com/", listOf("discord.com", "discordapp.com", "discord.gg", "discord.media")),
        ZapretService("github", "GitHub (updates)", "https://api.github.com/", listOf("github.com", "githubusercontent.com")),
    )
}
