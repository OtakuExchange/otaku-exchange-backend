package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import kotlin.uuid.Uuid

interface IUserRepository {
    suspend fun findById(id: Uuid): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByProviderUserId(providerUserId: String, provider: AuthProvider): User?
    suspend fun findByUsername(username: String): User?
    suspend fun save(user: User): User
    suspend fun updateUsername(id: Uuid, username: String): User
    suspend fun addBalance(id: Uuid, amount: Long): User
        // Deducts from balance and adds to lockedBalance — returns false if insufficient
    suspend fun lockBalance(id: Uuid, amount: Long): Boolean
    // Moves amount from lockedBalance back to balance (cancel/refund)
    suspend fun unlockBalance(id: Uuid, amount: Long)
    // Permanently deducts from lockedBalance (consumed by escrow on fill)
    suspend fun consumeLockedBalance(id: Uuid, amount: Long)
    suspend fun hasBalance(id: Uuid, amount: Long): Boolean
    suspend fun subtractBalance(id: Uuid, amount: Long): User
}