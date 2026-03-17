package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.*

object CommentLikeTable : Table("comment_likes") {
    val id        = uuid("id")
    val commentId = uuid("comment_id").references(CommentTable.id, onDelete = ReferenceOption.CASCADE).index()
    val userId    = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
