package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.*

object SubtopicTable : Table("subtopics") {
    val id        = uuid("id")
    val topicId   = uuid("topic_id").references(TopicTable.id, onDelete = ReferenceOption.CASCADE).index()
    val name      = text("name")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
