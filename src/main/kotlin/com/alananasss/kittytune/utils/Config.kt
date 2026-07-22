package com.alananasss.kittytune.utils

import com.alananasss.kittytune.core.AppDirs
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 * Desktop port of the Android Config object.
 * SharedPreferences("app_config") -> %APPDATA%\KittyTune\app_config.json.
 * Embedded SoundCloud client credentials are required for auth — carried over verbatim.
 */
object Config {
    private const val KEY_CLIENT_ID = "dynamic_client_id"

    const val FALLBACK_ID = "7K3no7iJj8d02d20Z26Z26Z26Z26Z26"
    const val OFFICIAL_CLIENT_ID = "QOFuKCOeAXIph267vzqj3B1wb65cZVAQ"
    const val OFFICIAL_CLIENT_SECRET = "EhBDsGIj9EbuBbRf0QkhH9Fq9BX3yN4B"

    val OFFICIAL_CLIENT_SIGNATURE: String by lazy {
        calculateSignature(OFFICIAL_CLIENT_ID, OFFICIAL_CLIENT_SECRET)
    }

    private fun calculateSignature(clientId: String, clientSecret: String): String {
        return try {
            val concat = "$clientId:$clientSecret".toByteArray(Charsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(concat)
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        } catch (e: Exception) {
            "ztQ_RKaMCPavrjcXvMT6t0STPQ0vU2cY4YKGVeLU6Iw"
        }
    }

    const val BASE_URL = "https://api-v2.soundcloud.com/"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val configFile = File(AppDirs.dataDir, "app_config.json")
    private val authFlowFile = File(AppDirs.dataDir, "soundcloud_auth_flow.json")
    private val json = Json { prettyPrint = true }

    var CLIENT_ID: String = FALLBACK_ID
        private set

    private fun readJson(file: File): MutableMap<String, String> = try {
        if (file.exists()) {
            file.readText().let { text ->
                json.parseToJsonElement(text).jsonObject
                    .mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: "" }
                    .toMutableMap()
            }
        } else mutableMapOf()
    } catch (_: Exception) {
        mutableMapOf()
    }

    private fun writeJson(file: File, map: Map<String, String>) {
        try {
            val obj = buildJsonObject { map.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
            file.writeText(json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), obj))
        } catch (_: Exception) {
        }
    }

    fun init() {
        val map = readJson(configFile)
        CLIENT_ID = map[KEY_CLIENT_ID]?.takeIf { it.isNotBlank() } ?: FALLBACK_ID
    }

    fun updateClientId(newId: String) {
        if (newId.isNotBlank() && newId != CLIENT_ID) {
            CLIENT_ID = newId
            val map = readJson(configFile)
            map[KEY_CLIENT_ID] = newId
            writeJson(configFile, map)
        }
    }

    /**
     * Stable per-install device id. On Android this derived from ANDROID_ID;
     * on desktop we generate & persist a random UUID once.
     */
    fun getOrCreateSoundCloudDeviceId(): String {
        val map = readJson(authFlowFile)
        map["soundcloud_device_id"]?.takeIf { it.isNotBlank() }?.let { return it }

        val deviceId = UUID.randomUUID().toString().replace("-", "")
        map["soundcloud_device_id"] = deviceId
        writeJson(authFlowFile, map)
        return deviceId
    }
}
