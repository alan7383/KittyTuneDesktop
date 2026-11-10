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
     *
     * The optional CDM sidecar is tried first, because it is the only path that gives the app PCM
     * samples — and therefore the only one where the equalizer, the filters and the pitch shifter
     * work on a protected track. It is off unless the user configured it; when it is absent this
     * falls straight through to the in-process CDM and then to PlayReady.
     */
    val isAvailable: Boolean
        get() = CdmSidecar.installIfConfigured() ||
            (NativeWidevineCdm.installIfAvailable() && WidevineCdmRegistry.isAvailable)

    /**
     * True when protected playback is decoded by the sidecar rather than by the PlayReady renderer.
     *
     * [com.alananasss.kittytune.audio.AudioEngine] reads this to decide whether a protected URL goes
     * to the OS renderer or through the ordinary FFmpeg + DSP chain. Cheap on purpose: it must not
     * trigger a probe while a track is being prepared.
     */
    val usesSidecar: Boolean get() = CdmSidecar.isInstalled

    /**
     * True while asking this CDM for a licence is still worth the round trip.
     *
     * [isAvailable] answers "is a CDM installed"; this answers "will the server license it". They
     * differ on Windows, where the licence server refuses the client identity of a CDM driven outside
     * a browser (measured; see `docs/SOUNDCLOUD_DRM_WINDOWS.md`). The first protected track discovers
     * it; from then on `StreamResolver` stops offering encrypted candidates, so its existing provider
     * and preview fallbacks take over instead of handing the engine a stream that dies at the first
     * fragment. Nothing is remembered across runs: a new CDM, a different platform or a changed server
     * policy is tried again from scratch.
     */
    val canLicense: Boolean
        get() = isAvailable && !SoundCloudWidevineLicense.licenceRefused

    /** The CDM in use, for diagnostics: a sidecar description, or the in-process binary's path. */
    val cdmPath: String? get() = if (CdmSidecar.isInstalled) CdmSidecar.describe else NativeWidevineCdm.currentCdmPath

    /**
     * The licence token that came back with a stream URL, for everything that reads that URL.
     *
     * The token is a property of the resolved stream, not of the player: a karaoke split, the AI
     * detector or the beat analyser all open the same `ctr-encrypted-hls` playlist through
     * [com.alananasss.kittytune.audio.HlsStreamAdapter], and without the token those features would
     * decode ciphertext on a protected track. Keyed by URL, bounded, and only ever filled by a
     * resolution that actually carried a token.
     */
    private val licences = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 64
        }
    )

    /** Remembers [token] for [url]; a blank one is ignored, never remembered as an absence. */
    fun rememberLicence(url: String, token: String?) {
        if (url.isBlank() || token.isNullOrBlank()) return
        licences[url] = token
    }

    fun licenceFor(url: String): String? = licences[url]

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
        currentSession?.let { if (currentKey == key && !it.isExpired) return it }

        runCatching { currentSession?.close() }
        currentSession = null
        currentKey = null

        val cdm = requireCdm()
        val session = openLicensedSession(cdm, pssh, licenseToken, encryption.keyIdHex)
        currentSession = session
        currentKey = key
        Logger.d(TAG, "session ready for kid=${encryption.keyIdHex}")
        return session
    }

    /**
     * The CDM to use, after making sure the configured one is actually registered.
     *
     * [WidevineCdmRegistry] only holds a CDM once something installed it, and installing the sidecar
     * happens lazily. A caller that asks for a session before any track was resolved — the settings'
     * own test, exactly — used to get the in-process CDM instead, the one SoundCloud refuses with a
     * `403`. Asking here costs nothing when it is already registered and fixes the order.
     */
    private fun requireCdm(): WidevineCdm {
        CdmSidecar.installIfConfigured()
        return WidevineCdmRegistry.require()
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
        downloadSession?.let { if (downloadKey == key && !it.isExpired) return it }

        runCatching { downloadSession?.close() }
        downloadSession = null
        downloadKey = null

        // With the sidecar, the second instance is a second daemon: the in-process CDM is the one
        // SoundCloud refuses on Windows. The sidecar has to be registered before that choice is made,
        // which is exactly what a caller that never resolved a track would otherwise skip.
        CdmSidecar.installIfConfigured()
        val cdm = downloadCdm
            ?: (if (CdmSidecar.isInstalled) CdmSidecar.secondaryCdm() else NativeWidevineCdm.secondaryCdm())?.also {
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

    /**
     * Closes [session] when it is playback's current one: its stream no longer needs decrypting.
     *
     * Closing is what lets the WSL sidecar stop its daemon a few seconds later instead of keeping the
     * Linux VM up. Any other session (a download's, an older one) is left to its owner.
     */
    @Synchronized
    fun releaseSession(session: WidevineLicenseSession) {
        if (currentSession !== session) return
        runCatching { session.close() }
        currentSession = null
        currentKey = null
    }

    /**
     * Forgets every session and the download instance, because the CDM behind them changed (the
     * method was switched, or the sidecar was set up again). The next track licenses afresh.
     */
    @Synchronized
    fun forgetSessions() {
        runCatching { currentSession?.close() }
        currentSession = null
        currentKey = null
        runCatching { downloadSession?.close() }
        downloadSession = null
        downloadKey = null
        downloadCdm = null
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
    /**
     * Asks [cdm] for a license request, posts it to SoundCloud, and hands the answer back.
     *
     * One retry when the CDM process dies on the way: before a key exists there is nothing to keep,
     * and a fresh session starts a fresh daemon. Any other failure — a refusal, a live CDM rejecting
     * the PSSH — is a real answer and propagates as before.
     */
    private fun openLicensedSession(
        cdm: WidevineCdm,
        pssh: ByteArray,
        licenseToken: String,
        keyIdHex: String?,
    ): WidevineLicenseSession {
        Logger.d(TAG, "opening a license session with ${cdm.name} (kid=$keyIdHex)")
        var attempt = 0
        while (true) {
            val session = cdm.openSession(pssh)
            try {
                val request = session.licenseRequest()
                val license = SoundCloudWidevineLicense.request(request, licenseToken)
                session.provideLicense(license)
                return session
            } catch (e: Throwable) {
                runCatching { session.close() }
                if (attempt > 0 || !looksLikeDeadCdm(e)) throw e
                attempt++
                Logger.w(TAG, "the CDM stopped before the licence, asking a fresh one (${e.message})")
            }
        }
    }

    /** The CDM process went away, as opposed to it refusing the request. */
    private fun looksLikeDeadCdm(e: Throwable): Boolean {
        val message = e.message ?: return false
        return message.contains("closed the pipe") ||
            message.contains("is not running") ||
            message.contains("does not answer") ||
            message.contains("stopped listening")
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
        (session as? KeyIdScopedSession)?.useKeyId(track.keyId)
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
