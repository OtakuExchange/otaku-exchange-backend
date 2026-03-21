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
}