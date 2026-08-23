package com.alananasss.kittytune.ui.upload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.upload.*
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.profile.ProfileViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO

class UploadViewModel {

    private val repo = UploadRepository()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val isLoggedIn: Boolean
        get() = !TokenManager.isGuestMode() && !TokenManager.getAccessToken().isNullOrEmpty()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState = _uploadState.asStateFlow()

    var editingTrack by mutableStateOf<Track?>(null)
    var editingTrackId by mutableStateOf<Long?>(null)
    var editingTrackUrn by mutableStateOf<String?>(null)
    var existingArtworkUrl by mutableStateOf<String?>(null)
    val isEditMode: Boolean get() = editingTrackId != null
    var isSavingEdit by mutableStateOf(false)
    var isDeletingTrack by mutableStateOf(false)
    var showDeleteConfirmationDialog by mutableStateOf(false)

    // Form fields
    var title by mutableStateOf("")
    var userPermalink by mutableStateOf("")
    var permalink by mutableStateOf("")
    var isPermalinkManuallyEdited by mutableStateOf(false)
    var artist by mutableStateOf("")

    var description by mutableStateOf("")
    var genre by mutableStateOf("")
    var tagInput by mutableStateOf("")
    var tags by mutableStateOf(listOf<String>())
    var privacy by mutableStateOf(TrackPrivacy.PUBLIC)
    var downloadable by mutableStateOf(false)
    var offlineListening by mutableStateOf(true)
    var feedable by mutableStateOf(false)
    var embeddable by mutableStateOf(true)
    var apiStreamable by mutableStateOf(true)
    var commentable by mutableStateOf(true)
    var revealComments by mutableStateOf(true)
    var revealStats by mutableStateOf(true)
    var caption by mutableStateOf("")
    var labelName by mutableStateOf("")
    var releaseDate by mutableStateOf("")
    var license by mutableStateOf(TrackLicense.ALL_RIGHTS_RESERVED)
    var isrc by mutableStateOf("")
    var iswc by mutableStateOf("")
    var publisher by mutableStateOf("")
    var composer by mutableStateOf("")
    var purchaseTitle by mutableStateOf("")
    var purchaseUrl by mutableStateOf("")
    var explicitContent by mutableStateOf(false)
    var containsMusic by mutableStateOf(true)
    var albumTitle by mutableStateOf("")
    var releaseTitle by mutableStateOf("")
    var upcOrEan by mutableStateOf("")
    var pLine by mutableStateOf("")
    var cLine by mutableStateOf("")
    var selectedCategoryTab by mutableIntStateOf(0)
    var showAdvancedFields by mutableStateOf(false)

    // Scheduling
    var isSchedulingEnabled by mutableStateOf(false)
    var scheduledEpochMs by mutableStateOf<Long?>(null)
    var scheduledTimezone by mutableStateOf(TimeZone.getDefault().id)

    // Artwork
    var artworkBitmap by mutableStateOf<BufferedImage?>(null)
    var artworkFile by mutableStateOf<File?>(null)
    var tempArtworkBitmap by mutableStateOf<BufferedImage?>(null)

    // Storefront / Commerce Option
    var selectedCommerceOption by mutableStateOf(CommerceOption.BUY_LINK)
    var hasStorefront by mutableStateOf(false)
    var storefrontType by mutableStateOf(BuyModuleType.DIGITAL)
    var storefrontTitle by mutableStateOf("")
    var storefrontPrice by mutableStateOf("")
    var storefrontLink by mutableStateOf("")
    var storefrontLinkTitle by mutableStateOf("")
    var storefrontDescription by mutableStateOf("")
    var storefrontImageFile by mutableStateOf<File?>(null)
    var storefrontBitmap by mutableStateOf<BufferedImage?>(null)
    var storefrontImageUrl by mutableStateOf<String?>(null)
    var isSavingStorefront by mutableStateOf(false)
    var isDeletingStorefront by mutableStateOf(false)
    var storefrontErrorMessage by mutableStateOf<String?>(null)

    // Geo Blocking
    var geoBlockingMode by mutableStateOf(GeoBlockingMode.EVERYWHERE)
    var geoBlockingRegions by mutableStateOf("")

