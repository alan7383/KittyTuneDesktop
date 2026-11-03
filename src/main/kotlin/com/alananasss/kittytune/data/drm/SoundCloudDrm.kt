package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.utils.Logger

/**
 * Turns a protected HLS playlist into playable audio, through a Widevine CDM.
 *
 * The full path for one SoundCloud protected track:
 *
 *  1. [StreamResolver][com.alananasss.kittytune.data.StreamResolver] resolves a `ctr-encrypted-hls`
 *     transcoding and gets a playlist URL plus `licenseAuthToken`.
 *  2. The playlist's Widevine `#EXT-X-KEY` becomes an [HlsEncryptionInfo] carrying the `pssh` box.
 *  3. [session] opens a CDM session, POSTs the CDM's license request to
 *     [SoundCloudWidevineLicense], and hands the license back. The CDM now holds the content key.
 *  4. [decryptSegment] walks the fragment, asks the CDM to decrypt each sample, and strips the
 *     encryption boxes so the demuxer below sees an ordinary `mp4a` fragment.
 *
 * No key ever reaches this code: on a desktop the CDM is the only component that can hold one, which
 * is also true on Android, where `MediaDrm` plays that role.
 */
object SoundCloudDrm {

    private const val TAG = "SoundCloudDrm"

    /** The CDM bridge holds a single session at a time, so only the current track's is kept. */
    private var currentKey: String? = null
    private var currentSession: WidevineLicenseSession? = null

    /**
     * The download side's own CDM instance and session.
     *
     * Downloads run in the background, often while something is playing, and opening a session resets
     * the instance it is opened on. Sharing playback's instance would drop the key of the track
     * currently playing as soon as a download started, so downloads get a second instance.
     */
    private var downloadCdm: WidevineCdm? = null
    private var downloadKey: String? = null
    private var downloadSession: WidevineLicenseSession? = null

    /**
     * True when protected tracks can actually be played.
     *
     * Loading the CDM is attempted here, once and lazily: a build (or a machine) without one keeps
     * the previous behaviour instead of failing at the first protected track.
     */
    val isAvailable: Boolean
        get() = NativeWidevineCdm.installIfAvailable() && WidevineCdmRegistry.isAvailable

    /** The CDM binary in use, for diagnostics. */
    val cdmPath: String? get() = NativeWidevineCdm.currentCdmPath

    /**
     * Returns a licensed session for [encryption], opening one when needed.
     *
     * @param licenseToken the `licenseAuthToken` from the stream resolution.
     * @throws DrmUnavailableException when no CDM is installed.
     * @throws DrmLicenseException when the playlist offers no Widevine key, or the license is refused.
     */
    @Synchronized
    fun session(encryption: HlsEncryptionInfo, licenseToken: String): WidevineLicenseSession {
        val pssh = requireWidevinePssh(encryption, licenseToken)

        // One track plays at a time and the CDM holds one session: keying on the playlist KID plus the
        // token means a seek or a re-resolve inside the token's lifetime reuses the session instead of
        // paying for a second license exchange.
        val key = (encryption.keyIdHex ?: IsoBmff.hex(pssh)) + ":" + licenseToken.hashCode()
        currentSession?.let { if (currentKey == key) return it }

        runCatching { currentSession?.close() }
        currentSession = null
        currentKey = null

        val cdm = WidevineCdmRegistry.require()
        val session = openLicensedSession(cdm, pssh, licenseToken, encryption.keyIdHex)
        currentSession = session
        currentKey = key
        Logger.d(TAG, "session ready for kid=${encryption.keyIdHex}")
        return session
    }

