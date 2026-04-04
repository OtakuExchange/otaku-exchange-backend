package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.*

object EventTable : Table("events") {
    val id = uuid("id")
    val topicId = uuid("topic_id").references(TopicTable.id, onDelete = ReferenceOption.CASCADE).index()
    val format = text("format")
    val name = text("name")
    val description = text("description")
    val closeTime = timestamp("close_time")
    val status = text("status")
    val resolutionRule = text("resolution_rule")
    val logoPath = text("logo_path").nullable()
    val pandaScoreId = long("panda_score_id").nullable()
    val createdAt = timestamp("created_at")
    val multiplier = integer("multiplier").default(1)

    override val primaryKey = PrimaryKey(id)
}