    fun toggleCountryCode(code: String) {
        val upper = code.trim().uppercase()
        if (upper.isBlank()) return
        val currentList = geoBlockingRegions
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (currentList.contains(upper)) {
            currentList.remove(upper)
        } else {
            currentList.add(upper)
        }
        geoBlockingRegions = currentList.joinToString(", ")
    }

    // Snippet preview
    var snippetStartSeconds by mutableStateOf(0)
    var snippetEndSeconds by mutableStateOf(20)
    var trackDurationSeconds by mutableStateOf(0)
    var isSnippetCustomized by mutableStateOf(false)
    var waveformUrl by mutableStateOf<String?>(null)
    var isPlayingSnippet by mutableStateOf(false)

    // Selected file
    var selectedFile by mutableStateOf<File?>(null)
    var selectedFileName by mutableStateOf("")
    var selectedFileSizeBytes by mutableStateOf(0L)
    var uploadFileProgress by mutableStateOf(0f)

    // Checklist
    var isChecklistDismissed by mutableStateOf(false)
    val isTitleDone: Boolean get() = title.isNotBlank()
    val isArtworkDone: Boolean get() = artworkBitmap != null || !existingArtworkUrl.isNullOrBlank()
    val isGenreDone: Boolean get() = genre.isNotBlank()
    val isDescriptionDone: Boolean get() = description.isNotBlank()
    val isTagsDone: Boolean get() = tags.isNotEmpty()

    val completedChecklistCount: Int
        get() = listOf(isTitleDone, isArtworkDone, isGenreDone, isDescriptionDone).count { it }

    // Validation
    val isTitleValid: Boolean get() = title.isNotBlank()
    val isArtistValid: Boolean get() = artist.isNotBlank()
    val isPermalinkValid: Boolean
        get() = permalink.isBlank() || (permalink.matches(Regex("^[a-zA-Z0-9-_]+$")) && permalink.any { it.isLetter() })
    val isPurchaseUrlValid: Boolean get() = purchaseUrl.isBlank() || isValidUrl(purchaseUrl)
    val isStorefrontLinkValid: Boolean get() = !hasStorefront || storefrontLink.isBlank() || isValidUrl(storefrontLink)

    val canUpload: Boolean get() = isTitleValid && isArtistValid && isPermalinkValid && isPurchaseUrlValid && selectedFile != null
    val canSubmit: Boolean
        get() = if (isEditMode) {
            isTitleValid && isArtistValid && isPermalinkValid && isPurchaseUrlValid && isStorefrontLinkValid && !isSavingEdit && !isDeletingTrack
        } else {
            canUpload
        }

    val hasUnsavedChanges: Boolean
        get() = if (isEditMode) {
            title != (editingTrack?.title ?: "") ||
                    artist != (editingTrack?.user?.username ?: "") ||
                    description != (editingTrack?.description ?: "") ||
                    genre != (editingTrack?.genre ?: "") ||
                    selectedFile != null ||
                    artworkBitmap != null
        } else {
            selectedFile != null || title.isNotBlank() || description.isNotBlank() || artworkBitmap != null
        }

    private var uploadJob: Job? = null

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        if (!isLoggedIn) return
        scope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.create()
                val me = api.getMe()
                withContext(Dispatchers.Main) {
                    if (artist.isBlank()) {
                        artist = me.username ?: ""
                    }
                    userPermalink = me.permalink ?: (if (me.id != 0L) "user-${me.id}" else "")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        title = newTitle
        if (!isPermalinkManuallyEdited) {
            permalink = generateSlug(newTitle)
        }
    }

    fun onPermalinkChanged(newPermalink: String) {
        permalink = newPermalink.lowercase().replace("\n", "").replace(" ", "-")
        isPermalinkManuallyEdited = true
    }

