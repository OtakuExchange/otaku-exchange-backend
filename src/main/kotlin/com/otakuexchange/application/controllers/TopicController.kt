package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import kotlinx.serialization.Serializable
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

@Serializable
data class SubtopicEventCounts(
    val subtopicId: Uuid,
    val total: Int,
    val byStatus: Map<String, Int>
)

@Serializable
data class TopicSubtopicEventCounts(
    val topicId: Uuid,
    val subtopics: List<SubtopicEventCounts>
)

class TopicController(
    private val topicRepository: ITopicRepository,
    private val userRepository: IUserRepository
) : IRouteController {

    override fun registerRoutes(route: Route) {

        route.get("/topics") {
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            val topics = topicRepository.getTopics(currentUser?.id)
            call.respond(topics)
        }

        route.get("/topics/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            val topic = topicRepository.getById(id, currentUser?.id)
            if (topic == null) call.respond(HttpStatusCode.NotFound, "Topic not found")
            else call.respond(topic)
        }

        route.get("/topics/{id}/subtopics/event-counts") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val bySubtopic = topicRepository.getEventCountsBySubtopic(id)
            val topicSubtopicEventCounts = TopicSubtopicEventCounts(
                topicId = id,
                subtopics = bySubtopic.entries
                    .sortedBy { it.key.toString() }
                    .map { (subtopicId, byStatus) ->
                        SubtopicEventCounts(
                            subtopicId = subtopicId,
                            total = byStatus.values.sum(),
                            byStatus = byStatus
                        )
                    }
            )
            call.respond(topicSubtopicEventCounts)
        }

    }

    override fun registerProtectedRoutes(route: Route) {

        route.post("/topics") {
            val topic = call.receive<Topic>()
            val saved = topicRepository.save(topic)
            call.respond(HttpStatusCode.Created, saved)
        }

        route.put("/topics/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@put
            }
            val topic = call.receive<Topic>()
            if (topic.id != id) {
                call.respond(HttpStatusCode.BadRequest, "ID mismatch")
                return@put
            }
            val updated = topicRepository.update(topic)
            call.respond(updated)
        }

        route.delete("/topics/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            val deleted = topicRepository.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Topic not found")
        }
    }
}