package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
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
            it[authProvider] = user.authProvider.name
            it[providerUserId] = user.providerUserId
            it[balance] = user.balance
            it[lockedBalance] = user.lockedBalance
            it[isAdmin] = user.isAdmin
            it[avatarUrl] = user.avatarUrl
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

    override suspend fun updateAvatarUrl(id: Uuid, avatarUrl: String): Unit = transaction {
        UserTable.update({ UserTable.id eq id }) {
            it[UserTable.avatarUrl] = avatarUrl
        }
    }

    override suspend fun addBalance(id: Uuid, amount: Long): User = transaction {
        UserTable.update({ UserTable.id eq id }) {
            it[UserTable.balance] = UserTable.balance + amount
        }
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .singleOrNull()
            ?.toUser() ?: error("User not found after balance update")
    }

    override suspend fun hasBalance(id: Uuid, amount: Long): Boolean = transaction {
        val user = UserTable.selectAll()
            .where { UserTable.id eq id }
            .singleOrNull()
            ?.toUser() ?: return@transaction false
        user.balance >= amount
    }

    override suspend fun subtractBalance(id: Uuid, amount: Long): User = transaction {
        UserTable.update({ UserTable.id eq id }) {
            it[UserTable.balance] = UserTable.balance - amount
        }
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .singleOrNull()
            ?.toUser() ?: error("User not found after balance subtraction")
    }

    override suspend fun lockBalance(id: Uuid, amount: Long): Boolean = TODO("Not implemented")
    override suspend fun unlockBalance(id: Uuid, amount: Long): Unit = TODO("Not implemented")
    override suspend fun consumeLockedBalance(id: Uuid, amount: Long): Unit = TODO("Not implemented")

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        username = this[UserTable.username],
        email = this[UserTable.email],
        authProvider = AuthProvider.valueOf(this[UserTable.authProvider]),
        providerUserId = this[UserTable.providerUserId],
        balance = this[UserTable.balance],
        lockedBalance = this[UserTable.lockedBalance],
        isAdmin = this[UserTable.isAdmin],
        avatarUrl = this[UserTable.avatarUrl],
        createdAt = this[UserTable.createdAt]
    )
}