package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonUserRepository : IUserRepository {

    override suspend fun findById(id: Uuid): User? = transaction {
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findByEmail(email: String): User? = transaction {
        UserTable.selectAll()
            .where { UserTable.email eq email }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findByUsername(username: String): User? = transaction {
        UserTable.selectAll()
            .where { UserTable.username eq username }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findByProviderUserId(providerUserId: String, provider: AuthProvider): User? = transaction {
        UserTable.selectAll()
            .where { (UserTable.providerUserId eq providerUserId) and (UserTable.authProvider eq provider.name) }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun save(user: User): User = transaction {
        UserTable.insert {
            it[id] = user.id
            it[username] = user.username
            it[email] = user.email
            it[passwordHash] = user.passwordHash
            it[authProvider] = user.authProvider.name
            it[providerUserId] = user.providerUserId
            it[balance] = user.balance
            it[lockedBalance] = user.lockedBalance
            it[createdAt] = user.createdAt
        }
        user
    }

    override suspend fun updateUsername(id: Uuid, username: String): User = transaction {
        UserTable.update({ UserTable.id eq id }) {
            it[UserTable.username] = username
        }
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .singleOrNull()
            ?.toUser() ?: error("User not found after update")
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        username = this[UserTable.username],
        email = this[UserTable.email],
        passwordHash = this[UserTable.passwordHash],
        authProvider = AuthProvider.valueOf(this[UserTable.authProvider]),
        providerUserId = this[UserTable.providerUserId],
        balance = this[UserTable.balance],
        lockedBalance = this[UserTable.lockedBalance],
        createdAt = this[UserTable.createdAt]
    )
}