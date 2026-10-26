package com.alananasss.kittytune.data.qobuz

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.search.ProviderSearchRanker
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class QobuzSearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<User> = emptyList()
)

object QobuzSearchRepository {
    private const val DEFAULT_BASE = "https://qobuz-dll.vercel.app"
    private const val CATALOG_API_BASE = "https://www.qobuz.com/api.json/0.2"
    private const val CATALOG_APP_ID = "712109809"

    /**
     * The official Qobuz app identifies itself with its own id and secret. The
     * radio endpoints (radio/track, radio/album, radio/artist) and the OAuth
     * login only accept those, so they are needed to reach the real radio.
     */
    private const val OFFICIAL_APP_ID = "312369995"
    private const val OFFICIAL_APP_SECRET = "e79f8b9be485692b0e5f9dd895826368"

    // Shares ProxyManager's pool and dispatcher; only the timeouts are this repository's own.
    private val client = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient().newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun getBaseUrls(): List<String> {
        val custom = runCatching { PlayerPreferences().getQobuzCustomInstances() }.getOrNull()
        val customList = custom.orEmpty()
            .split('\n', '\r', ',', ';', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.contains("://")) it.removeSuffix("/") else "https://${it.removeSuffix("/")}" }

        return (customList + DEFAULT_BASE).distinct()
    }

    suspend fun search(query: String, limit: Int = 50): QobuzSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext QobuzSearchResult()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val baseUrls = getBaseUrls()

        for (base in baseUrls) {
            val url = "$base/api/get-music?q=$encodedQuery&offset=0"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue

            val tracks = data.optJSONObject("tracks")?.optJSONArray("items")?.mapObjects { it.toQobuzTrack() } ?: emptyList()
            val albums = data.optJSONObject("albums")?.optJSONArray("items")?.mapObjects { it.toQobuzAlbum() } ?: emptyList()
            val artists = data.optJSONObject("artists")?.optJSONArray("items")?.mapObjects { it.toQobuzArtist() } ?: emptyList()

            return@withContext QobuzSearchResult(
                tracks = ProviderSearchRanker.rankTracks(query, tracks),
                albums = albums,
                playlists = emptyList(), // Qobuz public resolver does not support fetching playlist tracks
                artists = artists
            )
        }

        QobuzSearchResult()
    }

    /**
     * Resolves one exact Qobuz track. A pasted open.qobuz.com link carries an id,
     * not a title, so searching its text would only return unrelated songs.
     *
     * The catalogue's own track/get endpoint needs an app id but no listener token;
     * it is tried first so this works even when the configured qobuz-dll instance is
     * down. Custom resolver instances are kept as a fallback for deployments that
     * expose a get-track route.
     */
    suspend fun getTrack(trackId: String): Track? = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("qobuz:track:").trim()
        if (cleanId.isBlank()) return@withContext null
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")

        fetchJson("$CATALOG_API_BASE/track/get?track_id=$encodedId&app_id=$CATALOG_APP_ID")?.let { root ->
            val data = root.optJSONObject("data") ?: root
            data.toQobuzTrack()?.let { return@withContext it }
        }

        for (base in getBaseUrls()) {
            val root = fetchJson("$base/api/get-track?track_id=$encodedId") ?: continue
            val data = root.optJSONObject("data") ?: continue
            data.toQobuzTrack()?.let { return@withContext it }
        }

        null
    }

    /**
     * Logs in to Qobuz as the official app (using the app's own app_id) and
     * stores the user_auth_token so the official radio endpoints
     * (radio/track, radio/album, radio/artist) become usable.
     *
     * Returns the token on success, null on failure.
     */
    suspend fun login(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val signature = officialSignature(
            path = listOf("oauth2", "login"),
            params = mapOf("username" to email.trim(), "password" to password),
            timestamp = timestamp,
        )
        val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
        val encodedPassword = URLEncoder.encode(password, "UTF-8")
        val root = fetchJson(
            "$CATALOG_API_BASE/oauth2/login?username=$encodedEmail&password=$encodedPassword" +
                "&app_id=$OFFICIAL_APP_ID&request_ts=$timestamp&request_sig=$signature"
        ) ?: return@withContext null
        val token = root.optString("user_auth_token").takeIf { it.isNotBlank() }
        if (token != null) {
            com.alananasss.kittytune.data.local.PlayerPreferences().setQobuzUserAuthToken(token)
        }
        token
    }

    /**
     * The signature Qobuz asks for on its signed endpoints: the path after the
     * version, then the parameters sorted by name, then the timestamp, then the
     * app secret — hashed with MD5.
     */
    private fun officialSignature(
        path: List<String>,
        params: Map<String, String>,
        timestamp: Long,
    ): String {
        val payload = buildString {
            append(path.joinToString(""))
            params.keys.sorted().forEach { key -> append(key).append(params[key]) }
            append(timestamp)
            append(OFFICIAL_APP_SECRET)
        }
        return md5(payload)
    }

    private fun md5(value: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Logs in to Qobuz with a Google identity token, the same call the official
     * app makes once Google sign-in has produced one.
     */
    suspend fun loginWithGoogle(identityToken: String): String? =
        loginWithOAuth("google", mapOf("identity_token" to identityToken.trim()))

    /**
     * Logs in to Qobuz with a Facebook access token, the same call the official
     * app makes after the Facebook login dialog.
     */
    suspend fun loginWithFacebook(accessToken: String, userId: String, expiresAt: Long): String? =
        loginWithOAuth(
            "facebook",
            mapOf(
                "facebook_access_token" to accessToken.trim(),
                "facebook_access_token_expires" to expiresAt.toString(),
                "facebook_user_id" to userId.trim(),
            ),
        )

    /**
     * The Facebook id of the account behind an access token — Qobuz wants both
     * the token and the id when it logs a Facebook session in.
     */
    suspend fun fetchFacebookUserId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val root = fetchJson(
            "https://graph.facebook.com/me?fields=id&access_token=${accessToken.urlEncoded()}"
        ) ?: return@withContext null
        root.optString("id").takeIf { it.isNotBlank() }
    }

    /** The OAuth2 endpoint of the official app, signed the way it expects. */
    private suspend fun loginWithOAuth(
        endpoint: String,
        params: Map<String, String>,
    ): String? = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val signature = officialSignature(listOf("oauth2", endpoint), params, timestamp)
        val query = params.entries.joinToString("&") { (key, value) ->
            "${key.urlEncoded()}=${value.urlEncoded()}"
        }
        val root = fetchJson(
            "$CATALOG_API_BASE/oauth2/$endpoint?$query" +
                "&app_id=$OFFICIAL_APP_ID&request_ts=$timestamp&request_sig=$signature"
        ) ?: return@withContext null
        val token = root.optString("user_auth_token").takeIf { it.isNotBlank() }
        if (token != null) {
            com.alananasss.kittytune.data.local.PlayerPreferences().setQobuzUserAuthToken(token)
        }
        token
    }

    /**
     * The official Qobuz radio around a track. Requires a logged-in Qobuz
     * user (see [login]); without one the endpoint answers 401 and this
     * returns null, so the catalogue-derived radio is used instead.
     */
    suspend fun getOfficialRadio(trackId: String): List<Track> = withContext(Dispatchers.IO) {
        val token = com.alananasss.kittytune.data.local.PlayerPreferences().getQobuzUserAuthToken()
        if (token.isNullOrBlank()) return@withContext emptyList()
        val cleanId = trackId.removePrefix("qobuz:track:").trim()
        if (cleanId.isBlank()) return@withContext emptyList()
        val root = fetchJson(
            "$CATALOG_API_BASE/radio/track?track_id=${cleanId.urlEncoded()}" +
                "&app_id=$OFFICIAL_APP_ID&user_auth_token=$token",
            bearerToken = token,
        ) ?: return@withContext emptyList()
        val data = root.optJSONObject("data") ?: root
        val items = data.optJSONObject("tracks")?.optJSONArray("items")
            ?: root.optJSONObject("tracks")?.optJSONArray("items")
            ?: return@withContext emptyList()
        items.mapObjects { it.toQobuzTrack() }
    }

    /**
     * A radio around a Qobuz track, built from the catalogue itself: the album
     * the track belongs to, the artist's own tracks, and a few of the artists
     * Qobuz itself lists as similar. Qobuz has no radio endpoint of its own, but
     * everything here comes from the same catalogue the track was found in.
     */
    suspend fun getRadio(trackId: String, limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        // Prefer the official radio when the user is logged in to Qobuz.
        val official = getOfficialRadio(trackId)
        if (official.isNotEmpty()) return@withContext official
        val cleanId = trackId.removePrefix("qobuz:track:").trim()
        if (cleanId.isBlank()) return@withContext emptyList()

        val seed = fetchJson("$CATALOG_API_BASE/track/get?track_id=${cleanId.urlEncoded()}&app_id=$CATALOG_APP_ID")
        val albumId = seed?.optJSONObject("album")?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
        val artistId = seed?.optJSONObject("performer")?.optLong("id")
            ?: seed?.optJSONObject("artist")?.optLong("id")
            ?: 0L

        val tracks = mutableListOf<Track>()

        albumId?.let { id ->
            fetchJson("$CATALOG_API_BASE/album/get?album_id=${id.urlEncoded()}&app_id=$CATALOG_APP_ID")
                ?.optJSONObject("tracks")
                ?.optJSONArray("items")
                ?.mapObjects { it.toQobuzTrack() }
                ?.let { tracks.addAll(it) }
        }

        if (artistId > 0L) {
            tracks.addAll(artistTopTracks(artistId))
            similarArtistIds(artistId).take(3).forEach { similar ->
                tracks.addAll(artistTopTracks(similar))
            }
            // A small artist has neither many tracks nor similar artists, so the
            // station would be a handful of songs: the artist's own albums fill it.
            if (tracks.size < 12) {
                artistAlbumIds(artistId).take(2).forEach { albumId ->
                    fetchJson("$CATALOG_API_BASE/album/get?album_id=${albumId.urlEncoded()}&app_id=$CATALOG_APP_ID")
                        ?.optJSONObject("tracks")
                        ?.optJSONArray("items")
                        ?.mapObjects { it.toQobuzTrack() }
                        ?.let { tracks.addAll(it) }
                }
            }
        }

        tracks.distinctBy { it.id }.take(limit)
    }

    /** The albums the catalogue lists for an artist. */
    private suspend fun artistAlbumIds(artistId: Long): List<String> =
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$artistId&app_id=$CATALOG_APP_ID&extra=albums")
            ?.optJSONObject("albums")
            ?.optJSONArray("items")
            ?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            }
            ?: emptyList()

    /** The tracks the catalogue lists for an artist. */
    private suspend fun artistTopTracks(artistId: Long): List<Track> =
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$artistId&app_id=$CATALOG_APP_ID&extra=tracks")
            ?.optJSONObject("tracks")
            ?.optJSONArray("items")
            ?.mapObjects { it.toQobuzTrack() }
            ?: emptyList()

    /** The artists the catalogue considers close to this one. */
    private suspend fun similarArtistIds(artistId: Long): List<Long> =
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$artistId&app_id=$CATALOG_APP_ID")
            ?.optJSONArray("similar_artist_ids")
            ?.let { array ->
                (0 until array.length()).mapNotNull { array.optLong(it).takeIf { id -> id > 0L } }
            }
            ?: emptyList()

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    suspend fun getAlbum(albumId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("qobuz:album:").trim()
        if (cleanId.isBlank()) return@withContext null
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")

        fun mapAlbum(data: JSONObject): Playlist? {
            val title = data.optString("title")
            if (title.isBlank()) return null
            val tracksArray = data.optJSONObject("tracks")?.optJSONArray("items")
            val tracks = tracksArray?.mapObjects { it.toQobuzTrack(albumOverride = data) } ?: emptyList()

            val artistObj = data.optJSONObject("artist")
            val artistName = artistObj?.extractName() ?: "Qobuz"
            val artistId = artistObj?.optLong("id") ?: 0L
            val imageObj = data.optJSONObject("image")
            val cover = imageObj?.optString("large")
                ?: imageObj?.optString("small")
                ?: imageObj?.optString("thumbnail")
            val stableId = abs(("qobuz:album:$cleanId").hashCode().toLong())

            return Playlist(
                id = stableId,
                title = title,
                artworkUrl = cover,
                calculatedArtworkUrl = cover,
                trackCount = data.optInt("tracks_count", tracks.size),
                user = User(
                    id = artistId,
                    username = artistName,
                    avatarUrl = null,
                    urn = "qobuz:artist:$artistId"
                ),
                tracks = tracks,
                isAlbum = true,
                releaseDate = data.optString("release_date_original"),
                permalink = cleanId,
                permalinkUrl = data.optString("url").ifBlank { "https://www.qobuz.com/album/$cleanId" },
                urn = "qobuz:album:$cleanId"
            )
        }

        fetchJson("$CATALOG_API_BASE/album/get?album_id=$encodedId&app_id=$CATALOG_APP_ID")?.let { root ->
            val data = root.optJSONObject("data") ?: root
            mapAlbum(data)?.let { return@withContext it }
        }

        for (base in getBaseUrls()) {
            val url = "$base/api/get-album?album_id=$encodedId"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue
            mapAlbum(data)?.let { return@withContext it }
        }

        null
    }

    suspend fun getArtist(artistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("qobuz:artist:").trim()
        val baseUrls = getBaseUrls()

        val encodedId = URLEncoder.encode(cleanId, "UTF-8")
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$encodedId&app_id=$CATALOG_APP_ID&extra=tracks,albums")?.let { artist ->
            val topTracks = artist.optJSONObject("tracks")
                ?.optJSONArray("items")
                ?.mapObjects { it.toQobuzTrack() }
                ?: emptyList()
            val name = artist.extractName() ?: artist.optString("name")
            val picture = extractArtistPicture(artist) ?: topTracks.firstOrNull()?.artworkUrl
            val idLong = artist.optLong("id")
            val stableId = abs(("qobuz:artist:$cleanId").hashCode().toLong())
            if (name.isNotBlank()) {
                return@withContext Playlist(
                    id = stableId,
                    title = name,
                    artworkUrl = picture,
                    calculatedArtworkUrl = picture,
                    trackCount = topTracks.size,
                    user = User(
                        id = idLong,
                        username = name,
                        avatarUrl = picture,
                        permalink = cleanId,
                        urn = "qobuz:artist:$cleanId"
                    ),
                    tracks = topTracks,
                    isAlbum = false,
                    permalink = cleanId,
                    permalinkUrl = artist.optString("url").ifBlank { "https://www.qobuz.com/artist/$cleanId" },
                    urn = "qobuz:artist:$cleanId"
                )
            }
        }

        for (base in baseUrls) {
            val url = "$base/api/get-artist?artist_id=$cleanId"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue
            val artist = data.optJSONObject("artist") ?: continue

            val topTracksArray = artist.optJSONArray("top_tracks")
            val topTracks = topTracksArray?.mapObjects { it.toQobuzTrack() } ?: emptyList()

            val name = artist.extractName() ?: artist.optString("name")
            val picture = extractArtistPicture(artist) ?: topTracks.firstOrNull()?.artworkUrl
            val idLong = artist.optLong("id")
            val stableId = abs(("qobuz:artist:$cleanId").hashCode().toLong())

            return@withContext Playlist(
                id = stableId,
                title = name,
                artworkUrl = picture,
                calculatedArtworkUrl = picture,
                trackCount = topTracks.size,
                user = User(
                    id = idLong,
                    username = name,
                    avatarUrl = picture,
                    permalink = cleanId,
                    urn = "qobuz:artist:$cleanId"
                ),
                tracks = topTracks,
                isAlbum = false,
                permalink = cleanId,
                permalinkUrl = "https://www.qobuz.com/artist/$cleanId",
                urn = "qobuz:artist:$cleanId"
            )
        }

        null
    }

    suspend fun getPlaylist(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("qobuz:playlist:").trim()
        if (cleanId.isBlank()) return@withContext null
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")

        // The catalogue endpoint resolves public playlists directly, including
        // their tracks, without needing a listener token or the custom resolver.
        fetchJson("$CATALOG_API_BASE/playlist/get?playlist_id=$encodedId&app_id=$CATALOG_APP_ID&extra=tracks")?.let { root ->
            val data = root.optJSONObject("data") ?: root
            data.toQobuzPlaylist()?.let { return@withContext it }
        }

        val searchRes = search(cleanId, limit = 10)
        val matched = searchRes.playlists.firstOrNull { it.permalink == cleanId || it.urn == "qobuz:playlist:$cleanId" }
        matched
    }

    private fun JSONObject.extractName(): String? {
        val direct = optString("name").trim()
        if (direct.isNotBlank() && !direct.startsWith("{") && !direct.equals("null", ignoreCase = true)) {
            return direct
        }
        val nested = optJSONObject("name")?.optString("display")?.trim()
        if (!nested.isNullOrBlank() && !nested.equals("null", ignoreCase = true)) {
            return nested
        }
        return null
    }

    private fun JSONObject.toQobuzTrack(albumOverride: JSONObject? = null): Track? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val title = optString("title")
        if (title.isBlank()) return null
        val version = optString("version")
        val displayTitle = if (version.isNotBlank() && version != "null") "$title ($version)" else title

        val performerObj = optJSONObject("performer")
        val trackArtistObj = optJSONObject("artist")
        val albumObj = albumOverride ?: optJSONObject("album")
        val albumArtistObj = albumObj?.optJSONObject("artist")
        val artistName = performerObj?.extractName()
            ?: trackArtistObj?.extractName()
            ?: albumArtistObj?.extractName()
            ?: "Unknown Artist"
        val artistId = performerObj?.optLong("id")
            ?: trackArtistObj?.optLong("id")
            ?: albumArtistObj?.optLong("id")
            ?: 0L

        val albumImageObj = albumObj?.optJSONObject("image") ?: optJSONObject("image")
        val cover = albumImageObj?.optString("large")
            ?: albumImageObj?.optString("small")
            ?: albumImageObj?.optString("thumbnail")

        val durationSec = optLong("duration")
        val isrc = optString("isrc").takeIf { it.isNotBlank() && it != "null" }
        val explicit = optBoolean("parental_warning", false)
        val permalink = "qobuz:track:$id"
        val stableId = abs(permalink.hashCode().toLong())

        val specs = optString("maximum_technical_specifications").takeIf { it.isNotBlank() }

        return Track(
            id = stableId,
            title = displayTitle,
            artworkUrl = cover,
            durationMs = durationSec * 1000L,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                permalink = artistId.toString(),
                urn = "qobuz:artist:$artistId"
            ),
            publisherMetadata = TrackPublisherMetadata(
                artist = artistName,
                albumTitle = albumObj?.optString("title"),
                albumId = albumObj?.optString("id"),
                isrc = isrc,
                explicit = explicit,
                publisher = specs
            ),
            permalink = permalink,
            permalinkUrl = optString("url").ifBlank { "https://open.qobuz.com/track/$id" },
            source = "qobuz",
            streamable = true
        )
    }

    private fun JSONObject.toQobuzAlbum(): Playlist? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val title = optString("title")
        if (title.isBlank()) return null

        val imageObj = optJSONObject("image")
        val cover = imageObj?.optString("large") ?: imageObj?.optString("small")
        val artistObj = optJSONObject("artist")
        val artistName = artistObj?.extractName() ?: "Unknown Artist"
        val artistId = artistObj?.optLong("id") ?: 0L
        val tracksCount = optInt("tracks_count")
        val stableId = abs(("qobuz:album:$id").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = title,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = tracksCount,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                urn = "qobuz:artist:$artistId"
            ),
            isAlbum = true,
            permalink = id,
            permalinkUrl = optString("url").ifBlank { "https://www.qobuz.com/album/$id" },
            urn = "qobuz:album:$id"
        )
    }

    private fun JSONObject.toQobuzPlaylist(): Playlist? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val name = optString("name")
        if (name.isBlank()) return null

        val images300 = optJSONArray("images300")
        val images = optJSONArray("images")
        val imageRect = optJSONArray("image_rectangle")
        val cover = images300?.optString(0)?.takeIf { it.isNotBlank() }
            ?: images?.optString(0)?.takeIf { it.isNotBlank() }
            ?: imageRect?.optString(0)?.takeIf { it.isNotBlank() }

        val ownerObj = optJSONObject("owner")
        val ownerName = ownerObj?.optString("name") ?: "Qobuz"
        val ownerId = ownerObj?.optLong("id") ?: abs(ownerName.hashCode().toLong())
        val tracks = optJSONObject("tracks")
            ?.optJSONArray("items")
            ?.mapObjects { it.toQobuzTrack() }
        val tracksCount = optInt("tracks_count", tracks?.size ?: 0)
        val stableId = abs(("qobuz:playlist:$id").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = name,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = tracksCount,
            user = User(
                id = ownerId,
                username = ownerName,
                avatarUrl = null
            ),
            tracks = tracks,
            isAlbum = false,
            description = optString("description"),
            permalink = id,
            permalinkUrl = "https://www.qobuz.com/playlist/$id",
            urn = "qobuz:playlist:$id"
        )
    }

    private fun extractArtistPicture(artistObj: JSONObject): String? {
        val direct = artistObj.optString("picture").takeIf { it.isNotBlank() && it != "null" && it.startsWith("http") }
        if (direct != null) return direct

        val imageObj = artistObj.optJSONObject("image") ?: artistObj.optJSONObject("images")
        if (imageObj != null) {
            val directUrl = imageObj.optString("large").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("extralarge").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("mega").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("medium").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("small").takeIf { it.isNotBlank() && it.startsWith("http") }
            if (directUrl != null) return directUrl

            for (key in listOf("portrait", "square", "landscape")) {
                val child = imageObj.optJSONObject(key)
                val hash = child?.optString("hash")?.takeIf { it.isNotBlank() && it != "null" }
                if (hash != null) {
                    val format = child.optString("format").ifBlank { "jpg" }
                    return "https://static.qobuz.com/images/artists/covers/large/$hash.$format"
                }
            }
            val directHash = imageObj.optString("hash").takeIf { it.isNotBlank() && it != "null" }
            if (directHash != null) {
                val format = imageObj.optString("format").ifBlank { "jpg" }
                return "https://static.qobuz.com/images/artists/covers/large/$directHash.$format"
            }
        }
        return null
    }

    private fun JSONObject.toQobuzArtist(): User? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val name = extractName() ?: optString("name")
        if (name.isBlank()) return null

        val picture = extractArtistPicture(this)
        val stableId = abs(("qobuz:artist:$id").hashCode().toLong())

        return User(
            id = stableId,
            username = name,
            avatarUrl = picture,
            permalink = id,
            urn = "qobuz:artist:$id"
        )
    }

    private fun fetchJson(url: String, bearerToken: String? = null): JSONObject? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .apply { if (!bearerToken.isNullOrBlank()) header("Authorization", "Bearer $bearerToken") }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                JSONObject(body)
            }
        }.getOrNull()
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T> {
        val list = mutableListOf<T>()
        for (i in 0 until length()) {
            optJSONObject(i)?.let { obj ->
                transform(obj)?.let { list.add(it) }
            }
        }
        return list
    }
}
