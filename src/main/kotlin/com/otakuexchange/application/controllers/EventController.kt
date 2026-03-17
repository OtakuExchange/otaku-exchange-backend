package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.market.Comment
import kotlinx.serialization.Serializable
import com.otakuexchange.domain.repositories.ICommentRepository
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.plugins.clerkUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

@Serializable
data class CreateCommentRequest(val content: String, val parentId: Uuid? = null)

class EventController(
    private val eventRepository: IEventRepository,
    private val commentRepository: ICommentRepository,
    private val userRepository: IUserRepository
) : IRouteController {

    override fun registerRoutes(route: Route) {

        route.get("/topics/{topicId}/events") {
            val topicId = try {
                Uuid.parse(call.parameters["topicId"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid topicId")
                return@get
            }
            val events = eventRepository.getEventsByTopicId(topicId)
            call.respond(events)
        }

        route.get("/events/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val event = eventRepository.getById(id)
            if (event == null) call.respond(HttpStatusCode.NotFound, "Event not found")
            else call.respond(event)
        }

        route.get("/events/{id}/comments") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val clerkId = call.principal<JWTPrincipal>()?.payload?.subject
            val currentUser = clerkId?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            call.respond(commentRepository.getByEventId(id, currentUser?.id))
        }

    }

    override fun registerProtectedRoutes(route: Route) {

        route.post("/events/{id}/comments") {
            val eventId = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@post
            }
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
            val body = call.receive<CreateCommentRequest>()
            val saved = commentRepository.save(
                Comment(eventId = eventId, userId = user.id, content = body.content, parentId = body.parentId)
            )
            call.respond(HttpStatusCode.Created, saved)
        }

        route.post("/events/{id}/comments/{commentId}/like") {
            val commentId = try {
                Uuid.parse(call.parameters["commentId"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid commentId")
                return@post
            }
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
            val liked = commentRepository.likeComment(commentId, user.id)
            if (liked) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.Conflict, "Already liked")
        }

        route.delete("/events/{id}/comments/{commentId}/like") {
            val commentId = try {
                Uuid.parse(call.parameters["commentId"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid commentId")
                return@delete
            }
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "User not found")
            val unliked = commentRepository.unlikeComment(commentId, user.id)
            if (unliked) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Like not found")
        }

        route.post("/events") {
            val event = call.receive<Event>()
            val saved = eventRepository.save(event)
            call.respond(HttpStatusCode.Created, saved)
        }

        route.put("/events/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@put
            }
            val event = call.receive<Event>()
            if (event.id != id) {
                call.respond(HttpStatusCode.BadRequest, "ID mismatch")
                return@put
            }
            val updated = eventRepository.update(event)
            call.respond(updated)
        }

        route.delete("/events/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            val deleted = eventRepository.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Event not found")
        }
    }
}
