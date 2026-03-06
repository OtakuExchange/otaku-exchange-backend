package com.otakuexchange.domain.user

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class AuthProvider { GOOGLE, DISCORD, EMAIL }

@Serializable
data class User(
    val id: Uuid = Uuid.random(),
    val username: String,
    val email: String,
    val passwordHash: String? = null,
    val authProvider: AuthProvider,
    val providerUserId: String? = null,
    val balance: Double = 0.0,
    val lockedBalance: Double = 0.0,
    val createdAt: Instant = Clock.System.now()
) {
    val availableBalance: Double get() = balance - lockedBalance
}