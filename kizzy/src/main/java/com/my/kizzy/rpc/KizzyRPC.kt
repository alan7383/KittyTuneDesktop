    /*
     *
     *  ******************************************************************
     *  *  * Copyright (C) 2022
     *  *  * KizzyRPC.kt is part of Kizzy
     *  *  *  and can not be copied and/or distributed without the express
     *  *  * permission of yzziK(Vaibhav)
     *  *  *****************************************************************
     *
     *
     */
    
    package com.my.kizzy.rpc
    
    import com.my.kizzy.gateway.DiscordWebSocket
    import com.my.kizzy.gateway.entities.presence.Activity
    import com.my.kizzy.gateway.entities.presence.Assets
    import com.my.kizzy.gateway.entities.presence.Metadata
    import com.my.kizzy.gateway.entities.presence.Presence
    import com.my.kizzy.gateway.entities.presence.Timestamps
    import com.my.kizzy.repository.KizzyRepository
    import io.ktor.client.HttpClient
    import io.ktor.client.request.get
    import io.ktor.client.request.header
    import io.ktor.client.statement.bodyAsText
    import org.json.JSONObject
    
    /**
     * Modified by Zion Huang
     */
    open class KizzyRPC(token: String) {
        private val kizzyRepository = KizzyRepository()
        private val discordWebSocket = DiscordWebSocket(token)
    
        fun closeRPC() {
            discordWebSocket.close()
        }
    
        fun isRpcRunning(): Boolean {
            return discordWebSocket.isSocketConnectedToAccount()
        }
    
    suspend fun setActivity(
        name: String,
        state: String?,
        details: String?,
        largeImage: RpcImage?,
        smallImage: RpcImage?,
        largeText: String? = null,
        smallText: String? = null,
        buttons: List<Pair<String, String>>? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        type: Type = Type.PLAYING,
        streamUrl: String? = null,
        applicationId: String? = null,
        status: String? = "online",
        since: Long? = null,
    ) {
        if (!isRpcRunning()) {
            discordWebSocket.connect()
        }
        val presence = Presence(
            activities = listOf(
                Activity(
                    name = name,
                    state = state,
                    details = details,
                    type = type.value,
                    timestamps = if (startTime != null || endTime != null) {
                        Timestamps(start = startTime, end = endTime)
                    } else null,
                    assets = Assets(
                        // Force image resolution
                        largeImage = largeImage?.resolveImage(kizzyRepository),
                        smallImage = smallImage?.resolveImage(kizzyRepository),
                        largeText = largeText,
                        smallText = smallText
                    ),
                    buttons = buttons?.map { it.first },
                    metadata = Metadata(buttonUrls = buttons?.map { it.second }),
                    // MAJOR FIX: Send applicationId regardless
                    applicationId = applicationId, 
                    url = streamUrl
                )
            ),
            afk = true,
            since = since,
            status = status ?: "online"
        )
        discordWebSocket.sendActivity(presence)
    }

        enum class Type(val value: Int) {
            PLAYING(0),
            STREAMING(1),
            LISTENING(2),
            WATCHING(3),
            COMPETING(5)
        }
    
        companion object {
            suspend fun getUserInfo(token: String): Result<UserInfo> = runCatching {
                val client = HttpClient()
                try {
                    val response = client.get("https://discord.com/api/v9/users/@me") {
                        header("Authorization", token)
                    }
                    check(response.status.value in 200..299) { "Discord profile request failed: ${response.status.value}" }
                    val json = JSONObject(response.bodyAsText())
                    val username = json.getString("username")
                    val name = json.optString("global_name").takeUnless { it.isBlank() || it == "null" } ?: username
                    val avatar = json.optString("avatar").takeUnless { it.isBlank() || it == "null" }
                    val avatarUrl = avatar?.let { "https://cdn.discordapp.com/avatars/${json.getString("id")}/$it.png?size=128" }
                    UserInfo(username, name, avatarUrl)
                } finally {
                    client.close()
                }
            }
        }
    }
    


