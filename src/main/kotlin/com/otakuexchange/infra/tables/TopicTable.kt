package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table

object TopicTable : Table("topics") {
    val id = uuid("id")
    val topic = text("topic")
    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(id)
}