package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Subtopic
import com.otakuexchange.domain.repositories.ISubtopicRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

class SubtopicController(
    private val subtopicRepository: ISubtopicRepository,
    private val userRepository: IUserRepository
) : IRouteController {

    override fun registerRoutes(route: Route) {

        route.get("/subtopics/{id}/events") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            val events = subtopicRepository.getEventsBySubtopicId(id, currentUser?.id)
            call.respond(events)
        }

    }

    override fun registerProtectedRoutes(route: Route) {

        route.post("/topics/{topicId}/subtopics") {
            val topicId = try {
                Uuid.parse(call.parameters["topicId"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid topicId")
                return@post
            }
            val subtopic = call.receive<Subtopic>().copy(topicId = topicId)
            val saved = subtopicRepository.save(subtopic)
            call.respond(HttpStatusCode.Created, saved)
        }

        route.delete("/subtopics/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            val deleted = subtopicRepository.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Subtopic not found")
        }

        route.post("/events/{eventId}/subtopics/{subtopicId}") {
            val eventId = try { Uuid.parse(call.parameters["eventId"] ?: "") } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid eventId"); return@post
            }
            val subtopicId = try { Uuid.parse(call.parameters["subtopicId"] ?: "") } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid subtopicId"); return@post
            }
            subtopicRepository.addEventToSubtopic(eventId, subtopicId)
            call.respond(HttpStatusCode.NoContent)
        }

        route.delete("/events/{eventId}/subtopics/{subtopicId}") {
            val eventId = try { Uuid.parse(call.parameters["eventId"] ?: "") } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid eventId"); return@delete
            }
            val subtopicId = try { Uuid.parse(call.parameters["subtopicId"] ?: "") } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid subtopicId"); return@delete
            }
            subtopicRepository.removeEventFromSubtopic(eventId, subtopicId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
