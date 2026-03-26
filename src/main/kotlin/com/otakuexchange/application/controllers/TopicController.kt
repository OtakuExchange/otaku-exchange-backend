package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.repositories.ITopicRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

class TopicController(private val topicRepository: ITopicRepository) : IRouteController {

    override fun registerRoutes(route: Route) {

        route.get("/topics") {
            val topics = topicRepository.getTopics().filter { !it.hidden }
            call.respond(topics)
        }

        route.get("/topics/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val topic = topicRepository.getById(id)
            if (topic == null) call.respond(HttpStatusCode.NotFound, "Topic not found")
            else call.respond(topic)
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