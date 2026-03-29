package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object DailyStreakTable : Table("daily_streaks") {
    val userId    = uuid("user_id").references(UserTable.id)
    val streak    = integer("streak").default(0)
    val lastClaim = date("last_claim")

    override val primaryKey = PrimaryKey(userId)
}