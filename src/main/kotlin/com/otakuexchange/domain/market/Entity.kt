package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Entity(
    val id: Uuid = Uuid.random(),
    val name: String,
    val abbreviatedName: String? = null,
    val logoPath: String,
    val color: String,
    val createdAt: Instant = Clock.System.now()
)
