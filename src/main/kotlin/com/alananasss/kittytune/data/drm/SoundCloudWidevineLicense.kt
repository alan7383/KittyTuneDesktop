package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.utils.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The Widevine license endpoint SoundCloud uses for `ctr-encrypted-hls`.
 *
 * Reverse-engineered from SoundCloud-Desktop 8.7.0 and identical to what the Android build talks to
 * (`SoundCloudDrmCallback`): a raw `POST` of the CDM's signed license request, with the short-lived
 * JWT that came back alongside the stream URL as `license_token`.
 *
 * ```
 * POST https://license.media-streaming.soundcloud.cloud/playback/widevine?license_token=<JWT>
 * Content-Type: application/octet-stream
 * <signed license request>     →     <license>
 * ```
 *
 * A second endpoint serves offline/persistable licenses; it returns a license that survives the
 * session, which is what a download would ask for.
 */
object SoundCloudWidevineLicense {

    private const val HOST = "https://license.media-streaming.soundcloud.cloud"
    const val PLAYBACK_URL = "$HOST/playback/widevine?license_token="
    const val PERSISTABLE_URL = "$HOST/persistable/widevine?license_token="

    /**
     * Where the exchange actually goes. Overridable so a test can point it at a local server and
     * exercise the whole path — playlist key, CDM, POST, key selection — without the network.
     */
    @Volatile
    internal var playbackUrl: String = PLAYBACK_URL

    @Volatile
    internal var persistableUrl: String = PERSISTABLE_URL

    /**
     * True once the server has answered `403` to a licence request from this machine.
     *
     * A `403` is not transient: the server judges the *client identity* inside the request, and a CDM
     * driven outside a browser is refused on Windows (measured; see `docs/SOUNDCLOUD_DRM_WINDOWS.md`).
     * Re-asking for every protected track only costs a round trip and fails the fragment afterwards,
     * so the refusal is remembered for the session and `StreamResolver` stops offering encrypted
     * candidates — which lets its existing provider / preview fallback take over.
     */
    @Volatile
    var licenceRefused: Boolean = false
        private set

    /**
     * Forgets a previous refusal. Production keeps the answer for the whole run, except when a
     * different CDM takes over (the WSL sidecar replacing the refused in-process one).
     */
    internal fun resetLicenceRefusal() {
        licenceRefused = false
    }

    private val OCTET_STREAM = "application/octet-stream".toMediaType()

    internal var client: okhttp3.OkHttpClient.Builder = ProxyManager.getOkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)

    /**
     * Exchanges [licenseRequest] for a license.
     *
     * @param licenseToken the `licenseAuthToken` returned with the stream URL.
     * @param offline ask for a persistable license (downloads) instead of a playback one.
     * @throws DrmLicenseException when the server refuses or answers with nothing.
     */
    fun request(licenseRequest: ByteArray, licenseToken: String, offline: Boolean = false): ByteArray {
        if (licenseToken.isBlank()) {
            throw DrmLicenseException("cannot ask for a license without a license token")
        }
        val url = (if (offline) persistableUrl else playbackUrl) + licenseToken
        val http = Request.Builder()
            .url(url)
            .post(licenseRequest.toRequestBody(OCTET_STREAM))
            .build()

        Logger.d(TAG, "POST license request (${licenseRequest.size} B, offline=$offline)")
        val call = client.build().newCall(http)
        try {
            call.execute().use { response ->
                val body = response.body?.bytes()
                if (!response.isSuccessful) {
                    // 403 is the server judging the client identity, not a bad token: remember it so
                    // the resolver stops routing protected tracks into a licence exchange that the
                    // server will refuse every time on this machine.
                    if (response.code == 403) licenceRefused = true
                    val detail = body
                        ?.let { String(it, 0, minOf(it.size, 160), Charsets.UTF_8) }
                        ?.trim()
                    throw DrmLicenseException(
                        "license server answered HTTP ${response.code}" +
                            (if (detail.isNullOrEmpty()) "" else ": $detail") +
                            refusalHint(response.code)
                    )
                }
                if (body == null || body.isEmpty()) {
                    throw DrmLicenseException("license server returned an empty license")
                }
                Logger.d(TAG, "license granted (${body.size} B)")
                return body
            }
        } catch (e: DrmLicenseException) {
            throw e
        } catch (e: IOException) {
            throw DrmLicenseException("license request failed: ${e.message}", e)
        }
    }

    /**
     * Why a `403` is worth spelling out.
     *
     * The server judges the client identity carried in the request body. The Windows in-process CDM
     * is refused, while the Linux module loaded by the native host is accepted — measured. So a `403`
     * with the module installed does not mean "impossible on Windows": it means something other than
     * the module answered, or the request went through a proxy SoundCloud refuses.
     */
    private fun refusalHint(code: Int): String {
        if (code != 403) return ""
        val os = System.getProperty("os.name").orEmpty()
        if (!os.startsWith("Windows", ignoreCase = true)) return ""
        return " — the licence server refused that request. With the native module installed this " +
            "means the CDM that answered was not it (the in-process one is refused), or the request " +
            "went through a proxy SoundCloud refuses; see docs/SOUNDCLOUD_CDM_SIDECAR.md"
    }

    private const val TAG = "SoundCloudWidevineLicense"
}
