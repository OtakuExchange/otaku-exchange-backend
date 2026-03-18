package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.repositories.IEntityRepository
import com.otakuexchange.infra.tables.EntityTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonEntityRepository : IEntityRepository {

    override suspend fun save(entity: Entity): Entity = transaction {
        EntityTable.insert {
            it[id]             = entity.id
            it[name]           = entity.name
            it[abbreviatedName] = entity.abbreviatedName
            it[logoPath]       = entity.logoPath
            it[color]          = entity.color
            it[createdAt]      = entity.createdAt
        }
        entity
    }

    override suspend fun getAll(): List<Entity> = transaction {
        EntityTable.selectAll().map { it.toEntity() }
    }

    override suspend fun getById(id: Uuid): Entity? = transaction {
        EntityTable.selectAll()
            .where { EntityTable.id eq id }
            .singleOrNull()
            ?.toEntity()
    }

    private fun ResultRow.toEntity() = Entity(
        id             = this[EntityTable.id],
        name           = this[EntityTable.name],
        abbreviatedName = this[EntityTable.abbreviatedName],
        logoPath       = this[EntityTable.logoPath],
        color          = this[EntityTable.color],
        createdAt      = this[EntityTable.createdAt]
    )
}
