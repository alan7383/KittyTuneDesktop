package com.my.kizzy.gateway.entities.presence

import kotlinx.serialization.Serializable

@Serializable
data class ActivityParty(val id: String, val size: List<Int>)
