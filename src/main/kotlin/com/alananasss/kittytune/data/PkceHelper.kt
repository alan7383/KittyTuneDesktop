package com.alananasss.kittytune.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (Proof Key for Code Exchange) helper.
 * Desktop port of the Android version — android.util.Base64 -> java.util.Base64.
 */
object PkceHelper {

    private const val VERIFIER_LENGTH = 64
    private const val VALID_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val VALID_CHAR_COUNT = 66

    fun generateVerifier(): String {
        val random = SecureRandom()
        val chars = CharArray(VERIFIER_LENGTH)
        for (i in 0 until VERIFIER_LENGTH) {
            val idx = (random.nextInt(1000)) % VALID_CHAR_COUNT
            chars[i] = VALID_CHARS[idx]
        }
        return String(chars)
    }

    fun generateChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
