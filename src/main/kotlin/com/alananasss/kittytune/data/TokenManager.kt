package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
    private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
    private const val KEY_ACCOUNTS = "accounts"

    /** Everything that belongs to one session; what is copied when an account is put away or brought back. */
    private val SESSION_KEYS = listOf(
        KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_TOKEN_TIMESTAMP,
        KEY_TOKEN_EXPIRES_AT, KEY_TOKEN_SCOPE, KEY_LAST_VALIDATED_AT,
    )

    /** A SoundCloud account that has signed in on this computer and can be switched back to. */
    data class SavedAccount(val id: Long, val username: String, val avatarUrl: String?)

    private const val DEFAULT_TOKEN_VALIDITY_MS = 25 * 60 * 1000L
    private const val REFRESH_SKEW_MS = 5 * 60 * 1000L

    val logoutFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * A stored scope for the logout DB cleanup. The previous code created an inline
     * `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ... }` with no stored reference —
     * the job had no parent and couldn't be cancelled or awaited, so a logout immediately followed
     * by process exit could lose the DB cleanup mid-flight (audit issue).
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // The lock this object always declared but never used: two concurrent save() calls wrote
        // the same file at once, and a crash mid-write left a half-JSON token store behind — the
        // corrupted-then-logged-out sessions the audit flagged. Serialized, and written through a
        // temp file + atomic replace so the token file is either the old one or the new one, never
        // a torn mix of both.
        synchronized(lock) {
            try {
                val obj = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }
                val tmp = File(file.parentFile, "soundtune_auth.json.tmp")
                tmp.writeText(json.encodeToString(JsonElement.serializer(), obj))
                com.alananasss.kittytune.core.AtomicFiles.replace(tmp, file)
            } catch (_: Exception) {
            }
        }
    }

    private val _accounts = kotlinx.coroutines.flow.MutableStateFlow(readAccounts())

    /** The accounts kept on this computer, in the order they were first signed in. */
    val accounts: kotlinx.coroutines.flow.StateFlow<List<SavedAccount>> get() = _accounts

    private fun accountEntries(): List<JsonObject> =
        (values[KEY_ACCOUNTS] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun JsonObject.accountId(): Long = (this["id"] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun readAccounts(): List<SavedAccount> = accountEntries().map { entry ->
        SavedAccount(
            entry.accountId(),
            (entry["username"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            (entry["avatar_url"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun writeAccounts(entries: List<JsonObject>) {
        if (entries.isEmpty()) values.remove(KEY_ACCOUNTS) else values[KEY_ACCOUNTS] = JsonArray(entries)
        _accounts.value = readAccounts()
    }

    /** The saved account the current session belongs to, or 0 for a guest or a session not identified yet. */
    fun activeAccountId(): Long = getLongValue(KEY_ACTIVE_ACCOUNT_ID, 0L)

    /**
     * Files the current session under the account it turned out to belong to.
     *
     * A login only yields tokens; whose they are is known once the profile has been fetched, which is when this
     * is called. From then on the saved copy follows every token refresh (see [syncActiveAccount]).
     */
    fun rememberActiveAccount(id: Long, username: String, avatarUrl: String?) {
        if (id == 0L || !hasAccessToken()) return
        synchronized(lock) {
            val entry = buildJsonObject {
                put("id", JsonPrimitive(id))
                put("username", JsonPrimitive(username))
                if (!avatarUrl.isNullOrBlank()) put("avatar_url", JsonPrimitive(avatarUrl))
                SESSION_KEYS.forEach { key -> values[key]?.let { put(key, it) } }
            }
            val entries = accountEntries()
            val unchanged = activeAccountId() == id && entries.any { it == entry }
            if (unchanged) return
            values[KEY_ACTIVE_ACCOUNT_ID] = JsonPrimitive(id)
            writeAccounts(if (entries.any { it.accountId() == id }) entries.map { if (it.accountId() == id) entry else it } else entries + entry)
            save()
        }
    }

    /**
     * Copies the live tokens over the active account's saved ones.
     *
     * SoundCloud rotates the refresh token each time it is used, so a saved copy that did not follow would be
     * dead by the time anyone switched back to it.
     */
    private fun syncActiveAccount() {
        val active = activeAccountId()
        if (active == 0L) return
        val entries = accountEntries()
        if (entries.none { it.accountId() == active }) return
        writeAccounts(entries.map { entry ->
            if (entry.accountId() != active) entry else buildJsonObject {
                entry.filterKeys { it !in SESSION_KEYS }.forEach { (key, value) -> put(key, value) }
                SESSION_KEYS.forEach { key -> values[key]?.let { put(key, it) } }
            }
        })
    }

    /** Removes a saved account. Forgetting the active one leaves its session running; [logout] ends it too. */
    fun forgetAccount(id: Long) {
        synchronized(lock) {
            writeAccounts(accountEntries().filter { it.accountId() != id })
            if (activeAccountId() == id) values.remove(KEY_ACTIVE_ACCOUNT_ID)
            save()
        }
    }

    /**
     * Makes a saved account the current session. False when there is no such account.
     *
     * The account being left keeps its place in the list. Its tokens may well have expired while it sat there,
     * so a refresh is asked for straight away.
     */
    fun switchToAccount(id: Long): Boolean {
        if (accountEntries().none { it.accountId() == id }) return false
        if (activeAccountId() == id && hasAccessToken()) return true
        clearAccountData()
        if (!activateSavedSession(id)) return false
        SessionManager.start()
        SessionManager.requestSessionRefresh()
        SessionManager.onLoginCompleted()
        return true
    }

    /** The storage half of [switchToAccount]: the saved tokens become the live ones, and nothing else happens. */
    internal fun activateSavedSession(id: Long): Boolean = synchronized(lock) {
        val entry = accountEntries().firstOrNull { it.accountId() == id } ?: return false
        SESSION_KEYS.forEach { key -> values.remove(key) }
        SESSION_KEYS.forEach { key -> entry[key]?.let { values[key] = it } }
        values[KEY_IS_GUEST_MODE] = JsonPrimitive(false)
        values[KEY_ACTIVE_ACCOUNT_ID] = JsonPrimitive(id)
        save()
        true
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
        val expiresAt = resolveExpiresAt(now, expiresInSeconds, cleanAccessToken)

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
        syncActiveAccount()
        save()
    }

    /** An authorization-code login starts a new session; omitted fields belong to nobody yet. */
    fun replaceSession(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
        scope: String?
    ) {
        synchronized(lock) {
            clearTokens()
            saveTokens(accessToken, refreshToken, expiresInSeconds, scope)
        }
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

    /** False for a legacy non-expiring token: it has nothing to renew and no expiry to watch. */
    fun hasExpiringSession(): Boolean =
        OAuthTokens.jwtExpiryMillis(getAccessToken()) != null || getRefreshToken() != null

    fun shouldRefreshAccessToken(bufferMs: Long = REFRESH_SKEW_MS): Boolean {
        if (!hasAccessToken()) return true
        if (!hasExpiringSession()) return false

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

    /** Ends the live session. A saved copy of the account, if there is one, is left where it is. */
    fun clearTokens() {
        SESSION_KEYS.forEach { values.remove(it) }
        values.remove(KEY_ACTIVE_ACCOUNT_ID)
        save()
    }

    /** Signing out is for good: the account is forgotten as well, unlike switching away from it. */
    fun logout() {
        activeAccountId().takeIf { it != 0L }?.let { forgetAccount(it) }
        endSession(guest = false, notifyRouter = true)
    }

    /** Leave the signed-in account for guest mode without telling the router; the account stays saved. */
    fun continueAsGuest() = endSession(guest = true, notifyRouter = false)

    private fun endSession(guest: Boolean, notifyRouter: Boolean) {
        clearTokens()
        values[KEY_IS_GUEST_MODE] = JsonPrimitive(guest)
        save()
        clearAccountData()
        if (notifyRouter) logoutFlow.tryEmit(Unit)
    }

    /** Drops everything cached about whoever was signed in: likes, reposts, cookies, home feed, online playlists. */
    private fun clearAccountData() {
        // Stop the session keep-alive loop: it would otherwise keep hitting the token endpoint
        // every 20 minutes after logout, for a token that no longer exists (audit Phase 4).
        SessionManager.stop()
        LikeRepository.clear()
        RepostRepository.clear()
        // Whose follows the social proof was about is gone with the session.
        SocialProofRepository.clear()
        com.alananasss.kittytune.data.network.CookieStore.clear()
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        prefs.setCachedUserId(0L)
        prefs.setCachedUsername(null)
        prefs.setCachedUserUploads(emptyList())
        com.alananasss.kittytune.core.NamedPrefs("home_cache").clear()

        scope.launch {
            try {
                val dao = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
                dao.deleteNonDownloadedOnlinePlaylists()
                dao.cleanUnreferencedEmptyTracks()
                DownloadManager.notifyLibraryUpdated()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resolveExpiresAt(now: Long, expiresInSeconds: Long?, accessToken: String): Long {
        val explicitExpiry = expiresInSeconds
            ?.takeIf { it > 0L }
            ?.let { seconds ->
                val safeSeconds = seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000L)
                now + safeSeconds * 1000L
            }

        if (explicitExpiry != null) return explicitExpiry
        OAuthTokens.jwtExpiryMillis(accessToken)?.let { return it }

        val existingExpiry = getLongValue(KEY_TOKEN_EXPIRES_AT, 0L)
        if (existingExpiry > now) return existingExpiry

        return now + DEFAULT_TOKEN_VALIDITY_MS
    }

    private fun cleanToken(value: String?): String? = value
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}
