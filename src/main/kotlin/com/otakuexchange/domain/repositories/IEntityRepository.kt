package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Entity
import kotlin.uuid.Uuid

interface IEntityRepository {
    suspend fun save(entity: Entity): Entity
    suspend fun getAll(): List<Entity>
    suspend fun getById(id: Uuid): Entity?
}
