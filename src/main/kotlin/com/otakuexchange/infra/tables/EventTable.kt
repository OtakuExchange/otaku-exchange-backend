package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object EventTable : Table("events") {
    val eventId = integer("event_id").autoIncrement()
    val topicId = integer("topic_id")
    val format = text("format")
    val name = text("name")
    val description = text("description")
    val closeTime = timestamp("close_time")
    val status = text("status")
    val resolutionRule = text("resolution_rule")
}