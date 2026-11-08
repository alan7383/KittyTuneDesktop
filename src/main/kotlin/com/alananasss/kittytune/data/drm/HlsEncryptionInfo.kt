package com.alananasss.kittytune.data.drm

import java.util.Base64

/**
 * The PlayReady half of a SoundCloud playlist key line.
 *
 * The same `#EXT-X-KEY` set also carries a PlayReady object whose `LA_URL` is a working licence
 * endpoint on Windows (`/playback/playready`). Widevine cannot be licensed with the Windows CDM
 * outside a browser, but the OS PlayReady CDM can, so this is the Windows-native route. It is kept
 * separate from [HlsEncryptionInfo] because the two DRM systems have different licence exchanges.
 */
data class PlayReadyInfo(
    /** `LA_URL` from the WRMHEADER, i.e. the endpoint a licence challenge goes to. */
    val licenseUrl: String?,
    /** `KID` as lowercase hex without `0x`, when the header states it. */
    val keyIdHex: String?,
    /** `DS_ID` from the WRMHEADER, when present. */
    val domainServiceId: String?,
    /** `ALGID`, normally `AESCTR`. */
    val algorithm: String?,
    /** The `CUSTOMATTRIBUTES` SoundCloud adds (`CID`, `DRMTYPE`). */
    val customAttributes: Map<String, String>,
) {
    /** The licence URL with SoundCloud's short-lived token appended, as the OS CDM expects. */
    fun licenseUrlWithToken(token: String): String? =
        licenseUrl?.let { if (it.contains('?')) "$it&license_token=$token" else "$it?license_token=$token" }
}

/**
 * The encryption an HLS playlist declares through `#EXT-X-KEY`.
 *
 * SoundCloud lists several keys for the same fragment set: a Widevine one
 * (`KEYFORMAT="urn:uuid:edef8ba9-…"`, carrying a `pssh` box as a `data:` URI) and a PlayReady one
 * (`KEYFORMAT="com.microsoft.playready"`). [parse] selects the Widevine line for the existing CDM
 * path; [parsePlayReady] exposes the PlayReady licence URL, which is the only route that works with
 * the PlayReady CDM Windows provides (see `docs/SOUNDCLOUD_DRM_WINDOWS.md`).
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
         * Reads the PlayReady key line SoundCloud emits next to the Widevine one.
         *
         * The `data:` URI carries a PlayReady Object: a little-endian length and record count,
         * then records whose type 1 is the UTF-16LE WRMHEADER XML. The header names the licence
         * service, the key id and the domain service id, which is all a Windows PlayReady CDM needs.
         */
        fun parsePlayReady(playlist: String): PlayReadyInfo? {
            for (raw in playlist.lineSequence()) {
                val line = raw.trim()
                if (!line.startsWith("#EXT-X-KEY:", ignoreCase = true)) continue
                val attributes = attributes(line.substringAfter(':'))
                if (!attributes["KEYFORMAT"].orEmpty().startsWith("com.microsoft", ignoreCase = true)) continue
                val uri = attributes["URI"] ?: continue
                val header = decodeDataUri(uri)?.let { playReadyHeader(it) } ?: continue
                return playReadyInfo(header)
            }
            return null
        }

        /** Extracts the WRMHEADER XML out of a PlayReady Object (or a `pssh` box wrapping one). */
        internal fun playReadyHeader(bytes: ByteArray): String? {
            var data = bytes
            if (data.size >= 8 && String(data, 4, 4, Charsets.US_ASCII) == "pssh") {
                val size = readU32Be(data, 0) ?: return null
                if (size > data.size || data.size < 32) return null
                val payloadSize = readU32Be(data, 28) ?: return null
                if (28 + 4 + payloadSize > data.size) return null
                data = data.copyOfRange(32, 32 + payloadSize)
            }
            if (data.size >= 2 && data[0] == '<'.code.toByte() && data[1] == 0.toByte()) {
                return String(data, Charsets.UTF_16LE)
            }
            if (data.size < 6) return null
            val objectLength = readU32Le(data, 0) ?: return null
            if (objectLength > data.size) return null
            var offset = 6
            val records = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
            repeat(records) {
                if (offset + 4 > data.size) return null
                val type = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                val length = (data[offset + 2].toInt() and 0xFF) or ((data[offset + 3].toInt() and 0xFF) shl 8)
                offset += 4
                if (offset + length > data.size) return null
                if (type == 1) return String(data, offset, length, Charsets.UTF_16LE)
                offset += length
            }
            return null
        }

        /** Reads KID/LA_URL/DS_ID/ALGID and the custom attributes out of a WRMHEADER XML string. */
        internal fun playReadyInfo(header: String): PlayReadyInfo {
            fun tag(name: String): String? =
                Regex("<$name>([^<]*)</$name>", RegexOption.IGNORE_CASE)
                    .find(header)?.groupValues?.get(1)?.trim()

            val kid = tag("KID")?.let { base64GuidHex(it) }
            val dsId = tag("DS_ID")?.let { base64Guid(it) }
            val custom = LinkedHashMap<String, String>()
            Regex("<CUSTOMATTRIBUTES[^>]*>(.*?)</CUSTOMATTRIBUTES>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(header)?.groupValues?.get(1)?.let { body ->
                    Regex("<([A-Za-z0-9_]+)>([^<]*)</\\1>").findAll(body).forEach {
                        custom[it.groupValues[1]] = it.groupValues[2].trim()
                    }
                }
            return PlayReadyInfo(
                licenseUrl = tag("LA_URL"),
                keyIdHex = kid,
                domainServiceId = dsId,
                algorithm = tag("ALGID"),
                customAttributes = custom,
            )
        }

        private fun readU32Le(bytes: ByteArray, offset: Int): Int? {
            if (offset + 4 > bytes.size) return null
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }

        private fun readU32Be(bytes: ByteArray, offset: Int): Int? {
            if (offset + 4 > bytes.size) return null
            return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }

        /** PlayReady stores GUIDs with the first three fields in little-endian order. */
        private fun base64GuidHex(value: String): String? = base64GuidParts(value)

        private fun base64Guid(value: String): String? {
            val hex = base64GuidHex(value) ?: return null
            if (hex.length != 32) return null
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20)}"
        }

        private fun base64GuidParts(value: String): String? {
            val bytes = try {
                Base64.getDecoder().decode(value.trim())
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (bytes.size != 16) return null
            val ordered = byteArrayOf(
                bytes[3], bytes[2], bytes[1], bytes[0],
                bytes[5], bytes[4], bytes[7], bytes[6],
                bytes[8], bytes[9], bytes[10], bytes[11],
                bytes[12], bytes[13], bytes[14], bytes[15],
            )
            return ordered.joinToString("") { "%02x".format(it) }
        }

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
            // SoundCloud emits Widevine and PlayReady side by side. The Widevine CDM is what the
            // current session uses; the PlayReady line is read separately by [parsePlayReady].
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
