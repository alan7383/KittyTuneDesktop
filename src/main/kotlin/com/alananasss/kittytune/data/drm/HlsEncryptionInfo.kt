package com.alananasss.kittytune.data.drm

import java.util.Base64

/**
 * The encryption an HLS playlist declares through `#EXT-X-KEY`.
 *
 * SoundCloud lists several keys for the same fragment set: a Widevine one
 * (`KEYFORMAT="urn:uuid:edef8ba9-…"`, carrying a `pssh` box as a `data:` URI) and a PlayReady one
 * (`KEYFORMAT="com.microsoft.playready"`). Only the Widevine line can be turned into a key here, so
 * [parse] selects it explicitly rather than taking whichever line comes first.
 */
data class HlsEncryptionInfo(
    /** `METHOD`, e.g. `SAMPLE-AES` (Widevine) or `SAMPLE-AES-CTR` (PlayReady on SoundCloud). */
    val method: String,
    /** `KEYFORMAT`, e.g. `urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed`. */
    val keyFormat: String?,
    /** `KEYID` as lowercase hex without `0x`, when the playlist states it. */
    val keyIdHex: String?,
    /** `IV` as lowercase hex without `0x`, when the playlist states it. */
    val ivHex: String?,
    /** `URI` verbatim, which may be a `data:` URI carrying the PSSH. */
    val keyUri: String?,
    /** The `pssh` box decoded out of a `data:` URI, when there is one. */
    val pssh: ByteArray?,
) {
    /** True when this key is the Widevine one, i.e. the one a Widevine license can answer. */
    val isWidevine: Boolean
        get() = keyFormat?.equals(CencDecrypter.WIDEVINE_KEY_FORMAT, ignoreCase = true) == true

    /** `SAMPLE-AES` marks sample-level encryption (CENC), unlike plain `AES-128` whole-segment keys. */
    val isSampleAes: Boolean
        get() = method.uppercase().startsWith("SAMPLE-AES")

    override fun equals(other: Any?): Boolean =
        other is HlsEncryptionInfo &&
            method == other.method &&
            keyFormat == other.keyFormat &&
            keyIdHex == other.keyIdHex &&
            ivHex == other.ivHex &&
            keyUri == other.keyUri

    override fun hashCode(): Int =
        ((((method.hashCode() * 31 + (keyFormat?.hashCode() ?: 0)) * 31) +
            (keyIdHex?.hashCode() ?: 0)) * 31 + (ivHex?.hashCode() ?: 0)) * 31 + (keyUri?.hashCode() ?: 0)

    companion object {

        /**
         * Picks the encryption a playlist declares, preferring the Widevine key line.
         *
         * Returns null for a clear playlist, which is the overwhelmingly common case: SoundCloud only
         * protects `ctr-encrypted-hls` / `cbc-encrypted-hls` transcodings.
         */
        fun parse(playlist: String): HlsEncryptionInfo? {
            val parsed = ArrayList<HlsEncryptionInfo>(2)
            for (raw in playlist.lineSequence()) {
                val line = raw.trim()
                if (!line.startsWith("#EXT-X-KEY:", ignoreCase = true)) continue
                val attributes = attributes(line.substringAfter(':'))
                val method = attributes["METHOD"] ?: continue
                if (method.equals("NONE", ignoreCase = true)) continue

                val uri = attributes["URI"]
                parsed.add(
                    HlsEncryptionInfo(
                        method = method,
                        keyFormat = attributes["KEYFORMAT"],
                        keyIdHex = attributes["KEYID"]?.let { normalizeHex(it) },
                        ivHex = attributes["IV"]?.let { normalizeHex(it) },
                        keyUri = uri,
                        pssh = uri?.let { decodeDataUri(it) },
                    )
                )
            }
            if (parsed.isEmpty()) return null
            // SoundCloud emits Widevine and PlayReady side by side; only Widevine is usable here.
            return parsed.firstOrNull { it.isWidevine }
                ?: parsed.firstOrNull { it.keyFormat?.startsWith("com.microsoft", ignoreCase = true) != true }
        }

        /** Splits `KEY=VALUE,KEY="VALUE,WITH,COMMAS"` on the commas that are not inside quotes. */
        internal fun attributes(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            val current = StringBuilder()
            var quoted = false
            val parts = ArrayList<String>(6)
            for (ch in text) {
                when {
                    ch == '"' -> { quoted = !quoted; current.append(ch) }
                    ch == ',' && !quoted -> { parts.add(current.toString()); current.clear() }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) parts.add(current.toString())

            for (part in parts) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val key = part.substring(0, eq).trim().uppercase()
                var value = part.substring(eq + 1).trim()
                if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
                    value = value.substring(1, value.length - 1)
                }
                out[key] = value
            }
            return out
        }

        /** Hex with any `0x`/`0X` prefix removed and lowercased, so keys compare byte-for-byte. */
        internal fun normalizeHex(value: String): String =
            value.trim().removePrefix("0x").removePrefix("0X").lowercase()

        /** Decodes the base64 payload of a `data:` URI, or null when the URI is a plain URL. */
        internal fun decodeDataUri(uri: String): ByteArray? {
            if (!uri.startsWith("data:", ignoreCase = true)) return null
            val comma = uri.indexOf(',')
            if (comma < 0) return null
            val meta = uri.substring(5, comma)
            if (!meta.contains("base64", ignoreCase = true)) return null
            return try {
                Base64.getDecoder().decode(uri.substring(comma + 1).trim())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
