package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TopicWithSubtopics(
    val id: Uuid,
    val topic: String,
    val description: String,
    val subtopics: List<Subtopic>
)
