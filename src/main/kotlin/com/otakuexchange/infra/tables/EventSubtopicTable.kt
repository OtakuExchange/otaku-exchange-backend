package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object EventSubtopicTable : Table("event_subtopics") {
    val eventId    = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE)
    val subtopicId = uuid("subtopic_id").references(SubtopicTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(eventId, subtopicId)
}
