package com.alananasss.kittytune.data.spotify

import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object SpotifyRepository {

    private const val TAG = "SpotifyRepository"

    /**
     * Browser-like User-Agent pool (mirrors the web player fingerprinting).
     * One agent is picked per app session and kept for every request so the
     * identity stays consistent across calls.
     */
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:134.0) Gecko/20100101 Firefox/134.0"
    )
    private val userAgent: String by lazy { USER_AGENTS[Random.nextInt(USER_AGENTS.size)] }

    private const val MAX_ATTEMPTS = 4
    private const val BACKOFF_BASE_MS = 500L
    private const val BACKOFF_MAX_MS = 8_000L

    private enum class FetchOutcome { SUCCESS, RETRY_NEW_TOKEN, RETRY_BACKOFF, GIVE_UP }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()

    /**
     * Central GET used by every endpoint: bearer token handling with one
     * refresh-and-retry on HTTP 401, exponential backoff with jitter on
     * transient failures (429 / 5xx / network), honoring Retry-After, and
     * explicit detection of rotated persisted-query hashes.
     */
    private suspend fun <T> execute(
        url: String,
        parse: (JSONObject) -> T?
    ): T? = withContext(Dispatchers.IO) {
        var attempt = 0
        var refreshedAfter401 = false

        while (attempt < MAX_ATTEMPTS) {
            val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
            var outcome = FetchOutcome.GIVE_UP
            var parsed: T? = null
            var retryAfterMs = BACKOFF_BASE_MS

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("app-platform", "WebPlayer")
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 -> {
                            // Anonymous token rejected: refresh once, then give up.
                            SpotifyTokenManager.invalidateToken()
                            outcome =
                                if (refreshedAfter401) FetchOutcome.GIVE_UP else FetchOutcome.RETRY_NEW_TOKEN
                            refreshedAfter401 = true
                        }
                        response.code == 429 || response.code >= 500 -> {
                            response.header("Retry-After")?.toLongOrNull()?.let {
                                retryAfterMs = (it * 1000L).coerceAtMost(BACKOFF_MAX_MS)
                            }
                            outcome = FetchOutcome.RETRY_BACKOFF
                        }
                        !response.isSuccessful -> FetchOutcome.GIVE_UP
                        else -> {
                            val bodyStr = response.body?.string()
                            if (bodyStr.isNullOrBlank()) {
                                outcome = FetchOutcome.GIVE_UP
                            } else {
                                try {
                                    val json = JSONObject(bodyStr)
                                    val errors = json.optJSONArray("errors")
                                    if (errors != null && hasPersistedQueryNotFound(errors)) {
                                        Logger.e(
                                            TAG,
                                            "Spotify rejected a persisted-query hash (PersistedQueryNotFound); " +
                                                "the internal API contract changed and needs an update."
                                        )
                                        outcome = FetchOutcome.GIVE_UP
                                    } else {
                                        parsed = parse(json)
                                        outcome = if (parsed != null) FetchOutcome.SUCCESS else FetchOutcome.GIVE_UP
                                    }
                                } catch (e: Exception) {
                                    Logger.w(TAG, "Failed to parse Spotify response: ${e.message}")
                                    outcome = FetchOutcome.GIVE_UP
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                outcome = FetchOutcome.RETRY_BACKOFF
            } catch (e: Exception) {
                Logger.w(TAG, "Spotify request error: ${e.message}")
                outcome = FetchOutcome.GIVE_UP
            }

            when (outcome) {
                FetchOutcome.SUCCESS -> return@withContext parsed
                FetchOutcome.RETRY_NEW_TOKEN -> attempt++
                FetchOutcome.RETRY_BACKOFF -> {
                    if (++attempt >= MAX_ATTEMPTS) return@withContext null
                    val backoff = (retryAfterMs shl (attempt - 1)).coerceAtMost(BACKOFF_MAX_MS)
                    delay(backoff + Random.nextLong(backoff / 4 + 1))
                }
                FetchOutcome.GIVE_UP -> return@withContext null
            }
        }
        null
    }

    /** Fetch a pathfinder GraphQL query and return its `data` envelope. */
    private suspend fun fetchPathfinderData(url: String): JSONObject? =
        execute(url) { it.optJSONObject("data") }

    /** Fetch any bearer-authenticated JSON endpoint (non-pathfinder shape). */
    private suspend fun fetchJsonObject(url: String): JSONObject? = execute(url) { it }

    /**
     * Spotify's own cover-art color extraction (fetchExtractedColors): one
     * small request instead of downloading the artwork and analyzing pixels.
     * The image id is taken from the artwork URL (i.scdn.co/image/<hash>).
     */
    suspend fun getExtractedColors(artworkUrl: String?): SpotifyColors? {
        if (artworkUrl.isNullOrBlank()) return null
        val imageId = Regex("/image/([a-f0-9]+)").find(artworkUrl)?.groupValues?.get(1) ?: return null
        return try {
            val extracted = fetchPathfinderData(
                SpotifyPathfinderApi.buildExtractedColorsUrl(listOf("spotify:image:$imageId"))
            )?.optJSONArray("extractedColors")?.optJSONObject(0) ?: return null

            fun hex(node: JSONObject?): String? {
                val value = node?.optString("hex")
                return value?.takeIf { it.startsWith("#") }
            }

            val raw = hex(extracted.optJSONObject("colorRaw")) ?: return null
            SpotifyColors(
                raw = raw,
                dark = hex(extracted.optJSONObject("colorDark")) ?: raw,
                light = hex(extracted.optJSONObject("colorLight")) ?: raw,
                isFallback = extracted.optJSONObject("colorRaw")?.optBoolean("isFallback", false) == true
            )
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to extract cover colors: ${e.message}")
            null
        }
    }

    private fun hasPersistedQueryNotFound(errors: JSONArray): Boolean {
        for (i in 0 until errors.length()) {
            if (errors.optJSONObject(i)?.optString("message") == "PersistedQueryNotFound") return true
        }
        return false
    }

    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): SpotifySearchResults {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SpotifySearchResults(query = query)

        val url = SpotifyPathfinderApi.buildSearchUrl(trimmed, offset = offset, limit = limit)
        return try {
            val searchV2 = fetchPathfinderData(url)?.optJSONObject("searchV2")
                ?: return SpotifySearchResults(query = query)

            val tracks = parseSearchTracks(searchV2.optJSONObject("tracksV2"))
            val albums = parseSearchAlbums(searchV2.optJSONObject("albumsV2"))
            val artists = parseSearchArtists(searchV2.optJSONObject("artists"))
            val playlists = parseSearchPlaylists(searchV2.optJSONObject("playlists"))

            SpotifySearchResults(
                query = trimmed,
                tracks = tracks,
                albums = albums,
                artists = artists,
                playlists = playlists,
                totalTracks = tracks.size
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Exception during Spotify search: ${e.message}", e)
            SpotifySearchResults(query = query)
        }
    }

    suspend fun getTrack(trackId: String): SpotifyTrack? {
        val cleanId = extractId(trackId)
        if (cleanId.isBlank()) return null
        return try {
            fetchPathfinderData(SpotifyPathfinderApi.buildTrackUrl(cleanId))
                ?.optJSONObject("trackUnion")
                ?.let { parseTrackNode(it) }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch Spotify track $trackId: ${e.message}")
            null
        }
    }

    suspend fun getAlbum(albumId: String): SpotifyAlbum? {
        val cleanId = extractId(albumId)
        if (cleanId.isBlank()) return null
        return try {
            val albumUnion = fetchPathfinderData(SpotifyPathfinderApi.buildAlbumUrl(cleanId))
                ?.optJSONObject("albumUnion") ?: return null

            val name = albumUnion.optString("name", "Unknown Album")
            val coverUrl = extractCoverArt(albumUnion.optJSONObject("coverArt"))
            val dateStr = albumUnion.optJSONObject("date")?.optStringOrNull("isoString")
            val artists = parseArtistList(albumUnion.optJSONObject("artists")?.optJSONArray("items"))

            val tracksList = mutableListOf<SpotifyTrack>()
            val tracksV2 = albumUnion.optJSONObject("tracksV2")
            val totalCount = tracksV2?.optInt("totalCount", 0) ?: 0

            suspend fun collect(items: JSONArray?) {
                if (items == null) return
                for (i in 0 until items.length()) {
                    val trackNode = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                    parseTrackNode(
                        trackNode,
                        defaultAlbumName = name,
                        defaultArtwork = coverUrl,
                        defaultReleaseDate = dateStr
                    )?.let(tracksList::add)
                }
            }

            collect(tracksV2?.optJSONArray("items"))

            // Paginate through the remaining album tracks (50 per page, hard cap 500).
            var albumOffset = tracksV2?.optJSONArray("items")?.length() ?: 0
            while (albumOffset < totalCount && tracksList.size < 500) {
                val pageItems = fetchPathfinderData(
                    SpotifyPathfinderApi.buildAlbumUrl(cleanId, offset = albumOffset, limit = 50)
                )?.optJSONObject("albumUnion")?.optJSONObject("tracksV2")?.optJSONArray("items")
                if (pageItems == null || pageItems.length() == 0) break
                collect(pageItems)
                albumOffset += pageItems.length()
            }

            SpotifyAlbum(
                id = cleanId,
                name = name,
                artists = artists,
                artworkUrl = coverUrl,
                releaseDate = dateStr,
                totalTracks = if (totalCount > 0) totalCount else tracksList.size,
                tracks = tracksList
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch Spotify album $albumId: ${e.message}")
            null
        }
    }

    suspend fun getPlaylist(playlistId: String, maxTracks: Int = 1000): SpotifyPlaylist? {
        val cleanId = extractId(playlistId)
        if (cleanId.isBlank()) return null
        return try {
            val playlistV2 = fetchPathfinderData(
                SpotifyPathfinderApi.buildPlaylistUrl(cleanId, offset = 0, limit = 100)
            )?.optJSONObject("playlistV2") ?: return null

            val name = playlistV2.optString("name", "Spotify Playlist")
            val description = playlistV2.optString("description").ifBlank { null }
            val ownerData = playlistV2.optJSONObject("ownerV2")?.optJSONObject("data")
            val ownerName = ownerData?.optString("name")
            val ownerUri = ownerData?.optString("uri")
            val ownerId = ownerUri?.let { extractId(it) }?.ifBlank { null }
                ?: ownerData?.optString("username")?.takeIf { it.isNotBlank() }
            val ownerAvatarUrl = extractImageFromItems(ownerData?.optJSONObject("avatar")?.optJSONArray("sources"))
                ?: extractImageFromItems(ownerData?.optJSONObject("images")?.optJSONArray("items"))

            val followersCount = playlistV2.optJSONObject("followers")?.optLong("totalCount")
                ?: playlistV2.optLong("likesCount", 0L).takeIf { it > 0L }
                ?: playlistV2.optLong("followersCount", 0L).takeIf { it > 0L }

            val artworkUrl = extractImageFromItems(playlistV2.optJSONObject("images")?.optJSONArray("items"))

            val tracksList = mutableListOf<SpotifyTrack>()
            val content = playlistV2.optJSONObject("content")
            val totalCount = content?.optInt("totalCount", 0) ?: 0

            suspend fun collect(items: JSONArray?) {
                if (items == null) return
                for (i in 0 until items.length()) {
                    val trackData = items.optJSONObject(i)
                        ?.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                    parseTrackNode(trackData)?.let(tracksList::add)
                }
            }

            collect(content?.optJSONArray("items"))

            // Paginate long playlists (100 per page).
            var playlistOffset = content?.optJSONArray("items")?.length() ?: 0
            while (playlistOffset < totalCount && tracksList.size < maxTracks) {
                val pageItems = fetchPathfinderData(
                    SpotifyPathfinderApi.buildPlaylistUrl(cleanId, offset = playlistOffset, limit = 100)
                )?.optJSONObject("playlistV2")?.optJSONObject("content")?.optJSONArray("items")
                if (pageItems == null || pageItems.length() == 0) break
                collect(pageItems)
                playlistOffset += pageItems.length()
            }

            SpotifyPlaylist(
                id = cleanId,
                name = name,
                description = description,
                ownerName = ownerName,
                ownerId = ownerId,
                ownerAvatarUrl = ownerAvatarUrl,
                artworkUrl = artworkUrl,
                totalTracks = if (totalCount > 0) totalCount else tracksList.size,
                followersCount = followersCount,
                tracks = tracksList
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch Spotify playlist $playlistId: ${e.message}")
            null
        }
    }


    /**
     * Avatar of a single artist, memoized for the session.
     *
     * Track and album payloads carry artist ids and names but no visuals, so the
     * multi-artist picker has nothing to show until we ask. The artist overview is
     * heavier than we'd like for one image, but it is the only operation that exposes
     * it, and the cache keeps it to once per artist.
     */
    suspend fun getArtistAvatar(artistId: String): String? {
        val cleanId = extractId(artistId)
        if (cleanId.isBlank()) return null
        artistAvatarCache[cleanId]?.let { return it }
        val avatar = getArtist(cleanId)?.avatarUrl?.takeIf { it.isNotBlank() } ?: return null
        artistAvatarCache[cleanId] = avatar
        return avatar
    }

    private val artistAvatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val trackReleaseDateCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val EMBED_RELEASE_DATE =
        Regex("\"releaseDate\"\\s*:\\s*\\{\\s*\"isoString\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * Release date of a single track, memoized for the session.
     *
     * Bulk payloads don't carry one: playlist, search, artist and radio items omit it
     * entirely, and album items only have it because the album node does (they inherit it
     * for free). So a list would need one request per row — but the details panel shows one
     * track at a time and can afford exactly one.
     *
     * The public embed page answers it in ~10 KB with no token at all, so it goes first;
     * the pathfinder track query is the fallback.
     */
    suspend fun getTrackReleaseDate(trackId: String): String? {
        val cleanId = extractId(trackId)
        if (cleanId.isBlank()) return null
        trackReleaseDateCache[cleanId]?.let { return it }
        val date = fetchEmbedReleaseDate(cleanId)
            ?: getTrack(cleanId)?.releaseDate?.takeIf { it.isNotBlank() }
            ?: return null
        trackReleaseDateCache[cleanId] = date
        return date
    }

    /** Scrapes releaseDate out of open.spotify.com/embed/track/<id> (no auth needed). */
    private suspend fun fetchEmbedReleaseDate(trackId: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/track/$trackId")
                .header("Accept", "text/html")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                EMBED_RELEASE_DATE.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Embed release date failed for $trackId: ${e.message}")
            null
        }
    }

    suspend fun getArtist(artistId: String): SpotifyArtist? {
        val cleanId = extractId(artistId)
        if (cleanId.isBlank()) return null
        return try {
            val artistUnion = fetchPathfinderData(SpotifyPathfinderApi.buildArtistUrl(cleanId))
                ?.optJSONObject("artistUnion") ?: return null

            val profile = artistUnion.optJSONObject("profile")
            val name = profile?.optString("name") ?: "Unknown Artist"
            val verification = artistUnion.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
            val isReputationVerified = verification?.optBoolean("isVerified", false) == true
            val isProfileVerified = profile?.optBoolean("isVerified", false) == true
            val isUnionVerified = artistUnion.optBoolean("isVerified", false)
            val verified = isReputationVerified || isProfileVerified || isUnionVerified
            val biography = profile?.optJSONObject("biography")?.optString("text")?.ifBlank { null }

            val visuals = artistUnion.optJSONObject("visuals")
            val avatarUrl = extractImageFromSources(visuals?.optJSONObject("avatarImage")?.optJSONArray("sources"))
            val headerImageUrl =
                extractImageFromSources(visuals?.optJSONObject("headerImage")?.optJSONArray("sources"))

            val stats = artistUnion.optJSONObject("stats")
            val monthlyListeners = stats?.optLong("monthlyListeners")
            val worldRank = stats?.optInt("worldRank", 0)?.takeIf { it > 0 }
            val followers = stats?.optLong("followers")

            val discography = artistUnion.optJSONObject("discography")

            val topTracks = mutableListOf<SpotifyTrack>()
            val topTrackItems = discography?.optJSONObject("topTracks")?.optJSONArray("items")
            if (topTrackItems != null) {
                for (i in 0 until topTrackItems.length()) {
                    val item = topTrackItems.optJSONObject(i) ?: continue
                    val trackNode = item.optJSONObject("track") ?: item
                    parseTrackNode(trackNode, defaultAlbumName = null, defaultArtwork = avatarUrl)
                        ?.let(topTracks::add)
                }
            }

            val popularReleases = parseReleasesGroup(
                discography?.optJSONObject("popularReleasesV2") ?: discography?.optJSONObject("popularReleases"),
                name,
                avatarUrl
            )
            val albums = parseReleasesGroup(discography?.optJSONObject("albums"), name, avatarUrl)
            val singles = parseReleasesGroup(discography?.optJSONObject("singles"), name, avatarUrl)
            val compilations = parseReleasesGroup(discography?.optJSONObject("compilations"), name, avatarUrl)

            val relatedContent = artistUnion.optJSONObject("relatedContent")
            val relatedArtists = mutableListOf<SpotifyArtistRef>()
            val relItems = relatedContent?.optJSONObject("relatedArtists")?.optJSONArray("items")
            if (relItems != null) {
                for (i in 0 until relItems.length()) {
                    val item = relItems.optJSONObject(i) ?: continue
                    val relProfile = item.optJSONObject("profile")
                    val relName = relProfile?.optString("name") ?: item.optString("name")
                    val relUri = item.optString("uri")
                    val relId = item.optString("id").ifBlank { extractId(relUri) }
                    val relVisuals = item.optJSONObject("visuals")
                    val relAvatar =
                        extractImageFromSources(relVisuals?.optJSONObject("avatarImage")?.optJSONArray("sources"))
                    val relVerification =
                        item.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
                    val relVerified = relVerification?.optBoolean("isVerified", false) == true
                            || relProfile?.optBoolean("isVerified", false) == true
                            || item.optBoolean("isVerified", false)
                    if (relName.isNotBlank()) {
                        relatedArtists.add(
                            SpotifyArtistRef(
                                id = relId,
                                name = relName,
                                uri = relUri,
                                avatarUrl = relAvatar,
                                verified = relVerified
                            )
                        )
                    }
                }
            }

            val appearsOn = parseReleasesGroup(relatedContent?.optJSONObject("appearsOn"), name, avatarUrl)

            val discoveredOn = mutableListOf<SpotifyPlaylist>()
            val discGroups = listOfNotNull(
                relatedContent?.optJSONObject("featuringV2")?.optJSONArray("items"),
                relatedContent?.optJSONObject("discoveredOn")?.optJSONArray("items"),
                relatedContent?.optJSONObject("discoveredOnV2")?.optJSONArray("items")
            )
            for (discItems in discGroups) {
                for (i in 0 until discItems.length()) {
                    val item = discItems.optJSONObject(i) ?: continue
                    val playData = item.optJSONObject("data") ?: item
                    val pUri = playData.optString("uri")
                    val pName = playData.optString("name", "Spotify Playlist")
                    val pDesc = playData.optString("description").ifBlank { null }
                    val pOwner = playData.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")
                    val pArt = extractImageFromItems(playData.optJSONObject("images")?.optJSONArray("items"))
                        ?: extractCoverArt(playData.optJSONObject("coverArt"))
                    val pId = extractId(pUri).ifBlank { playData.optString("id") }
                    // Spotify exposes a playlist's length as content.totalCount here; the album
                    // shape (tracks.totalCount) also shows up in some of these groups. Left at 0
                    // when neither is present, and the card then omits the line entirely rather
                    // than claiming "0 tracks".
                    val pCount = playData.optJSONObject("content")?.optInt("totalCount", 0)
                        ?.takeIf { it > 0 }
                        ?: playData.optJSONObject("tracks")?.optInt("totalCount", 0)?.takeIf { it > 0 }
                        ?: playData.optJSONObject("tracksV2")?.optInt("totalCount", 0)?.takeIf { it > 0 }
                        ?: 0
                    if (pId.isNotBlank()) {
                        discoveredOn.add(
                            SpotifyPlaylist(
                                id = pId,
                                name = pName,
                                description = pDesc,
                                ownerName = pOwner,
                                artworkUrl = pArt,
                                totalTracks = pCount
                            )
                        )
                    }
                }
            }

            val externalLinks = mutableListOf<SpotifyExternalLink>()
            val linkItems = profile?.optJSONObject("externalLinks")?.optJSONArray("items")
            if (linkItems != null) {
                for (i in 0 until linkItems.length()) {
                    val item = linkItems.optJSONObject(i) ?: continue
                    val linkName = item.optString("name")
                    val linkUrl = item.optString("url")
                    if (linkName.isNotBlank() && linkUrl.isNotBlank()) {
                        externalLinks.add(SpotifyExternalLink(linkName, linkUrl))
                    }
                }
            }

            SpotifyArtist(
                id = cleanId,
                name = name,
                avatarUrl = avatarUrl,
                headerImageUrl = headerImageUrl,
                verified = verified,
                monthlyListeners = monthlyListeners,
                worldRank = worldRank,
                followers = followers,
                biography = biography,
                topTracks = topTracks,
                popularReleases = popularReleases,
                albums = albums.distinctBy { it.id },
                singles = singles.distinctBy { it.id },
                compilations = compilations.distinctBy { it.id },
                appearsOn = appearsOn.distinctBy { it.id },
                discoveredOn = discoveredOn.distinctBy { it.id },
                relatedArtists = relatedArtists.distinctBy { it.id },
                externalLinks = externalLinks
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch Spotify artist $artistId: ${e.message}", e)
            null
        }
    }

    /**
     * One page of the artist's complete discography (queryArtistDiscographyAll),
     * returned with the total count so callers can keep paging. Complements
     * [getArtist], whose overview only exposes the first handful of releases.
     */
    suspend fun getArtistDiscographyPage(
        artistId: String,
        offset: Int = 0,
        limit: Int = 50
    ): Pair<List<SpotifyAlbum>, Int>? {
        val cleanId = extractId(artistId)
        if (cleanId.isBlank()) return null
        return try {
            val all = fetchPathfinderData(
                SpotifyPathfinderApi.buildArtistDiscographyUrl(cleanId, offset = offset, limit = limit)
            )?.optJSONObject("artistUnion")?.optJSONObject("discography")?.optJSONObject("all")
                ?: return null

            val totalCount = all.optInt("totalCount", 0)
            if (all.optJSONArray("items") == null) return Pair(emptyList(), totalCount)
            Pair(parseReleasesGroup(all, "", null), totalCount)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch Spotify discography page for $artistId: ${e.message}")
            null
        }
    }

    fun getCharts(): List<SpotifyChart> {
        return SpotifyPathfinderApi.EDITORIAL_CHARTS
    }

    private fun parseReleasesGroup(
        groupNode: JSONObject?,
        defaultArtist: String,
        defaultArtwork: String?
    ): List<SpotifyAlbum> {
        if (groupNode == null) return emptyList()
        val items = groupNode.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyAlbum>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val releases = item.optJSONObject("releases")?.optJSONArray("items")
            val releaseNode = releases?.optJSONObject(0) ?: item.optJSONObject("data") ?: item
            val uri = releaseNode.optString("uri")
            val id = releaseNode.optString("id").ifBlank { extractId(uri) }
            val name = releaseNode.optString("name", "Unknown Release")
            val coverUrl = extractCoverArt(releaseNode.optJSONObject("coverArt"))
                ?: extractImageFromSources(releaseNode.optJSONArray("images"))
                ?: defaultArtwork
            val dateNode = releaseNode.optJSONObject("date")
            val dateStr = dateNode?.optStringOrNull("year")
                ?: dateNode?.optInt("year", 0)?.takeIf { it > 0 }?.toString()
                ?: dateNode?.optStringOrNull("isoString")?.take(4)
                ?: releaseNode.optStringOrNull("releaseDate")?.take(4)
            val totalTracks = releaseNode.optJSONObject("tracks")?.optInt("totalCount", 0) ?: 0
            val releaseType = releaseNode.optString("type").ifBlank {
                item.optString("type")
            }.ifBlank {
                if (totalTracks == 1) "SINGLE" else if (totalTracks in 2..6) "EP" else "ALBUM"
            }
            val artists = parseArtistList(releaseNode.optJSONObject("artists")?.optJSONArray("items"))
                .ifEmpty { listOf(SpotifyArtistRef(id = "", name = defaultArtist)) }

            list.add(
                SpotifyAlbum(
                    id = id,
                    name = name,
                    artists = artists,
                    artworkUrl = coverUrl,
                    releaseDate = dateStr,
                    releaseType = releaseType,
                    totalTracks = totalTracks
                )
            )
        }
        return list
    }

    private fun parseSearchTracks(node: JSONObject?): List<SpotifyTrack> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyTrack>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("item")?.optJSONObject("data") ?: item.optJSONObject("data") ?: continue
            val track = parseTrackNode(data)
            if (track != null) list.add(track)
        }
        return list
    }

    private fun parseSearchAlbums(node: JSONObject?): List<SpotifyAlbum> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyAlbum>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val id = extractId(uri)
            val name = data.optString("name", "Unknown Album")
            val coverUrl = extractCoverArt(data.optJSONObject("coverArt"))
            val artists = parseArtistList(data.optJSONObject("artists")?.optJSONArray("items"))
            val dateStr = data.optJSONObject("date")?.optString("year")

            list.add(
                SpotifyAlbum(
                    id = id,
                    name = name,
                    artists = artists,
                    artworkUrl = coverUrl,
                    releaseDate = dateStr
                )
            )
        }
        return list
    }

    private fun parseSearchArtists(node: JSONObject?): List<SpotifyArtist> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyArtist>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val id = extractId(uri)
            val name = data.optJSONObject("profile")?.optString("name") ?: data.optString("name", "Unknown Artist")
            val avatarUrl = extractImageFromSources(
                data.optJSONObject("visuals")?.optJSONObject("avatarImage")?.optJSONArray("sources")
            )
            val verification = data.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
            val isReputationVerified = verification?.optBoolean("isVerified", false) == true
            val isProfileVerified = data.optJSONObject("profile")?.optBoolean("isVerified", false) == true
            val isDataVerified = data.optBoolean("isVerified", false)
            val verified = isReputationVerified || isProfileVerified || isDataVerified

            list.add(
                SpotifyArtist(
                    id = id,
                    name = name,
                    avatarUrl = avatarUrl,
                    verified = verified
                )
            )
        }
        return list
    }

    private fun parseSearchPlaylists(node: JSONObject?): List<SpotifyPlaylist> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyPlaylist>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val id = extractId(uri)
            val name = data.optString("name", "Spotify Playlist")
            val description = data.optString("description").ifBlank { null }
            val owner = data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")
            val artworkUrl = extractImageFromItems(data.optJSONObject("images")?.optJSONArray("items"))

            list.add(
                SpotifyPlaylist(
                    id = id,
                    name = name,
                    description = description,
                    ownerName = owner,
                    artworkUrl = artworkUrl
                )
            )
        }
        return list
    }

    suspend fun getRadioPlaylistId(seedUri: String): String? {
        val cleanSeed = when {
            seedUri.startsWith("spotify:track:") || seedUri.startsWith("spotify:artist:") -> seedUri
            seedUri.startsWith("spotify_artist:") -> "spotify:artist:${seedUri.removePrefix("spotify_artist:")}"
            else -> "spotify:track:${extractId(seedUri)}"
        }
        val url = "https://spclient.wg.spotify.com/inspiredby-mix/v2/seed_to_playlist/$cleanSeed"

        return try {
            // The endpoint answers a JSON document whose playlist uri embeds the
            // editorial radio id; extract it from the serialized payload.
            val bodyJson = execute(url) { it } ?: return null
            Regex("spotify:playlist:(37i9dQZF[a-zA-Z0-9]+)").find(bodyJson.toString())
                ?.groupValues?.get(1)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to resolve radio playlist ID for $seedUri: ${e.message}")
            null
        }
    }

    suspend fun getRadio(seedId: String, isArtist: Boolean = false): SpotifyPlaylist? {
        val cleanId = extractId(seedId)
        if (cleanId.isBlank()) return null

        if (cleanId.startsWith("37i9dQZF")) {
            val playlist = getPlaylist(cleanId)
            if (playlist != null && playlist.tracks.isNotEmpty()) return playlist
        }

        // Editorial radio first: the inspiredby-mix endpoint resolves a seed
        // (track or artist) to a real Spotify radio playlist in one request.
        val seedUri = if (isArtist) "spotify:artist:$cleanId" else "spotify:track:$cleanId"
        val radioPlaylistId = getRadioPlaylistId(seedUri)
        if (!radioPlaylistId.isNullOrBlank()) {
            val playlist = getPlaylist(radioPlaylistId)
            if (playlist != null && playlist.tracks.isNotEmpty()) return playlist
        }

        if (isArtist) {
            val artist = getArtist(cleanId) ?: return null
            if (artist.discoveredOn.isNotEmpty()) {
                val radioFromArtist = artist.discoveredOn.firstOrNull {
                    it.id.startsWith("37i9dQZF1E4") || (it.name.contains("Radio", ignoreCase = true)
                        && !it.name.contains("This Is", ignoreCase = true))
                } ?: artist.discoveredOn.firstOrNull { it.id.startsWith("37i9dQZF") }

                if (radioFromArtist != null) {
                    val fullPlaylist = getPlaylist(radioFromArtist.id)
                    if (fullPlaylist != null && fullPlaylist.tracks.isNotEmpty()) return fullPlaylist
                }
            }

            // "Artist Radio" and "This Is ..." editorial playlists, searched concurrently.
            val candidates = coroutineScope {
                listOf(
                    async { search("${artist.name} Radio") },
                    async { search("This Is ${artist.name}") }
                ).awaitAll().flatMap { it.playlists }
            }

            val radioCandidate = candidates.firstOrNull {
                it.id.startsWith("37i9dQZF1E4") || (it.name.contains("Radio", ignoreCase = true)
                    && it.name.contains(artist.name, ignoreCase = true))
            } ?: candidates.firstOrNull { it.name.contains("This Is", ignoreCase = true) }
                ?: candidates.firstOrNull { it.name.contains("Radio", ignoreCase = true) || it.id.startsWith("37i9dQZF") }

            if (radioCandidate != null) {
                val fullPlaylist = getPlaylist(radioCandidate.id)
                if (fullPlaylist != null && fullPlaylist.tracks.isNotEmpty()) return fullPlaylist
            }

            if (artist.topTracks.isNotEmpty()) {
                // Dynamic fallback: top tracks of the artist + related artists, fetched concurrently.
                val relatedTracks = coroutineScope {
                    artist.relatedArtists.take(6).map { rel ->
                        async { getArtist(rel.id)?.topTracks?.take(4).orEmpty() }
                    }.awaitAll().flatten()
                }
                return SpotifyPlaylist(
                    id = "spotify_radio:$cleanId",
                    name = "${artist.name} Radio",
                    description = "Radio inspired by ${artist.name}",
                    artworkUrl = artist.avatarUrl ?: artist.headerImageUrl,
                    ownerName = "Spotify",
                    tracks = (artist.topTracks + relatedTracks).distinctBy { it.id }
                )
            }
        } else {
            val recTracks = getRadioTracks(cleanId)
            if (recTracks.size > 1) {
                val seedTrack = recTracks.first()
                return SpotifyPlaylist(
                    id = "spotify_radio:$cleanId",
                    name = "${seedTrack.name} Radio",
                    artworkUrl = seedTrack.artworkUrl,
                    ownerName = "Spotify",
                    tracks = recTracks
                )
            }
        }
        return null
    }

    /**
     * Dynamic radio mix seeded by one track. The official Web API
     * /v1/recommendations endpoint rejects anonymous web-player tokens, so this
     * builds the mix from the seed's artists and their related artists instead,
     * fetching every branch concurrently.
     */
    suspend fun getRadioTracks(trackId: String): List<SpotifyTrack> {
        val cleanId = extractId(trackId)
        if (cleanId.isBlank()) return emptyList()
        val seedTrack = getTrack(cleanId) ?: return emptyList()

        val mix = coroutineScope {
            seedTrack.artists.filter { it.id.isNotBlank() }.take(2).map { artistRef ->
                async {
                    val artistObj = getArtist(artistRef.id) ?: return@async emptyList<SpotifyTrack>()
                    val ownTracks = artistObj.topTracks.filter { it.id != seedTrack.id }
                    val relatedTracks = artistObj.relatedArtists.take(4).map { rel ->
                        async { getArtist(rel.id)?.topTracks?.take(3).orEmpty() }
                    }.awaitAll().flatten()
                    ownTracks + relatedTracks
                }
            }.awaitAll().flatten()
        }

        return (listOf(seedTrack) + mix).distinctBy { it.id }
    }

    suspend fun getCredits(trackId: String): SpotifyCredits? {
        val cleanId = extractId(trackId)
        if (cleanId.isBlank()) return null
        val url = "https://spclient.wg.spotify.com/track-credits-view/v0/experimental/$cleanId/credits"

        try {
            val json = fetchJsonObject(url) ?: return null
            val trackTitle = json.optString("trackTitle")
            val trackUri = json.optString("trackUri")

            val sourceNames = mutableListOf<String>()
            val sourcesArr = json.optJSONArray("sourceNames")
            if (sourcesArr != null) {
                for (i in 0 until sourcesArr.length()) {
                    val s = sourcesArr.optString(i)
                    if (s.isNotBlank()) sourceNames.add(s)
                }
            }

            val roles = mutableListOf<SpotifyCreditRole>()
            val roleCreditsArr = json.optJSONArray("roleCredits")
            if (roleCreditsArr != null) {
                for (i in 0 until roleCreditsArr.length()) {
                    val roleObj = roleCreditsArr.optJSONObject(i) ?: continue
                    val roleTitle = roleObj.optString("roleTitle")
                    val artistsList = mutableListOf<SpotifyCreditArtist>()
                    val artistsArr = roleObj.optJSONArray("artists")
                    if (artistsArr != null) {
                        for (j in 0 until artistsArr.length()) {
                            val artObj = artistsArr.optJSONObject(j) ?: continue
                            val name = artObj.optString("name")
                            val uri = artObj.optString("uri")
                            val id = extractId(uri)
                            val img = artObj.optStringOrNull("imageUri")?.let { spotifyImageUrl(it) }
                            val subroles = mutableListOf<String>()
                            val subArr = artObj.optJSONArray("subroles")
                            if (subArr != null) {
                                for (k in 0 until subArr.length()) {
                                    val sub = subArr.optString(k)
                                    if (sub.isNotBlank()) subroles.add(sub)
                                }
                            }
                            if (name.isNotBlank()) {
                                artistsList.add(
                                    SpotifyCreditArtist(
                                        id = id,
                                        name = name,
                                        uri = uri,
                                        imageUri = img,
                                        subroles = subroles
                                    )
                                )
                            }
                        }
                    }
                    roles.add(SpotifyCreditRole(roleTitle = roleTitle, artists = artistsList))
                }
            }

            return SpotifyCredits(
                trackTitle = trackTitle,
                trackUri = trackUri,
                roles = roles,
                sourceNames = sourceNames
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch Spotify credits for $trackId: ${e.message}")
        }

        // Fallback: derive minimal credits from the track itself.
        val track = getTrack(cleanId) ?: return null
        val fallbackRoles = mutableListOf<SpotifyCreditRole>()
        if (track.artists.isNotEmpty()) {
            val perfArtists = track.artists.mapIndexed { idx, art ->
                SpotifyCreditArtist(
                    id = art.id,
                    name = art.name,
                    uri = art.uri,
                    imageUri = art.avatarUrl,
                    subroles = if (idx == 0) listOf("Main Artist") else listOf("Featured Artist")
                )
            }
            fallbackRoles.add(SpotifyCreditRole(roleTitle = "Performers", artists = perfArtists))
        }

        return SpotifyCredits(
            trackTitle = track.name,
            trackUri = "spotify:track:$cleanId",
            roles = fallbackRoles,
            sourceNames = listOfNotNull(track.publisher?.takeIf { it.isNotBlank() })
        )
    }

    private fun parseTrackNode(
        node: JSONObject,
        defaultAlbumName: String? = null,
        defaultArtwork: String? = null,
        defaultReleaseDate: String? = null
    ): SpotifyTrack? {
        val uri = node.optString("uri")
        val name = node.optString("name")
        if (uri.isBlank() || name.isBlank()) return null

        val id = node.optString("id").ifBlank { extractId(uri) }
        // Playlist items carry "trackDuration", other unions use "duration".
        val durationMs = node.optJSONObject("duration")?.optLong("totalMilliseconds")
            ?: node.optJSONObject("trackDuration")?.optLong("totalMilliseconds")
            ?: node.optLong("duration", 0L)

        val isPlayable = node.optJSONObject("playability")?.optBoolean("playable", true) ?: true
        val explicit = node.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"

        val albumNode = node.optJSONObject("albumOfTrack") ?: node.optJSONObject("album")
        val albumName = albumNode?.optString("name") ?: defaultAlbumName
        val albumId = albumNode?.optString("uri")?.let { extractId(it) } ?: albumNode?.optString("id")
        val artworkUrl = extractCoverArt(albumNode?.optJSONObject("coverArt"))
            ?: extractImageFromSources(albumNode?.optJSONArray("images"))
            ?: defaultArtwork

        // org.json's optString returns "" for a missing key, never null, so an elvis chain over
        // it never falls through — the first link always "succeeds" with a blank. That is why the
        // release date came out "Unknown" whenever the album node carried a year but no isoString.
        val releaseDate = albumNode?.optJSONObject("date")?.optStringOrNull("isoString")
            ?: albumNode?.optJSONObject("date")?.optStringOrNull("year")
            ?: albumNode?.optJSONObject("date")?.optInt("year", 0)?.takeIf { it > 0 }?.toString()
            ?: albumNode?.optStringOrNull("release_date")
            ?: albumNode?.optStringOrNull("releaseDate")
            ?: node.optJSONObject("date")?.optStringOrNull("isoString")
            ?: node.optJSONObject("date")?.optStringOrNull("year")
            // Album track items carry no date of their own; the album's is the right answer
            // and costs nothing, which is what the caller passes in here.
            ?: defaultReleaseDate

        val playCount = node.optString("playcount").toLongOrNull()
            ?: node.optLong("playcount", 0L).takeIf { it > 0 }

        val label = albumNode?.optString("label")
        val copyrightItems = albumNode?.optJSONObject("copyright")?.optJSONArray("items")
        val copyrightText = if (copyrightItems != null && copyrightItems.length() > 0) {
            copyrightItems.optJSONObject(0)?.optString("text")
        } else null
        val publisher = label ?: copyrightText

        val artistsList = mutableListOf<SpotifyArtistRef>()
        val firstArtistItems = node.optJSONObject("firstArtist")?.optJSONArray("items")
        val otherArtistItems = node.optJSONObject("otherArtists")?.optJSONArray("items")
        if (firstArtistItems != null || otherArtistItems != null) {
            artistsList.addAll(parseArtistList(firstArtistItems))
            artistsList.addAll(parseArtistList(otherArtistItems))
        }
        if (artistsList.isEmpty()) {
            val generalArtists = node.optJSONObject("artists")?.optJSONArray("items")
            artistsList.addAll(parseArtistList(generalArtists))
        }

        return SpotifyTrack(
            id = id,
            name = name,
            durationMs = durationMs,
            artists = artistsList,
            albumName = albumName,
            albumId = albumId,
            artworkUrl = artworkUrl,
            releaseDate = releaseDate,
            explicit = explicit,
            isPlayable = isPlayable,
            shareUrl = "https://open.spotify.com/track/$id",
            playCount = playCount,
            publisher = publisher
        )
    }

    private fun parseArtistList(items: JSONArray?): List<SpotifyArtistRef> {
        if (items == null) return emptyList()
        val list = mutableListOf<SpotifyArtistRef>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val profile = item.optJSONObject("profile") ?: item
            val name = profile.optString("name")
            val uri = item.optString("uri").ifBlank { profile.optString("uri") }
            val id = extractId(uri)
            val visuals = item.optJSONObject("visuals") ?: profile.optJSONObject("visuals")
            val avatarUrl =
                extractImageFromSources(visuals?.optJSONObject("avatarImage")?.optJSONArray("sources"))
            val verification = item.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
                ?: profile.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
            val verified = verification?.optBoolean("isVerified", false) == true
                    || profile.optBoolean("isVerified", false)
                    || item.optBoolean("isVerified", false)
            if (name.isNotBlank()) {
                list.add(
                    SpotifyArtistRef(
                        id = id,
                        name = name,
                        uri = uri,
                        avatarUrl = avatarUrl,
                        verified = verified
                    )
                )
            }
        }
        return list
    }

    private fun extractCoverArt(coverArtNode: JSONObject?): String? {
        if (coverArtNode == null) return null
        val sources = coverArtNode.optJSONArray("sources") ?: return null
        return extractImageFromSources(sources)
    }

    private fun extractImageFromSources(sources: JSONArray?): String? {
        if (sources == null || sources.length() == 0) return null
        var bestUrl: String? = null
        var maxWidth = 0
        for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            val url = s.optString("url")
            val width = s.optInt("width", 0)
            if (url.isNotBlank() && (bestUrl == null || width > maxWidth)) {
                bestUrl = url
                maxWidth = width
            }
        }
        return bestUrl
    }

    private fun extractImageFromItems(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        val first = items.optJSONObject(0) ?: return null
        return extractImageFromSources(first.optJSONArray("sources"))
    }

    private val INTERNAL_ID_PREFIXES = listOf(
        "spotify:user:spotify:playlist:",
        "spotify:station:track:", "spotify:station:artist:",
        "spotify:track:", "spotify:album:", "spotify:artist:", "spotify:playlist:",
        "spotify:episode:", "spotify:show:",
        "spotify_track:", "spotify_album:", "spotify_artist:", "spotify_playlist:", "spotify_radio:",
        "station_artist:", "station_spotify:", "station:",
        "profile:"
    )

    /**
     * Normalizes any Spotify identifier form into the raw base62 entity id:
     * URNs ("spotify:track:X"), app-internal routes ("spotify_artist:X",
     * "station_spotify:X"), and web URLs including regionalized ones such as
     * open.spotify.com/intl-fr/track/X?si=...
     */
    fun extractId(input: String): String {
        var s = input.trim()
        if (s.isBlank()) return ""

        // Strip concatenated internal prefixes repeatedly so compound forms
        // like "spotify:user:spotify:playlist:X" collapse correctly.
        var changed = true
        while (changed) {
            changed = false
            for (prefix in INTERNAL_ID_PREFIXES) {
                if (s.startsWith(prefix, ignoreCase = true)) {
                    s = s.substring(prefix.length)
                    changed = true
                }
            }
        }
        if (s.startsWith("spotify:", ignoreCase = true)) s = s.substring("spotify:".length)

        // Web URLs: drop query params and keep only the last path segment.
        if (s.contains('/')) {
            s = s.substringBefore('?').trimEnd('/').substringAfterLast('/')
        } else {
            s = s.substringBefore('?')
        }

        return s.trim()
    }
}

/**
 * `optString` hands back an empty string for a missing key, which silently defeats every elvis
 * chain written over it. This returns null instead, so fallbacks actually fall back.
 */
private fun org.json.JSONObject.optStringOrNull(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

/**
 * The credits endpoint returns performer images as `spotify:image:<hash>` URNs, not URLs, so an
 * image loader given one straight has nothing to fetch — which is why the Performers list showed
 * placeholder silhouettes while the names loaded fine. Turns whatever form arrives into a URL.
 */
private fun spotifyImageUrl(raw: String): String? = when {
    raw.startsWith("http", ignoreCase = true) -> raw
    raw.startsWith("spotify:image:") -> "https://i.scdn.co/image/" + raw.removePrefix("spotify:image:")
    raw.matches(Regex("[0-9a-fA-F]{16,}")) -> "https://i.scdn.co/image/$raw"
    else -> null
}
