package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.*

object CommentTable : Table("comments") {
    val id       = uuid("id")
    val eventId  = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE).index()
    val userId   = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val parentId = uuid("parent_id").references(id, onDelete = ReferenceOption.CASCADE).nullable().index()
    val content   = text("content")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
