package com.alananasss.kittytune.data.upload

import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.ArtworkUploadRequest
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class UploadRepository {

    private val api = RetrofitClient.create()
    private val s3HttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun checkEligibility(): Result<UploadEligibilityResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.getUploadEligibility()
            }
        }

    suspend fun fetchUploadPolicy(fileName: String, fileSize: Long): Result<UploadPolicyResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.fetchUploadPolicy(UploadPolicyRequest(filename = fileName, filesize = fileSize))
            }
        }

    suspend fun uploadFileToS3(
        file: File,
        policy: UploadPolicyResponse,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val contentType = policy.headers["Content-Type"]
                ?: policy.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
                ?: "audio/mpeg"

            val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

            val progressBody = ProgressRequestBody(
                file = file,
                mediaType = mediaType,
                onProgress = onProgress
            )

            val requestBuilder = Request.Builder().url(policy.url)
            policy.headers.forEach { (key, value) -> requestBuilder.header(key, value) }

            val method = when (policy.method.uppercase()) {
                "POST" -> "POST"
                else -> "PUT"
            }
            val request = requestBuilder.method(method, progressBody).build()

            val response = s3HttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body.string()
                response.close()
                throw Exception("HTTP ${response.code}: $body")
            }
            response.close()
            policy.uid
        }
    }

    suspend fun createTrack(
        uid: String,
        metadata: UploadMetadata,
        originalFilename: String
    ): Result<CreatedTrackResult> = withContext(Dispatchers.IO) {
        runCatching {
            val publisherMeta = if (metadata.artist.isNotBlank()
                || metadata.albumTitle.isNotBlank()
                || metadata.isrc.isNotBlank() || metadata.iswc.isNotBlank()
                || metadata.upcOrEan.isNotBlank() || metadata.pLine.isNotBlank() || metadata.cLine.isNotBlank()
                || metadata.publisher.isNotBlank() || metadata.composer.isNotBlank()
                || metadata.releaseTitle.isNotBlank() || !metadata.containsMusic || metadata.explicitContent
            ) {
                PublisherMetadataInput(
                    artist = metadata.artist.takeIf { it.isNotBlank() },
                    albumTitle = metadata.albumTitle.takeIf { it.isNotBlank() },
                    containsMusic = metadata.containsMusic,
                    publisher = metadata.publisher.takeIf { it.isNotBlank() },
                    iswc = metadata.iswc.takeIf { it.isNotBlank() },
                    upcOrEan = metadata.upcOrEan.takeIf { it.isNotBlank() },
                    explicit = metadata.explicitContent,
                    cLine = metadata.cLine.takeIf { it.isNotBlank() },
                    pLine = metadata.pLine.takeIf { it.isNotBlank() },
                    composer = metadata.composer.takeIf { it.isNotBlank() },
                    releaseTitle = metadata.releaseTitle.takeIf { it.isNotBlank() },
                    isrc = metadata.isrc.takeIf { it.isNotBlank() }
                )
            } else null

            val scheduleInput = if (metadata.isScheduled && metadata.scheduledDateEpochMs != null) {
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(metadata.scheduledDateEpochMs))
                val tz = metadata.scheduledTimezone?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id
                TrackScheduleInput(
                    scheduledPublicDate = isoDate,
                    scheduledTimezone = tz
                )
            } else null

            val geoBlockingInput = when (metadata.geoBlockingMode) {
                GeoBlockingMode.EVERYWHERE -> null
                GeoBlockingMode.EXCLUSIVE -> {
                    val list = metadata.geoBlockingRegions
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotBlank() }
                    if (list.isNotEmpty()) GeoBlockingInput(exclusiveRegions = list, blockedRegions = null) else null
                }
                GeoBlockingMode.BLOCKED -> {
                    val list = metadata.geoBlockingRegions
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotBlank() }
                    if (list.isNotEmpty()) GeoBlockingInput(exclusiveRegions = null, blockedRegions = list) else null
                }
            }

            val trackInputData = TrackInputData(
                title = metadata.title,
                originalFilename = originalFilename,
                permalink = metadata.permalink.takeIf { it.isNotBlank() },
                description = metadata.description.takeIf { it.isNotBlank() },
                genre = metadata.genre.takeIf { it.isNotBlank() },
                tagList = if (metadata.tags.isNotEmpty()) metadata.tags.joinToString(",") else null,
                shareAccess = if (metadata.isScheduled) "PRIVATE" else if (metadata.privacy == TrackPrivacy.PUBLIC) "PUBLIC" else "PRIVATE",
                commentable = metadata.commentable,
                revealComments = metadata.revealComments,
                revealStats = metadata.revealStats,
                downloadable = metadata.downloadable,
                feedable = metadata.feedable,
                embeddable = metadata.embeddable,
                apiStreamable = metadata.apiStreamable,
                caption = metadata.caption.takeIf { it.isNotBlank() },
                labelName = metadata.labelName.takeIf { it.isNotBlank() },
                license = metadata.license.apiValue,
                releaseDate = metadata.releaseDate,
                purchaseTitle = metadata.purchaseTitle.takeIf { it.isNotBlank() },
                purchaseUrl = metadata.purchaseUrl.takeIf { it.isNotBlank() },
                publisherMetadata = publisherMeta,
                schedule = scheduleInput,
                snippetPresets = if (metadata.snippetStartSeconds != null && metadata.snippetEndSeconds != null) {
                    TrackSnippetPresetsInput(
                        startSeconds = metadata.snippetStartSeconds,
                        endSeconds = metadata.snippetEndSeconds
                    )
                } else null,
                geoBlocking = geoBlockingInput
            )

            val payload = CreateTrackInputPayload(uid = uid, trackInput = trackInputData)
            val request = CreateTrackGraphQlRequest(
                variables = CreateTrackVariables(createTrackInput = payload)
            )

            val response = api.createTrackGraphQl(request)

            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.joinToString { it.message })
            }

            response.data?.createTrack
                ?: throw Exception("Empty track creation response")
        }
    }

    suspend fun createTranscoding(uid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.createTranscodingGraphQl(
                    CreateTranscodingGraphQlRequest(
                        variables = TranscodingInputVariables(input = TranscodingUidInput(uid = uid))
                    )
                )
                val errors = response.errors
                if (!errors.isNullOrEmpty()) {
                    throw Exception(errors.joinToString { it.message })
                }
                Unit
            }
        }

    fun pollTranscodingStatus(uid: String): Flow<TranscodingStatus> = flow {
        var attempts = 0
        val maxAttempts = 120
        while (attempts < maxAttempts) {
            val response = runCatching {
                api.getTranscodingStatusGraphQl(
                    TranscodingStatusGraphQlRequest(
                        variables = TranscodingInputVariables(input = TranscodingUidInput(uid = uid))
                    )
                )
            }.getOrNull()

            val status = TranscodingStatus.from(
                response?.data?.transcodingStatus?.status
            )
            emit(status)

            when (status) {
                TranscodingStatus.FINISHED, TranscodingStatus.FAILURE -> return@flow
                else -> {
                    delay(3_000L)
                    attempts++
                }
            }
        }
        emit(TranscodingStatus.FAILURE)
    }.flowOn(Dispatchers.IO)

    suspend fun uploadArtwork(trackUrnOrId: String, image: BufferedImage): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baos = ByteArrayOutputStream()
                val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                val g = rgbImage.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()
                ImageIO.write(rgbImage, "JPEG", baos)
                val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
                val body = ArtworkUploadRequest(imageData = base64)
                val cleanId = trackUrnOrId.substringAfterLast(":")
                val trackUrn = "soundcloud:tracks:$cleanId"
                val response = api.uploadTrackArtwork(trackUrn, body)
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    throw Exception("Artwork upload failed: ${response.code()} - $errorBody")
                }
                Unit
            }
        }

    suspend fun getEditableTrack(trackUrn: String): Result<EditableTrackItemData?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fullUrn = if (trackUrn.startsWith("soundcloud:tracks:")) trackUrn else "soundcloud:tracks:$trackUrn"
                val request = FetchEditableTrackGraphQlRequest(
                    variables = FetchEditableTrackVariables(
                        allTracksInput = FetchEditableTrackInputWrapper(
                            trackKeys = FetchEditableTrackKeys(urn = fullUrn)
                        )
                    )
                )
                val response = api.fetchEditableTrackGraphQl(request)
                val errors = response.errors
                if (!errors.isNullOrEmpty()) {
                    throw Exception(errors.joinToString { it.message })
                }
                response.data?.allTracks?.firstOrNull()
            }
        }

    suspend fun getTrackById(trackIdOrUrn: String): Result<Track?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanId = trackIdOrUrn.substringAfterLast(":")
                api.getTracksByIds(cleanId).firstOrNull()
            }
        }

    suspend fun editTrack(
        trackUrn: String,
        metadata: UploadMetadata,
        replacingUid: String? = null,
        replacingFilename: String? = null
    ): Result<CreatedTrackResult> = withContext(Dispatchers.IO) {
        runCatching {
            val publisherMeta = if (metadata.artist.isNotBlank()
                || metadata.albumTitle.isNotBlank()
                || metadata.isrc.isNotBlank() || metadata.iswc.isNotBlank()
                || metadata.upcOrEan.isNotBlank() || metadata.pLine.isNotBlank() || metadata.cLine.isNotBlank()
                || metadata.publisher.isNotBlank() || metadata.composer.isNotBlank()
                || metadata.releaseTitle.isNotBlank() || !metadata.containsMusic || metadata.explicitContent
            ) {
                PublisherMetadataInput(
                    artist = metadata.artist.takeIf { it.isNotBlank() },
                    albumTitle = metadata.albumTitle.takeIf { it.isNotBlank() },
                    containsMusic = metadata.containsMusic,
                    publisher = metadata.publisher.takeIf { it.isNotBlank() },
                    iswc = metadata.iswc.takeIf { it.isNotBlank() },
                    upcOrEan = metadata.upcOrEan.takeIf { it.isNotBlank() },
                    explicit = metadata.explicitContent,
                    cLine = metadata.cLine.takeIf { it.isNotBlank() },
                    pLine = metadata.pLine.takeIf { it.isNotBlank() },
                    composer = metadata.composer.takeIf { it.isNotBlank() },
                    releaseTitle = metadata.releaseTitle.takeIf { it.isNotBlank() },
                    isrc = metadata.isrc.takeIf { it.isNotBlank() }
                )
            } else null

            val scheduleInput = if (metadata.isScheduled && metadata.scheduledDateEpochMs != null) {
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(metadata.scheduledDateEpochMs))
                val tz = metadata.scheduledTimezone?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id
                TrackScheduleInput(
                    scheduledPublicDate = isoDate,
                    scheduledTimezone = tz
                )
            } else null

            val geoBlockingInput = when (metadata.geoBlockingMode) {
                GeoBlockingMode.EVERYWHERE -> null
                GeoBlockingMode.EXCLUSIVE -> {
                    val list = metadata.geoBlockingRegions
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotBlank() }
                    if (list.isNotEmpty()) GeoBlockingInput(exclusiveRegions = list, blockedRegions = null) else null
                }
                GeoBlockingMode.BLOCKED -> {
                    val list = metadata.geoBlockingRegions
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotBlank() }
                    if (list.isNotEmpty()) GeoBlockingInput(exclusiveRegions = null, blockedRegions = list) else null
                }
            }

            val trackInputData = TrackInputData(
                title = metadata.title,
                originalFilename = replacingFilename?.takeIf { it.isNotBlank() },
                permalink = metadata.permalink.takeIf { it.isNotBlank() },
                description = metadata.description.takeIf { it.isNotBlank() },
                genre = metadata.genre.takeIf { it.isNotBlank() },
                tagList = if (metadata.tags.isNotEmpty()) metadata.tags.joinToString(",") else null,
                shareAccess = if (metadata.isScheduled) "PRIVATE" else if (metadata.privacy == TrackPrivacy.PUBLIC) "PUBLIC" else "PRIVATE",
                commentable = metadata.commentable,
                revealComments = metadata.revealComments,
                revealStats = metadata.revealStats,
                downloadable = metadata.downloadable,
                feedable = metadata.feedable,
                embeddable = metadata.embeddable,
                apiStreamable = metadata.apiStreamable,
                caption = metadata.caption.takeIf { it.isNotBlank() },
                labelName = metadata.labelName.takeIf { it.isNotBlank() },
                license = metadata.license.apiValue,
                releaseDate = metadata.releaseDate?.takeIf { it.isNotBlank() },
                purchaseTitle = metadata.purchaseTitle.takeIf { it.isNotBlank() },
                purchaseUrl = metadata.purchaseUrl.takeIf { it.isNotBlank() },
                publisherMetadata = publisherMeta,
                schedule = scheduleInput,
                snippetPresets = if (metadata.snippetStartSeconds != null && metadata.snippetEndSeconds != null) {
                    TrackSnippetPresetsInput(
                        startSeconds = metadata.snippetStartSeconds,
                        endSeconds = metadata.snippetEndSeconds
                    )
                } else null,
                geoBlocking = geoBlockingInput
            )

            val fullUrn = if (trackUrn.startsWith("soundcloud:tracks:")) trackUrn else "soundcloud:tracks:$trackUrn"
            val payload = EditTrackInputPayload(
                urn = fullUrn,
                trackInput = trackInputData,
                replacingUid = replacingUid?.takeIf { it.isNotBlank() },
                replacingOriginalFilename = replacingFilename?.takeIf { it.isNotBlank() }
            )
            val request = EditTrackGraphQlRequest(
                variables = EditTrackVariables(trackEditInput = payload)
            )

            val response = api.editTrackGraphQl(request)

            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.joinToString { it.message })
            }

            response.data?.editTrack
                ?: CreatedTrackResult(urn = fullUrn)
        }
    }

    suspend fun deleteTrack(trackUrnOrId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = trackUrnOrId.substringAfterLast(":")
            val fullUrn = if (trackUrnOrId.startsWith("soundcloud:tracks:")) trackUrnOrId else "soundcloud:tracks:$trackUrnOrId"

            val graphQlResult = runCatching {
                val request = DeleteTrackGraphQlRequest(variables = DeleteTrackVariables(urn = fullUrn))
                val response = api.deleteTrackGraphQl(request)
                if (!response.errors.isNullOrEmpty()) {
                    throw Exception(response.errors.joinToString { it.message })
                }
                Unit
            }

            val longId = cleanId.toLongOrNull()
            if (longId != null && graphQlResult.isFailure) {
                val restResponse = api.deleteTrackRest(longId)
                if (!restResponse.isSuccessful && restResponse.code() != 404) {
                    throw Exception("Failed to delete track (HTTP ${restResponse.code()})")
                }
            } else if (graphQlResult.isFailure) {
                graphQlResult.getOrThrow()
            }
            Unit
        }
    }

    suspend fun getBuyModule(trackUrn: String): Result<BuyModuleItemData?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fullUrn = if (trackUrn.startsWith("soundcloud:tracks:")) trackUrn else "soundcloud:tracks:$trackUrn"
                val request = FetchBuyModuleGraphQlRequest(
                    variables = FetchBuyModuleVariables(urn = fullUrn)
                )
                val response = api.fetchBuyModuleGraphQl(request)
                response.data?.buyModule
            }
        }

    suspend fun createOrUpdateBuyModule(
        trackUrn: String,
        type: String,
        title: String,
        price: String,
        link: String,
        description: String?,
        linkTitle: String?,
        imageData: String?,
        imageUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fullUrn = if (trackUrn.startsWith("soundcloud:tracks:")) trackUrn else "soundcloud:tracks:$trackUrn"
            val request = CreateBuyModuleGraphQlRequest(
                variables = CreateBuyModuleVariables(
                    input = CreateBuyModuleInput(
                        trackUrn = fullUrn,
                        type = type,
                        title = title,
                        price = price,
                        link = link,
                        description = description?.takeIf { it.isNotBlank() },
                        linkTitle = linkTitle?.takeIf { it.isNotBlank() },
                        imageData = imageData?.takeIf { it.isNotBlank() },
                        imageUrl = imageUrl?.takeIf { it.isNotBlank() }
                    )
                )
            )
            val response = api.createBuyModuleGraphQl(request)
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.joinToString { it.message })
            }
            val resultTypename = response.data?.createBuyModule?.get("__typename")?.asString
            if (resultTypename != null && (resultTypename.endsWith("Error") || resultTypename.contains("Error"))) {
                throw Exception("SoundCloud error: $resultTypename")
            }
            Unit
        }
    }

    suspend fun deleteBuyModule(trackUrn: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fullUrn = if (trackUrn.startsWith("soundcloud:tracks:")) trackUrn else "soundcloud:tracks:$trackUrn"
            val request = DeleteBuyModuleGraphQlRequest(
                variables = DeleteBuyModuleVariables(
                    input = DeleteBuyModuleInput(urn = fullUrn)
                )
            )
            val response = api.deleteBuyModuleGraphQl(request)
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.joinToString { it.message })
            }
            val resultTypename = response.data?.deleteBuyModule?.get("__typename")?.asString
            if (resultTypename != null && (resultTypename.endsWith("Error") || resultTypename.contains("Error"))) {
                throw Exception("SoundCloud error: $resultTypename")
            }
            Unit
        }
    }

    private class ProgressRequestBody(
        private val file: File,
        private val mediaType: okhttp3.MediaType,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {

        private val totalBytes = file.length()

        override fun contentType() = mediaType
        override fun contentLength() = totalBytes

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesWritten = 0L
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    bytesWritten += read
                    onProgress(bytesWritten.toFloat() / totalBytes.coerceAtLeast(1L))
                }
            }
            sink.flush()
        }
    }
}
