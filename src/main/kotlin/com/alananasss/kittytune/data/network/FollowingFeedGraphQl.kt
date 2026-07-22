package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.domain.Format
import com.alananasss.kittytune.domain.Media
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.StreamItem
import com.alananasss.kittytune.domain.StreamResponse
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.Transcoding
import com.alananasss.kittytune.domain.User
import com.google.gson.annotations.SerializedName

object FollowingFeedGraphQl {
    fun request(page: String?): FollowingFeedGraphQlRequest = FollowingFeedGraphQlRequest(
        query = QUERY,
        variables = FollowingFeedVariables(
            page = page,
            includePromoted = true,
            includePlaylists = true,
            consentParams = ApiConsentParams(consentString = null, tcfVersion = "2.2"),
            first = 5,
        ),
    )

    private val QUERY = """
        query FollowingFeed(
          ${'$'}page: String
          ${'$'}includePromoted: Boolean
          ${'$'}includePlaylists: Boolean
          ${'$'}consentParams: ConsentParams
          ${'$'}first: Int
        ) {
          followingFeed(
            page: ${'$'}page
            includePromoted: ${'$'}includePromoted
            includePlaylists: ${'$'}includePlaylists
            consentParams: ${'$'}consentParams
          ) {
            items {
              __typename
              ... on RepostedItem {
                createdAt
                caption
                item { ...TrackFields ...PlaylistFields }
                reposter { ...UserFields }
              }
              ... on PostedItem {
                createdAt
                caption
                item { ...TrackFields ...PlaylistFields }
              }
              ... on PromotedItem {
                createdAt
                promotedUrn
                item { ...TrackFields ...PlaylistFields }
                promoter {
                  urn
                  name
                  username
                  avatarUrl
                  verified
                }
              }
            }
            page {
              nextPage
            }
          }
        }

        fragment UserFields on User {
          urn
          name
          username
          avatarUrl
          permalinkUrl
          city
          countryCode
          followersCount
          followingsCount
          tracksCount
          description
          verified
        }

        fragment TrackFields on Track {
          __typename
          urn
          title
          user { ...UserFields }
          fullDuration
          snipDuration
          counts {
            plays
            likes
            reposts
            comments
          }
          transcodings {
            url
            preset
            format {
              protocol
              mimeType
            }
          }
          artworkUrlTemplate
          permalinkUrl
          createdAt
          description
          genre
          authorization {
            policy
            monetizationModel
          }
        }

        fragment PlaylistFields on Playlist {
          __typename
          urn
          title
          artworkUrl
          releaseDate
          duration
          trackCount
          createdAt
          playlistCategory
          user { ...UserFields }
          paginatedTracks(first: ${'$'}first) {
            pageInfo {
              nextPage
              hasNextPage
            }
            tracks { ...TrackFields }
          }
        }
    """.trimIndent()
}

data class FollowingFeedGraphQlRequest(
    val query: String,
    val variables: FollowingFeedVariables,
)

data class FollowingFeedVariables(
    val page: String?,
    val includePromoted: Boolean,
    val includePlaylists: Boolean,
    val consentParams: ApiConsentParams,
    val first: Int,
)

data class ApiConsentParams(
    val consentString: String?,
    val tcfVersion: String?,
)

data class GraphQlFollowingFeedResponse(
    val data: FollowingFeedData?,
    val errors: List<GraphQlError>? = null,
) {
    fun toStreamResponse(): StreamResponse {
        val followingFeed = data?.followingFeed
        val items = followingFeed?.items
            ?.mapNotNull { it.toStreamItem() }
            .orEmpty()

        return StreamResponse(
            collection = items,
            next_href = followingFeed?.page?.nextPage,
        )
    }

    fun errorMessage(): String? = errors
        ?.mapNotNull { it.message?.takeIf(String::isNotBlank) }
        ?.joinToString("\n")
}

data class GraphQlError(
    val message: String?,
)

data class FollowingFeedData(
    val followingFeed: FollowingFeedPayload?,
)

data class FollowingFeedPayload(
    val items: List<FollowingFeedItem>?,
    val page: FollowingFeedPage?,
)

data class FollowingFeedPage(
    val nextPage: String?,
)

data class FollowingFeedItem(
    @SerializedName("__typename") val typename: String?,
    val createdAt: String?,
    val item: FollowingFeedEntity?,
    val reposter: FollowingFeedUser?,
    val promoter: FollowingFeedUser?,
) {
    fun toStreamItem(): StreamItem? {
        val track = item?.toTrack()
        val playlist = item?.toPlaylist()
        if (track == null && playlist == null) return null

        val isRepost = typename == "RepostedItem"
        val entityType = if (track != null) "track" else "playlist"
        val type = when {
            isRepost -> "$entityType-repost"
            typename == "PromotedItem" -> "$entityType-promoted"
            else -> entityType
        }

        return StreamItem(
            type = type,
            track = track,
            playlist = playlist,
            user = if (isRepost) reposter?.toUser() else promoter?.toUser(),
            createdAt = createdAt,
        )
    }
}