    /**
     * Opens a session for a download, on the download side's own CDM instance.
     *
     * Same license exchange as [session], but it can never reset the instance playback is using.
     *
     * @throws DrmUnavailableException when the second instance cannot be created.
     */
    @Synchronized
    fun sessionForDownload(encryption: HlsEncryptionInfo, licenseToken: String): WidevineLicenseSession {
        val pssh = requireWidevinePssh(encryption, licenseToken)
        val key = (encryption.keyIdHex ?: IsoBmff.hex(pssh)) + ":" + licenseToken.hashCode()
        downloadSession?.let { if (downloadKey == key) return it }

        runCatching { downloadSession?.close() }
        downloadSession = null
        downloadKey = null

        val cdm = downloadCdm
            ?: NativeWidevineCdm.secondaryCdm()?.also {
                downloadCdm = it
                Logger.d(TAG, "download CDM ready: ${it.name}")
            }
            ?: throw DrmUnavailableException(
                "no second Widevine CDM instance is available for downloads: " +
                    (NativeWidevineCdm.secondaryFailureReason ?: "unknown reason")
            )
        val session = openLicensedSession(cdm, pssh, licenseToken, encryption.keyIdHex)
        downloadSession = session
        downloadKey = key
        return session
    }

    /** Drops the download session; the instance is kept for the next download. */
    @Synchronized
    fun releaseDownloadSession() {
        runCatching { downloadSession?.close() }
        downloadSession = null
        downloadKey = null
    }

    /** Validates that [encryption] can be licensed with [licenseToken], returning its `pssh` box. */
    private fun requireWidevinePssh(encryption: HlsEncryptionInfo, licenseToken: String): ByteArray {
        if (!encryption.isWidevine) {
            throw DrmLicenseException(
                "playlist offers no Widevine key (KEYFORMAT=${encryption.keyFormat}); " +
                    "SoundCloud's cbc-encrypted-hls is FairPlay and cannot be used here"
            )
        }
        val pssh = encryption.pssh
            ?: throw DrmLicenseException("playlist key carries no PSSH payload")
        if (licenseToken.isBlank()) {
            throw DrmLicenseException("cannot ask for a license without a license token")
        }
        return pssh
    }

    /** Asks [cdm] for a license request, posts it to SoundCloud, and hands the answer back. */
    private fun openLicensedSession(
        cdm: WidevineCdm,
        pssh: ByteArray,
        licenseToken: String,
        keyIdHex: String?,
    ): WidevineLicenseSession {
        Logger.d(TAG, "opening a license session with ${cdm.name} (kid=$keyIdHex)")
        val session = cdm.openSession(pssh)
        try {
            val request = session.licenseRequest()
            val license = SoundCloudWidevineLicense.request(request, licenseToken)
            session.provideLicense(license)
        } catch (e: Throwable) {
            runCatching { session.close() }
            throw e
        }
        return session
    }

    /**
     * Decrypts one fragment of a protected track.
     *
     * Returns [segment] untouched when [track] is null, and refuses unsupported schemes loudly rather
     * than feeding ciphertext to the decoder.
     */
    fun decryptSegment(
        segment: ByteArray,
        track: CencDecrypter.TrackEncryption?,
        session: WidevineLicenseSession,
    ): ByteArray {
        if (track == null) return segment
        // The CDM picks the key by KID; tell it which one this fragment declares.
        (session as? NativeWidevineSession)?.useKeyId(track.keyId)
        return CencDecrypter.decryptSegment(segment, track) { buffer, offset, size, iv, subsamples ->
            val sample = buffer.copyOfRange(offset, offset + size)
            val plain = session.decryptSample(sample, iv, subsamples ?: emptyList())
            if (plain.size != size) {
                throw DrmLicenseException("CDM returned ${plain.size} bytes for a $size-byte sample")
            }
            System.arraycopy(plain, 0, buffer, offset, size)
        }
    }

    /** Rewrites an init segment so a protected track reads as clear. */
    fun clearInit(init: ByteArray): ByteArray = CencDecrypter.clearInitSegment(init)

    /** Drops the open session, e.g. when playback stops or the session ends. */
    @Synchronized
    fun clearCache() {
        runCatching { currentSession?.close() }
        currentSession = null
        currentKey = null
    }
}
