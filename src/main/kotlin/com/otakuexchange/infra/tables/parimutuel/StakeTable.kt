package com.otakuexchange.infra.tables.parimutuel

import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object StakeTable : Table("stakes") {
    val id            = uuid("id")
    val userId        = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val marketPoolId  = uuid("market_pool_id").references(MarketPoolTable.id, onDelete = ReferenceOption.CASCADE).index()
    val amount        = long("amount").default(0)   // total cents this user has staked
    val createdAt     = timestamp("created_at")
    val updatedAt     = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}