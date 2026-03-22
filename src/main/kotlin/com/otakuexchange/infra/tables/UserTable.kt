package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.*

object UserTable : Table("users") {
    val id = uuid("id")
    val username = text("username").uniqueIndex()
    val email = text("email").uniqueIndex()
    val authProvider = text("auth_provider")
    val providerUserId = text("provider_user_id").nullable().index()
    val balance = long("balance").default(0L)
    val lockedBalance = long("locked_balance").default(0L)
    val isAdmin = bool("is_admin").default(false)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}