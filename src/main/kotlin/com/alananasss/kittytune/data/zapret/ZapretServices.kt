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
    /**
     * The endpoints the app actually talks to. All of them have to answer with data: a site's front page can
     * load while the API behind search and playback is blocked, which is what made "works" wrong before.
     */
    val probeUrls: List<String>,
    val domains: List<String>,
)

object ZapretServices {
    val ALL = listOf(
        ZapretService(
            "soundcloud", "SoundCloud",
            listOf("https://soundcloud.com/", "https://api-v2.soundcloud.com/search?q=a", "https://i1.sndcdn.com/"),
            listOf("soundcloud.com", "sndcdn.com", "soundcloud.cloud"),
        ),
        ZapretService(
            "youtube", "YouTube Music",
            listOf("https://music.youtube.com/", "https://www.youtube.com/youtubei/v1/search", "https://i.ytimg.com/"),
            listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com", "youtubei.googleapis.com"),
        ),
        ZapretService(
            "spotify", "Spotify",
            listOf("https://open.spotify.com/", "https://api-partner.spotify.com/pathfinder/v1/query", "https://spclient.wg.spotify.com/", "https://i.scdn.co/"),
            listOf("spotify.com", "scdn.co", "spotifycdn.com"),
        ),
        ZapretService(
            "apple", "Apple Music",
            listOf("https://music.apple.com/", "https://amp-api.music.apple.com/v1/catalog/us/search?term=a"),
            listOf("music.apple.com", "amp-api.music.apple.com", "mzstatic.com"),
        ),
        ZapretService("deezer", "Deezer", listOf("https://api.deezer.com/chart", "https://www.deezer.com/"), listOf("deezer.com", "dzcdn.net")),
        ZapretService("tidal", "TIDAL", listOf("https://tidal.com/", "https://api.tidal.com/v1/"), listOf("tidal.com", "tidalhifi.com")),
        ZapretService("qobuz", "Qobuz", listOf("https://www.qobuz.com/", "https://www.qobuz.com/api.json/0.2/"), listOf("qobuz.com")),
        ZapretService(
            "lyrics", "Lyrics (LRCLIB, Musixmatch, Genius…)",
            listOf("https://lrclib.net/api/search?q=hello", "https://apic-desktop.musixmatch.com/", "https://api.genius.com/"),
            listOf("lrclib.net", "musixmatch.com", "genius.com", "paxsenix.org", "boidu.dev", "megalobiz.com", "simpmusic.org"),
        ),
        ZapretService(
            "helpers", "Helper APIs (Hugging Face, Render, Vercel)",
            listOf("https://huggingface.co/", "https://vercel.app/"),
            listOf("hf.space", "huggingface.co", "onrender.com", "vercel.app", "workers.dev"),
        ),
        ZapretService("discord", "Discord", listOf("https://discord.com/api/v9/gateway", "https://cdn.discordapp.com/"), listOf("discord.com", "discordapp.com", "discord.gg", "discord.media")),
        ZapretService("github", "GitHub (updates)", listOf("https://api.github.com/", "https://objects.githubusercontent.com/"), listOf("github.com", "githubusercontent.com")),
    )
}
