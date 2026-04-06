package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object UserEventViewTable : Table("user_event_views") {
    val userId   = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val eventId  = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE)
    val viewedAt = timestamp("viewed_at")

    override val primaryKey = PrimaryKey(userId, eventId)
}
