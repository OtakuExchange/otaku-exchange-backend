package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.*

object BookmarkTable : Table("bookmarks") {
    val id      = uuid("id")
    val userId  = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val eventId = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE).index()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
