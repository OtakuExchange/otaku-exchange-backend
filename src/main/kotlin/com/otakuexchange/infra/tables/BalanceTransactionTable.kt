package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object BalanceTransactionTable : Table("balance_transactions") {
    val id          = uuid("id")
    val userId      = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val amount      = long("amount")
    val balance     = long("balance")
    val type        = text("type")
    val referenceId = uuid("reference_id").nullable()
    val createdAt   = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}