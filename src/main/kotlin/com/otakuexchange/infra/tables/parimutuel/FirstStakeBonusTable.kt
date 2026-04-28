package com.otakuexchange.infra.tables.parimutuel

import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.Table

object FirstStakeBonusTable : Table("first_stake_bonuses") {
    val userId  = uuid("user_id").references(UserTable.id)
    val eventId = uuid("event_id").references(EventTable.id)
    val bonus   = long("bonus")

    override val primaryKey = PrimaryKey(userId, eventId)
}