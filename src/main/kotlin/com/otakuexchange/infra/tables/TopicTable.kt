package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table

object TopicTable : Table("topics") {
    val id = integer("topic_id").autoIncrement()
    val topic = text("topic")
    val description = text("description")
}