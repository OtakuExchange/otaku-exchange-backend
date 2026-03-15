package com.otakuexchange.domain.user

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class AuthProvider { GOOGLE, DISCORD, EMAIL, CLERK }

@Serializable
data class User(
    val id: Uuid = Uuid.random(),
    val username: String,
    val email: String,
    val passwordHash: String? = null,
    val authProvider: AuthProvider,
    val providerUserId: String? = null,
    val balance: Long = 0L,
    val lockedBalance: Long = 0L,
    val createdAt: Instant = Clock.System.now()
) {
    val availableBalance: Long get() = balance - lockedBalance
}