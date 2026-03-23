package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Topic(
    val id: Uuid = Uuid.random(),
    val topic: String,
    val description: String? = null
)