    private fun generateSlug(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9-_]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    fun onFileSelected(file: File) {
        selectedFile = file
        selectedFileName = file.name
        selectedFileSizeBytes = file.length()

        // Inspect ID3 tags
        scope.launch(Dispatchers.IO) {
            try {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag
                val header = audioFile.audioHeader
                val duration = header?.trackLength ?: 0

                withContext(Dispatchers.Main) {
                    trackDurationSeconds = duration
                    snippetEndSeconds = minOf(20, duration.coerceAtLeast(20))

                    if (tag != null) {
                        val parsedTitle = tag.getFirst(FieldKey.TITLE)?.trim()
                        if (!parsedTitle.isNullOrBlank() && title.isBlank()) {
                            onTitleChanged(parsedTitle)
                        }

                        val parsedArtist = tag.getFirst(FieldKey.ARTIST)?.trim()
                        if (!parsedArtist.isNullOrBlank() && (artist.isBlank() || artist == "user")) {
                            artist = parsedArtist
                        }

                        val parsedAlbum = tag.getFirst(FieldKey.ALBUM)?.trim()
                        if (!parsedAlbum.isNullOrBlank() && albumTitle.isBlank()) {
                            albumTitle = parsedAlbum
                        }

                        val parsedGenre = tag.getFirst(FieldKey.GENRE)?.trim()
                        if (!parsedGenre.isNullOrBlank() && genre.isBlank()) {
                            val matchedGenre = SOUNDCLOUD_GENRES.firstOrNull { it.equals(parsedGenre, ignoreCase = true) }
                            if (matchedGenre != null) {
                                genre = matchedGenre
                            }
                        }

                        val parsedYear = tag.getFirst(FieldKey.YEAR)?.trim()
                        if (!parsedYear.isNullOrBlank() && releaseDate.isBlank()) {
                            releaseDate = if (parsedYear.length == 4) "$parsedYear-01-01" else parsedYear
                        }

                        val parsedComposer = tag.getFirst(FieldKey.COMPOSER)?.trim()
                        if (!parsedComposer.isNullOrBlank() && composer.isBlank()) {
                            composer = parsedComposer
                        }

                        val parsedIsrc = tag.getFirst(FieldKey.ISRC)?.trim()
                        if (!parsedIsrc.isNullOrBlank() && isrc.isBlank()) {
                            isrc = parsedIsrc
                        }

                        val parsedArtwork = tag.firstArtwork
                        if (parsedArtwork != null && parsedArtwork.binaryData != null && artworkBitmap == null) {
                            try {
                                val img = ImageIO.read(parsedArtwork.binaryData.inputStream())
                                if (img != null) {
                                    artworkBitmap = img
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (title.isBlank()) {
            val baseName = selectedFileName
                .substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
            onTitleChanged(baseName)
        }
        _uploadState.value = UploadState.FileSelected(selectedFileName, selectedFileSizeBytes)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim().removePrefix("#")
        if (trimmed.isNotBlank() && !tags.contains(trimmed) && tags.size < 20) {
            tags = tags + trimmed
        }
        tagInput = ""
    }

    fun removeTag(tag: String) {
        tags = tags - tag
    }

    fun parseTagList(rawTags: String): List<String> {
        val result = mutableListOf<String>()
        val regex = Regex("""("[^"]+"|\S+)""")
        regex.findAll(rawTags).forEach { match ->
            val clean = match.value.trim().removeSurrounding("\"").removePrefix("#").trim()
            if (clean.isNotBlank() && !result.contains(clean)) {
                result.add(clean)
            }
        }
        return result
    }

    fun formatTagList(): String {
        return tags.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
    }

    fun toggleScheduling(enabled: Boolean) {
        isSchedulingEnabled = enabled
        if (enabled) {
            privacy = TrackPrivacy.PRIVATE
            if (scheduledEpochMs == null) {
                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                scheduledEpochMs = calendar.timeInMillis
            }
        }
    }

    fun onArtworkSelected(file: File) {
        artworkFile = file
        scope.launch(Dispatchers.IO) {
            try {
                val img = ImageIO.read(file)
                withContext(Dispatchers.Main) {
                    if (img != null) {
                        tempArtworkBitmap = img
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeArtwork() {
        artworkBitmap = null
        artworkFile = null
        tempArtworkBitmap = null
        existingArtworkUrl = null
    }

    fun onStorefrontImageSelected(file: File) {
        storefrontImageFile = file
        scope.launch(Dispatchers.IO) {
            try {
                val img = ImageIO.read(file)
                withContext(Dispatchers.Main) {
                    if (img != null) {
                        storefrontBitmap = img
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeStorefrontImage() {
        storefrontBitmap = null
        storefrontImageFile = null
        storefrontImageUrl = null
    }

    fun loadTrackForEditing(trackIdOrUrn: String) {
        val cleanId = trackIdOrUrn.substringAfterLast(":")
        val longId = cleanId.toLongOrNull() ?: return
        editingTrackId = longId
        editingTrackUrn = "soundcloud:tracks:$cleanId"

        scope.launch(Dispatchers.IO) {
            try {
                val editable = repo.getEditableTrack(editingTrackUrn!!).getOrNull()
                val trackModel = repo.getTrackById(cleanId).getOrNull()

                withContext(Dispatchers.Main) {
                    editingTrack = trackModel
                    existingArtworkUrl = trackModel?.artworkUrl ?: trackModel?.user?.avatarUrl ?: editable?.artworkUrlTemplate
                    waveformUrl = trackModel?.waveformUrl

                    if (editable != null) {
                        title = editable.title ?: trackModel?.title ?: ""
                        artist = editable.artist ?: trackModel?.user?.username ?: ""
                        permalink = editable.permalink ?: trackModel?.permalink ?: ""
                        description = editable.description ?: ""
                        caption = editable.caption ?: ""
                        genre = editable.genre ?: trackModel?.genre ?: ""
                        tags = editable.userTags ?: (trackModel?.tagList?.let { parseTagList(it) } ?: emptyList())
                        privacy = if (editable.isPublic == true || editable.share?.access == "PUBLIC") TrackPrivacy.PUBLIC else TrackPrivacy.PRIVATE
                        labelName = editable.labelName ?: ""
                        releaseDate = editable.releaseDate ?: ""
                        license = TrackLicense.entries.firstOrNull { it.apiValue.equals(editable.license, ignoreCase = true) } ?: TrackLicense.ALL_RIGHTS_RESERVED
                        purchaseTitle = editable.purchaseTitle ?: ""
                        purchaseUrl = editable.purchaseUrl ?: ""
                        commentable = editable.commentable ?: true
                        revealComments = editable.revealComments ?: true
                        revealStats = editable.revealStats ?: editable.displayStats ?: true
                        downloadable = editable.downloadable ?: false
                        feedable = editable.feedable ?: false
                        embeddable = editable.embeddable ?: true
                        apiStreamable = editable.apiStreamable ?: true

                        val pub = editable.publisherMetadata
                        if (pub != null) {
                            albumTitle = pub.albumTitle ?: ""
                            releaseTitle = pub.releaseTitle ?: ""
                            isrc = pub.isrc ?: ""
                            iswc = pub.iswc ?: ""
                            upcOrEan = pub.upcOrEan ?: ""
                            publisher = pub.publisher ?: ""
                            composer = pub.composer ?: ""
                            pLine = pub.pLine ?: ""
                            cLine = pub.cLine ?: ""
                            containsMusic = pub.containsMusic ?: true
                            explicitContent = pub.explicit ?: false
                        }

                        val geo = editable.geoBlocking
                        if (geo != null) {
                            if (!geo.exclusiveRegions.isNullOrEmpty()) {
                                geoBlockingMode = GeoBlockingMode.EXCLUSIVE
                                geoBlockingRegions = geo.exclusiveRegions.joinToString(", ")
                            } else if (!geo.blockedRegions.isNullOrEmpty()) {
                                geoBlockingMode = GeoBlockingMode.BLOCKED
                                geoBlockingRegions = geo.blockedRegions.joinToString(", ")
                            }
                        }

                        val sched = editable.schedule
                        if (sched != null && !sched.makePublicAt.isNullOrBlank()) {
                            try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }
                                val date = sdf.parse(sched.makePublicAt)
                                if (date != null && date.time > System.currentTimeMillis()) {
                                    isSchedulingEnabled = true
                                    scheduledEpochMs = date.time
                                    scheduledTimezone = sched.timezone ?: TimeZone.getDefault().id
                                    privacy = TrackPrivacy.PRIVATE
                                }
                            } catch (_: Exception) {
                            }
                        }
                    } else if (trackModel != null) {
                        title = trackModel.title ?: ""
                        artist = trackModel.user?.username ?: ""
                        permalink = trackModel.permalink ?: ""
                        description = trackModel.description ?: ""
                        genre = trackModel.genre ?: ""
                        tags = trackModel.tagList?.let { parseTagList(it) } ?: emptyList()
                        purchaseTitle = trackModel.purchaseTitle ?: ""
                        purchaseUrl = trackModel.purchaseUrl ?: ""
                        privacy = if (trackModel.sharing?.equals("private", ignoreCase = true) == true) TrackPrivacy.PRIVATE else TrackPrivacy.PUBLIC
                    }

                    // Check storefront
                    val buy = repo.getBuyModule(editingTrackUrn!!).getOrNull()
                    if (buy != null) {
                        hasStorefront = true
                        selectedCommerceOption = CommerceOption.STOREFRONT
                        storefrontType = BuyModuleType.fromValue(buy.type)
                        storefrontTitle = buy.title ?: ""
                        storefrontPrice = buy.price ?: ""
                        storefrontLink = buy.link ?: ""
                        storefrontLinkTitle = buy.linkTitle ?: ""
                        storefrontDescription = buy.description ?: ""
                        storefrontImageUrl = buy.imageUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startUpload() {
        val file = selectedFile ?: return
        if (!canUpload) return

        uploadJob?.cancel()
        uploadJob = scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Uploading(UploadStep.FETCHING_POLICY, 0.05f)
                }

                // 1. Eligibility
                val eligibility = repo.checkEligibility().getOrNull()
                if (eligibility != null && !eligibility.canUpload) {
                    val reasons = eligibility.reasons.orEmpty()
                    val msgKey = when {
                        reasons.any { it.contains("email", ignoreCase = true) } -> "upload_eligibility_not_confirmed"
                        reasons.any { it.contains("limit", ignoreCase = true) || it.contains("quota", ignoreCase = true) } -> "upload_eligibility_limit_reached"
                        else -> "upload_error_unknown"
                    }
                    withContext(Dispatchers.Main) {
                        _uploadState.value = UploadState.Error(msgKey)
                    }
                    return@launch
                }

                // 2. Upload policy
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Uploading(UploadStep.FETCHING_POLICY, 0.1f)
                }
                val policy = repo.fetchUploadPolicy(file.name, file.length()).getOrElse { e ->
                    withContext(Dispatchers.Main) {
                        _uploadState.value = UploadState.Error("upload_error_policy", e.message)
                    }
                    return@launch
                }

                // 3. Upload to S3
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_FILE, 0.15f)
                }
                val uid = repo.uploadFileToS3(file, policy) { progress ->
                    uploadFileProgress = progress
                    val mappedProgress = 0.15f + (progress * 0.45f)
                    _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_FILE, mappedProgress)
                }.getOrElse { e ->
                    withContext(Dispatchers.Main) {
                        _uploadState.value = UploadState.Error("upload_error_s3", e.message)
                    }
                    return@launch
                }

                // 4. Create Track
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Uploading(UploadStep.CREATING_TRACK, 0.65f)
                }
                val metadata = buildUploadMetadata()
                val created = repo.createTrack(uid, metadata, file.name).getOrElse { e ->
                    withContext(Dispatchers.Main) {
                        _uploadState.value = UploadState.Error("upload_error_creation", e.message)
                    }
                    return@launch
                }

                val trackUrn = created.urn
                val trackTitle = metadata.title

                // 5. Transcoding
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Uploading(UploadStep.TRANSCODING, 0.75f)
                }
                repo.createTranscoding(uid).getOrElse { e ->
                    withContext(Dispatchers.Main) {
                        _uploadState.value = UploadState.Error("upload_error_transcoding", e.message)
                    }
                    return@launch
                }

                var transcodingSuccess = false
                repo.pollTranscodingStatus(uid).collect { status ->
                    when (status) {
                        TranscodingStatus.FINISHED -> {
                            transcodingSuccess = true
                        }
                        TranscodingStatus.FAILURE -> {
                            withContext(Dispatchers.Main) {
                                _uploadState.value = UploadState.Error("upload_error_transcoding_server")
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                _uploadState.value = UploadState.Uploading(UploadStep.TRANSCODING, 0.85f)
                            }
                        }
                    }
                }

                if (!transcodingSuccess) {
                    if (_uploadState.value !is UploadState.Error) {
                        withContext(Dispatchers.Main) {
                            _uploadState.value = UploadState.Error("upload_error_transcoding_server")
                        }
                    }
                    return@launch
                }

                // 6. Artwork upload if available
                val artwork = artworkBitmap
                if (artwork != null) {
                    withContext(Dispatchers.Main) {
                        _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_ARTWORK, 0.9f)
                    }
                    repo.uploadArtwork(trackUrn, artwork)
                }

                // 7. Storefront if selected
                if (selectedCommerceOption == CommerceOption.STOREFRONT && hasStorefront && storefrontLink.isNotBlank()) {
                    saveStorefrontInternal(trackUrn)
                }

                ProfileViewModel.triggerRefresh()

                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Success(trackUrn, trackTitle)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Error("upload_error_unknown", e.message)
                }
            }
        }
    }

    fun saveTrackEdits(onSuccess: () -> Unit) {
        val urn = editingTrackUrn ?: return
        if (!canSubmit) return

        isSavingEdit = true
        scope.launch(Dispatchers.IO) {
            try {
                val newFile = selectedFile
                val replacingUid = if (newFile != null) {
                    val policy = repo.fetchUploadPolicy(newFile.name, newFile.length()).getOrElse { e ->
                        withContext(Dispatchers.Main) {
                            isSavingEdit = false
                            Toaster.show(e.message ?: "Failed to fetch replacement upload policy")
                        }
                        return@launch
                    }
                    repo.uploadFileToS3(newFile, policy) { }.getOrElse { e ->
                        withContext(Dispatchers.Main) {
                            isSavingEdit = false
                            Toaster.show(e.message ?: "Failed to upload replacement audio")
                        }
                        return@launch
                    }
                } else null

                val metadata = buildUploadMetadata()
                val result = repo.editTrack(
                    trackUrn = urn,
                    metadata = metadata,
                    replacingUid = replacingUid,
                    replacingFilename = if (replacingUid != null) selectedFileName else null
                )

                if (result.isFailure) {
                    val errorMsg = result.exceptionOrNull()?.message
                    withContext(Dispatchers.Main) {
                        isSavingEdit = false
                        val localizedMsg = if (errorMsg?.contains("permission", ignoreCase = true) == true ||
                            errorMsg?.contains("forbidden", ignoreCase = true) == true ||
                            errorMsg?.contains("pro", ignoreCase = true) == true ||
                            errorMsg?.contains("schedule", ignoreCase = true) == true
                        ) {
                            str("upload_error_schedule_next_pro")
                        } else {
                            errorMsg ?: "Failed to edit track"
                        }
                        Toaster.show(localizedMsg)
                    }
                    return@launch
                }

                // If file was replaced, poll transcoding
                if (replacingUid != null) {
                    repo.createTranscoding(replacingUid)
                    repo.pollTranscodingStatus(replacingUid).collect { status ->
                        // Wait for transcoding completion in background
                    }
                }

                // Upload new artwork if changed
                val newArtwork = artworkBitmap
                if (newArtwork != null) {
                    repo.uploadArtwork(urn, newArtwork)
                }

                // Handle storefront
                if (selectedCommerceOption == CommerceOption.STOREFRONT && hasStorefront) {
                    saveStorefrontInternal(urn)
                } else if (hasStorefront) {
                    repo.deleteBuyModule(urn)
                }

                // Refresh active track & profiles
                ProfileViewModel.triggerRefresh()
                editingTrack?.let { current ->
                    val updated = current.copy(
                        title = metadata.title,
                        description = metadata.description,
                        genre = metadata.genre
                    )
                    MusicManager.updateTrackMetadata(updated)
                }

                withContext(Dispatchers.Main) {
                    isSavingEdit = false
                    Toaster.show(str("edit_track_saved_success"))
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSavingEdit = false
                    Toaster.show(e.message ?: "Error saving edits")
                }
            }
        }
    }

    fun saveStorefront(onSuccess: (() -> Unit)? = null) {
        val trackUrn = editingTrackUrn ?: return
        if (isSavingStorefront || isDeletingStorefront) return
        isSavingStorefront = true
        storefrontErrorMessage = null

        scope.launch(Dispatchers.IO) {
            try {
                saveStorefrontInternal(trackUrn)
                withContext(Dispatchers.Main) {
                    hasStorefront = true
                    isSavingStorefront = false
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSavingStorefront = false
                    val errorMsg = e.message ?: ""
                    storefrontErrorMessage = if (errorMsg.contains("permission", ignoreCase = true) ||
                        errorMsg.contains("forbidden", ignoreCase = true) ||
                        errorMsg.contains("unauthorized", ignoreCase = true) ||
                        errorMsg.contains("pro", ignoreCase = true) ||
                        errorMsg.contains("UnknownError", ignoreCase = true)
                    ) {
                        str("upload_error_storefront_next_pro")
                    } else {
                        e.message ?: str("upload_error_storefront_save")
                    }
                }
            }
        }
    }

    fun deleteStorefront(onSuccess: (() -> Unit)? = null) {
        val trackUrn = editingTrackUrn ?: return
        if (isSavingStorefront || isDeletingStorefront) return
        isDeletingStorefront = true
        storefrontErrorMessage = null

        scope.launch(Dispatchers.IO) {
            try {
                repo.deleteBuyModule(trackUrn).onSuccess {
                    withContext(Dispatchers.Main) {
                        hasStorefront = false
                        selectedCommerceOption = CommerceOption.BUY_LINK
                        storefrontTitle = ""
                        storefrontPrice = ""
                        storefrontLink = ""
                        storefrontLinkTitle = ""
                        storefrontDescription = ""
                        storefrontImageFile = null
                        storefrontBitmap = null
                        storefrontImageUrl = null
                        purchaseUrl = ""
                        purchaseTitle = ""
                        isDeletingStorefront = false
                        onSuccess?.invoke()
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        isDeletingStorefront = false
                        storefrontErrorMessage = err.message ?: str("upload_error_storefront_delete")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDeletingStorefront = false
                    storefrontErrorMessage = e.message ?: str("upload_error_storefront_delete")
                }
            }
        }
    }

    private suspend fun saveStorefrontInternal(trackUrn: String) {
        val imgBase64 = storefrontBitmap?.let { bmp ->
            val baos = ByteArrayOutputStream()
            val rgb = BufferedImage(bmp.width, bmp.height, BufferedImage.TYPE_INT_RGB)
            val g = rgb.createGraphics()
            g.drawImage(bmp, 0, 0, null)
            g.dispose()
            ImageIO.write(rgb, "JPEG", baos)
            Base64.getEncoder().encodeToString(baos.toByteArray())
        }
        repo.createOrUpdateBuyModule(
            trackUrn = trackUrn,
            type = storefrontType.value,
            title = storefrontTitle.ifBlank { "Buy" },
            price = storefrontPrice,
            link = normalizeUrl(storefrontLink),
            description = storefrontDescription.takeIf { it.isNotBlank() },
            linkTitle = storefrontLinkTitle.takeIf { it.isNotBlank() },
            imageData = imgBase64,
            imageUrl = storefrontImageUrl
        )
    }

    fun deleteTrack(onSuccess: () -> Unit) {
        val urn = editingTrackUrn ?: return
        isDeletingTrack = true
        scope.launch(Dispatchers.IO) {
            val result = repo.deleteTrack(urn)
            if (result.isSuccess) {
                editingTrackId?.let { MusicManager.notifyTrackDeleted(it) }
                ProfileViewModel.triggerRefresh()
                delay(600)
            }
            withContext(Dispatchers.Main) {
                isDeletingTrack = false
                showDeleteConfirmationDialog = false
                if (result.isSuccess) {
                    Toaster.show(str("edit_track_deleted_success"))
                    onSuccess()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to delete track"
                    Toaster.show(err)
                }
            }
        }
    }

    private fun buildUploadMetadata(): UploadMetadata {
        return UploadMetadata(
            title = title.trim(),
            permalink = permalink.trim(),
            artist = artist.trim(),
            description = description.trim(),
            genre = genre.trim(),
            tags = tags,
            privacy = if (isSchedulingEnabled) TrackPrivacy.PRIVATE else privacy,
            caption = caption.trim(),
            labelName = labelName.trim(),
            releaseDate = releaseDate.trim().takeIf { it.isNotBlank() },
            license = license,
            downloadable = downloadable,
            offlineListening = offlineListening,
            feedable = feedable,
            embeddable = embeddable,
            apiStreamable = apiStreamable,
            commentable = commentable,
            revealComments = revealComments,
            revealStats = revealStats,
            containsMusic = containsMusic,
            albumTitle = albumTitle.trim(),
            isrc = isrc.trim(),
            iswc = iswc.trim(),
            upcOrEan = upcOrEan.trim(),
            publisher = publisher.trim(),
            composer = composer.trim(),
            releaseTitle = releaseTitle.trim(),
            pLine = pLine.trim(),
            cLine = cLine.trim(),
            explicitContent = explicitContent,
            purchaseTitle = if (selectedCommerceOption == CommerceOption.STOREFRONT && hasStorefront && storefrontLink.isNotBlank()) {
                storefrontLinkTitle.trim().ifBlank { storefrontTitle.trim().ifBlank { storefrontType.value } }
            } else if (selectedCommerceOption == CommerceOption.BUY_LINK) {
                purchaseTitle.trim()
            } else "",
            purchaseUrl = if (selectedCommerceOption == CommerceOption.STOREFRONT && hasStorefront && storefrontLink.isNotBlank()) {
                val trimmed = storefrontLink.trim()
                if (isValidUrl(trimmed)) normalizeUrl(trimmed) else ""
            } else if (selectedCommerceOption == CommerceOption.BUY_LINK && purchaseUrl.isNotBlank()) {
                val trimmed = purchaseUrl.trim()
                if (isValidUrl(trimmed)) normalizeUrl(trimmed) else ""
            } else "",
            isScheduled = isSchedulingEnabled,
            scheduledDateEpochMs = if (isSchedulingEnabled) scheduledEpochMs else null,
            scheduledTimezone = if (isSchedulingEnabled) scheduledTimezone else null,
            snippetStartSeconds = if (isSnippetCustomized) snippetStartSeconds else null,
            snippetEndSeconds = if (isSnippetCustomized) snippetEndSeconds else null,
            geoBlockingMode = geoBlockingMode,
            geoBlockingRegions = geoBlockingRegions
        )
    }

    fun reset() {
        uploadJob?.cancel()
        _uploadState.value = UploadState.Idle
        selectedFile = null
        selectedFileName = ""
        selectedFileSizeBytes = 0L
        uploadFileProgress = 0f
        editingTrack = null
        editingTrackId = null
        editingTrackUrn = null
        existingArtworkUrl = null
        title = ""
        permalink = ""
        isPermalinkManuallyEdited = false
        description = ""
        genre = ""
        tagInput = ""
        tags = emptyList()
        privacy = TrackPrivacy.PUBLIC
        artworkBitmap = null
        artworkFile = null
        tempArtworkBitmap = null
        hasStorefront = false
        storefrontTitle = ""
        storefrontPrice = ""
        storefrontLink = ""
        storefrontLinkTitle = ""
        storefrontDescription = ""
        storefrontBitmap = null
        storefrontImageFile = null
        storefrontImageUrl = null
        isSchedulingEnabled = false
        scheduledEpochMs = null
        isSnippetCustomized = false
        snippetStartSeconds = 0
        snippetEndSeconds = 20
        geoBlockingMode = GeoBlockingMode.EVERYWHERE
        geoBlockingRegions = ""
        labelName = ""
        releaseDate = ""
        albumTitle = ""
        releaseTitle = ""
        isrc = ""
        iswc = ""
        upcOrEan = ""
        publisher = ""
        composer = ""
        pLine = ""
        cLine = ""
        purchaseTitle = ""
        purchaseUrl = ""
        downloadable = false
        offlineListening = true
        feedable = false
        embeddable = true
        apiStreamable = true
        commentable = true
        revealComments = true
        revealStats = true
        containsMusic = true
        explicitContent = false
        license = TrackLicense.ALL_RIGHTS_RESERVED
        selectedCategoryTab = 0
        loadUserProfile()
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        resetToFileSelected()
    }

    fun resetToFileSelected() {
        if (selectedFileName.isNotBlank()) {
            uploadJob?.cancel()
            _uploadState.value = UploadState.FileSelected(selectedFileName, selectedFileSizeBytes)
        } else {
            reset()
        }
    }

    companion object {
        fun isValidUrl(url: String): Boolean {
            if (url.isBlank()) return false
            val candidate = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                "https://$url"
            } else url
            return try {
                val uri = URI(candidate)
                val host = uri.host
                !host.isNullOrBlank() && host.contains(".") && !host.startsWith(".") && !host.endsWith(".")
            } catch (e: Exception) {
                false
            }
        }

        fun normalizeUrl(url: String): String {
            val trimmed = url.trim()
            return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                "https://$trimmed"
            } else trimmed
        }
    }
}
