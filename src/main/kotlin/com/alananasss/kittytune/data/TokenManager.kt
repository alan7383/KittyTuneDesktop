package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop port of the Android TokenManager.
 * SharedPreferences("soundtune_auth") -> %APPDATA%\KittyTune\soundtune_auth.json.
 * Same keys, same refresh/expiry semantics.
 */
object TokenManager {

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    private const val KEY_TOKEN_SCOPE = "token_scope"
    private const val KEY_LAST_VALIDATED_AT = "last_validated_at"
    private const val KEY_IS_GUEST_MODE = "is_guest_mode"

    private const val DEFAULT_TOKEN_VALIDITY_MS = 25 * 60 * 1000L
    private const val REFRESH_SKEW_MS = 5 * 60 * 1000L
    private const val SCOPE_NON_EXPIRING = "non-expiring"

    val logoutFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val file = File(AppDirs.dataDir, "soundtune_auth.json")
    private val json = Json { prettyPrint = true }
    private val lock = Any()

    private val values: ConcurrentHashMap<String, JsonElement> = ConcurrentHashMap<String, JsonElement>().apply {
        try {
            if (file.exists()) {
                putAll(json.parseToJsonElement(file.readText()).jsonObject)
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            val obj = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }
            file.writeText(json.encodeToString(JsonElement.serializer(), obj))
        } catch (_: Exception) {
        }
    }

    private fun getStringValue(key: String): String? =
        (values[key] as? JsonPrimitive)?.contentOrNull

    private fun getLongValue(key: String, def: Long): Long =
        (values[key] as? JsonPrimitive)?.longOrNull ?: def

    fun saveTokens(
        accessToken: String,
        refreshToken: String? = getRefreshToken(),
        expiresInSeconds: Long? = null,
        scope: String? = getTokenScope()
    ) {
        val cleanAccessToken = cleanToken(accessToken) ?: return
        val cleanRefreshToken = cleanToken(refreshToken)
        val cleanScope = cleanToken(scope)
        val now = System.currentTimeMillis()
        val expiresAt = resolveExpiresAt(now, expiresInSeconds, cleanScope)

        values[KEY_ACCESS_TOKEN] = JsonPrimitive(cleanAccessToken)
        if (!cleanRefreshToken.isNullOrEmpty()) {
            values[KEY_REFRESH_TOKEN] = JsonPrimitive(cleanRefreshToken)
        } else if (refreshToken != null) {
            values.remove(KEY_REFRESH_TOKEN)
        }
        if (!cleanScope.isNullOrEmpty()) {
            values[KEY_TOKEN_SCOPE] = JsonPrimitive(cleanScope)
        }
        values[KEY_TOKEN_TIMESTAMP] = JsonPrimitive(now)
        values[KEY_LAST_VALIDATED_AT] = JsonPrimitive(now)
        values[KEY_TOKEN_EXPIRES_AT] = JsonPrimitive(expiresAt)
        values[KEY_IS_GUEST_MODE] = JsonPrimitive(false)
        save()
    }

    fun markAccessTokenFresh() {
        if (hasAccessToken()) {
            val now = System.currentTimeMillis()
            val expiresAt = getLongValue(KEY_TOKEN_EXPIRES_AT, 0L)
            values[KEY_TOKEN_TIMESTAMP] = JsonPrimitive(now)
            values[KEY_LAST_VALIDATED_AT] = JsonPrimitive(now)
            values[KEY_IS_GUEST_MODE] = JsonPrimitive(false)
            if (expiresAt == 0L || expiresAt <= now) {
                values[KEY_TOKEN_EXPIRES_AT] = JsonPrimitive(now + DEFAULT_TOKEN_VALIDITY_MS)
            }
            save()
        }
    }

    fun setGuestMode(isGuest: Boolean) {
        values[KEY_IS_GUEST_MODE] = JsonPrimitive(isGuest)
        save()
    }

    fun isGuestMode(): Boolean =
        (values[KEY_IS_GUEST_MODE] as? JsonPrimitive)?.booleanOrNull ?: false

    fun getAccessToken(): String? = cleanToken(getStringValue(KEY_ACCESS_TOKEN))

    fun getRefreshToken(): String? = cleanToken(getStringValue(KEY_REFRESH_TOKEN))

    fun getTokenScope(): String? = cleanToken(getStringValue(KEY_TOKEN_SCOPE))

    fun getAccessTokenExpiresAt(): Long = getLongValue(KEY_TOKEN_EXPIRES_AT, 0L)

    fun getLastValidatedAt(): Long = getLongValue(KEY_LAST_VALIDATED_AT, 0L)

    fun hasAccessToken(): Boolean = !getAccessToken().isNullOrEmpty()

    fun shouldRefreshAccessToken(bufferMs: Long = REFRESH_SKEW_MS): Boolean {
        if (!hasAccessToken()) return true
        if (getTokenScope() == SCOPE_NON_EXPIRING) return false

        val now = System.currentTimeMillis()
        val expiresAt = getAccessTokenExpiresAt()
        if (expiresAt > 0L) {
            return now + bufferMs >= expiresAt
        }

        val timestamp = getLongValue(KEY_TOKEN_TIMESTAMP, 0L)
        val effectiveValidity = (DEFAULT_TOKEN_VALIDITY_MS - bufferMs).coerceAtLeast(0L)
        return timestamp == 0L || (now - timestamp) > effectiveValidity
    }

    fun isTokenExpired(): Boolean = shouldRefreshAccessToken(bufferMs = 0L)

    fun clearTokens() {
        listOf(
            KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_TOKEN_TIMESTAMP,
            KEY_TOKEN_EXPIRES_AT, KEY_TOKEN_SCOPE, KEY_LAST_VALIDATED_AT,
        ).forEach { values.remove(it) }
        save()
    }

    fun logout() {
        clearTokens()
        values[KEY_IS_GUEST_MODE] = JsonPrimitive(false)
        save()
        com.alananasss.kittytune.data.network.CookieStore.clear()
        logoutFlow.tryEmit(Unit)
    }

    private fun resolveExpiresAt(now: Long, expiresInSeconds: Long?, scope: String?): Long {
        if (scope == SCOPE_NON_EXPIRING) return Long.MAX_VALUE

        val explicitExpiry = expiresInSeconds
            ?.takeIf { it > 0L }
            ?.let { seconds ->
                val safeSeconds = seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000L)
                now + safeSeconds * 1000L
            }

        if (explicitExpiry != null) return explicitExpiry

        val existingExpiry = getLongValue(KEY_TOKEN_EXPIRES_AT, 0L)
        if (existingExpiry > now) return existingExpiry

        return now + DEFAULT_TOKEN_VALIDITY_MS
    }

    private fun cleanToken(value: String?): String? = value
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}
