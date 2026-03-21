package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.*

object EntityTable : Table("entities") {
    val id        = uuid("id")
    val name             = text("name").uniqueIndex()
    val abbreviatedName  = text("abbreviated_name").nullable()
    val logoPath         = text("logo_path")
    val color        = text("color").nullable()
    val pandaScoreId = long("panda_score_id").nullable()
    val createdAt    = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
