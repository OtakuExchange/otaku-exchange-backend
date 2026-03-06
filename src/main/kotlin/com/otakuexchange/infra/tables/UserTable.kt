package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.*

object UserTable : Table("users") {
    val id = uuid("id")
    val username = text("username").uniqueIndex()
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash").nullable()
    val authProvider = text("auth_provider")
    val providerUserId = text("provider_user_id").nullable().index()
    val balance = double("balance").default(0.0)
    val lockedBalance = double("locked_balance").default(0.0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}