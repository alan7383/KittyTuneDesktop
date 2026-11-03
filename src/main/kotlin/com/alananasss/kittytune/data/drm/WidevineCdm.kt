package com.alananasss.kittytune.data.drm

/**
 * The seam a Widevine Content Decryption Module plugs into.
 *
 * ## Why this is an interface with nothing behind it
 *
 * On Android, KittyTune plays SoundCloud's `ctr-encrypted-hls` by handing the license to
 * `MediaDrm` (see `SoundCloudDrmCallback`): the platform owns a licensed Widevine CDM, builds the
 * license request, and hands back content keys. The JVM has no equivalent — there is no `MediaDrm`,
 * no Widevine CDM, and none ships with this app. The desktop build therefore *plugs into* a CDM
 * rather than containing one.
 *
 * Everything either side of the CDM is implemented and tested here: the playlist key parsing, the
 * license POST to SoundCloud, the key selection by KID, the CENC sample decryption and the fragment
 * rewrite. Only the license exchange — the part that requires a device certificate — is left to an
 * implementation of this interface.
 *
 * Implementations are registered once at startup through [WidevineCdmRegistry.install]. When none is
 * installed, DRM tracks fail with [DrmUnavailableException] and a message naming the missing piece,
 * instead of a decrypt error hundreds of lines into the player.
 */
interface WidevineCdm {

    /** For logs and error messages, e.g. `"Android MediaDrm"`. */
    val name: String

    /**
     * Opens a license session for the content described by [pssh].
     *
     * @param pssh the `pssh` box taken verbatim from the HLS `#EXT-X-KEY` `data:` URI — the same bytes
     *   the EME stack would pass to `generateRequest`.
     */
    fun openSession(pssh: ByteArray): WidevineLicenseSession
}

/** One license exchange. Closing it releases whatever key material the CDM is holding. */
interface WidevineLicenseSession : AutoCloseable {

    /** The signed license request to POST to the license server. */
    fun licenseRequest(): ByteArray

    /** Parses the server's answer into the keys it grants. */
    fun provideLicense(license: ByteArray): List<ContentKey>

    /**
     * Decrypts one CENC sample.
     *
     * The key stays inside the CDM: implementations may decrypt it themselves (a native CDM) or, for
     * a key-based CDM, apply AES-CTR locally. [subsamples] are `(clearBytes, encryptedBytes)` pairs
     * when the sample is only partly encrypted, empty when the whole sample is.
     */
    fun decryptSample(data: ByteArray, iv: ByteArray, subsamples: List<Pair<Int, Int>>): ByteArray

    override fun close()
}

/** Widevine `License.KeyContainer.KeyType`, as far as a decryptor cares. */
enum class ContentKeyType {
    SIGNING,
    CONTENT,
    KEY_CONTROL,
    OPERATOR_SESSION,
    ENTITLEMENT,
    OEM_CONTENT,
    UNKNOWN,
}

/**
 * A content key granted by a license.
 *
 * @param keyId the KID the key belongs to, normally the `default_KID` from the `tenc` box.
 * @param key the 16-byte AES key.
 */
data class ContentKey(
    val keyId: ByteArray,
    val key: ByteArray,
    val type: ContentKeyType = ContentKeyType.CONTENT,
) {
    val keyIdHex: String get() = IsoBmff.hex(keyId)

    override fun equals(other: Any?): Boolean =
        other is ContentKey && keyId.contentEquals(other.keyId) && key.contentEquals(other.key) && type == other.type

    override fun hashCode(): Int = (keyId.contentHashCode() * 31 + key.contentHashCode()) * 31 + type.hashCode()
}

/** Thrown when a DRM track is reached but no CDM is installed. */
class DrmUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when the license server refuses, or grants no usable key. */
class DrmLicenseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Holds the CDM the app was given, if any.
 *
 * Deliberately tiny and synchronous: installing happens once, before any DRM track is resolved, and
 * reads happen on the segment-decoding thread.
 */
object WidevineCdmRegistry {

    @Volatile
    private var cdm: WidevineCdm? = null

    /** Registers [implementation], or clears the registration when null. */
    fun install(implementation: WidevineCdm?) {
        cdm = implementation
    }

    /** The registered CDM, or null when the app is running without one. */
    fun current(): WidevineCdm? = cdm

    /** True when a CDM is installed, so the UI can say why a protected track will not play. */
    val isAvailable: Boolean get() = cdm != null

    /**
     * The registered CDM, or [DrmUnavailableException] explaining what is missing.
     *
     * The message is user-visible: it is what a protected track reports on a build without a CDM.
     */
    fun require(): WidevineCdm = cdm ?: throw DrmUnavailableException(
        "This build has no Widevine CDM, so SoundCloud's protected (ctr-encrypted-hls) tracks " +
            "cannot be decrypted. Everything else plays normally."
    )
}
