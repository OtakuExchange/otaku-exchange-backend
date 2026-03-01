package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable

@Serializable
data class Topic(
    val id: Int,           // unique identifier for the topic
    val topic: String,      // the name of the topic (e.g., "One Piece", "JJK")
    val description: String // a short description about the topic
)