data class FollowingFeedEntity(
    @SerializedName("__typename") val typename: String?,
    val urn: String?,
    val title: String?,
    val user: FollowingFeedUser?,
    val fullDuration: Long?,
    val snipDuration: Long?,
    val counts: FollowingFeedCounts?,
    val transcodings: List<FollowingFeedTranscoding>?,
    val artworkUrlTemplate: String?,
    val artworkUrl: String?,
    val permalinkUrl: String?,
    val createdAt: String?,
    val description: String?,
    val genre: String?,
    val authorization: FollowingFeedAuthorization?,
    val releaseDate: String?,
    val duration: Long?,
    val trackCount: Int?,
    val playlistCategory: String?,
    val paginatedTracks: FollowingFeedPaginatedTracks?,
) {
    fun toTrack(): Track? {
        if (typename != "Track") return null
        val id = urn.toSoundCloudId()
        if (id == 0L) return null

        return Track(
            id = id,
            title = title,
            artworkUrl = artworkUrlTemplate.toArtworkUrl(),
            durationMs = fullDuration ?: snipDuration,
            user = user?.toUser(),
            media = transcodings
                ?.mapNotNull { it.toTranscoding() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { Media(it) },
            genre = genre,
            permalinkUrl = permalinkUrl,
            description = description,
            createdAt = createdAt,
            playbackCount = counts?.plays ?: 0,
            likesCount = counts?.likes ?: 0,
            repostsCount = counts?.reposts ?: 0,
            commentCount = counts?.comments ?: 0,
            policy = authorization?.policy,
            monetizationModel = authorization?.monetizationModel,
            kind = "track",
        )
    }

    fun toPlaylist(): Playlist? {
        if (typename != "Playlist") return null
        val id = urn.toSoundCloudId()
        if (id == 0L) return null

        val isSystemPlaylist = urn?.startsWith("soundcloud:system-playlists:") == true
        val isAlbum = playlistCategory.equals("ALBUM", ignoreCase = true)

        return Playlist(
            id = id,
            title = title,
            artworkUrl = artworkUrl,
            calculatedArtworkUrl = null,
            trackCount = trackCount,
            user = user?.toUser(),
            tracks = paginatedTracks?.tracks?.mapNotNull { it.toTrack() },
            isAlbum = isAlbum,
            permalinkUrl = permalinkUrl,
            createdAt = createdAt,
            urn = urn,
            setType = if (isAlbum) "album" else null,
            releaseDate = releaseDate,
            kind = when {
                isSystemPlaylist -> "system-playlist"
                isAlbum -> "album"
                else -> "playlist"
            },
        )
    }
}

data class FollowingFeedUser(
    val urn: String?,
    val name: String?,
    val username: String?,
    val avatarUrl: String?,
    val permalinkUrl: String?,
    val city: String?,
    val countryCode: String?,
    val followersCount: Int?,
    val followingsCount: Int?,
    val tracksCount: Int?,
    val description: String?,
    val verified: Boolean?,
) {
    fun toUser(): User = User(
        id = urn.toSoundCloudId(),
        username = username ?: name,
        avatarUrl = avatarUrl,
        city = city,
        countryCode = countryCode,
        followersCount = followersCount ?: 0,
        followingsCount = followingsCount ?: 0,
        trackCount = tracksCount ?: 0,
        description = description,
        permalinkUrl = permalinkUrl,
        verified = verified ?: false,
        urn = urn,
    )
}

data class FollowingFeedCounts(
    val plays: Int?,
    val likes: Int?,
    val reposts: Int?,
    val comments: Int?,
)

data class FollowingFeedAuthorization(
    val policy: String?,
    val monetizationModel: String?,
)

data class FollowingFeedTranscoding(
    val url: String?,
    val preset: String?,
    val format: FollowingFeedFormat?,
) {
    fun toTranscoding(): Transcoding? {
        val safeUrl = url?.takeIf(String::isNotBlank) ?: return null
        return Transcoding(
            url = safeUrl,
            preset = preset.orEmpty(),
            format = Format(
                protocol = format?.protocol,
                mimeType = format?.mimeType,
            ),
        )
    }
}

data class FollowingFeedFormat(
    val protocol: String?,
    val mimeType: String?,
)

data class FollowingFeedPaginatedTracks(
    val pageInfo: FollowingFeedPlaylistPageInfo?,
    val tracks: List<FollowingFeedEntity>?,
)

data class FollowingFeedPlaylistPageInfo(
    val nextPage: String?,
    val hasNextPage: Boolean?,
)

private fun String?.toSoundCloudId(): Long {
    if (this.isNullOrBlank()) return 0L
    return substringAfterLast(':').toLongOrNull() ?: toLongOrNull() ?: 0L
}

private fun String?.toArtworkUrl(): String? {
    if (this.isNullOrBlank()) return null
    return replace("{size}", "large")
}
