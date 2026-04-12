package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.market.Comment
import kotlinx.serialization.Serializable
import com.otakuexchange.domain.repositories.IBookmarkRepository
import com.otakuexchange.domain.repositories.ICommentRepository
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.services.EventSchedulerService
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.plugins.clerkUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import com.otakuexchange.domain.event.EventStatus

@Serializable
data class CreateCommentRequest(val content: String, val parentId: Uuid? = null)

@Serializable
data class UpdateStatusRequest(val status: String)

class EventController(
    private val eventRepository: IEventRepository,
    private val commentRepository: ICommentRepository,
    private val userRepository: IUserRepository,
    private val bookmarkRepository: IBookmarkRepository,
    private val scheduler: EventSchedulerService
) : IRouteController {

    override fun registerRoutes(route: Route) {

        route.get("/topics/{topicId}/events") {
            val topicId = try {
                Uuid.parse(call.parameters["topicId"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid topicId")
                return@get
            }
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            call.respond(eventRepository.getEventsByTopicId(topicId, currentUser?.id))
        }

        route.get("/events/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            val event = eventRepository.getById(id, currentUser?.id)
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

        route.get("/events/open") {
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            call.respond(eventRepository.getEventsByStatus("open", currentUser?.id))
        }

        route.get("/events/staking-closed") {
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            call.respond(eventRepository.getEventsByStatus("staking_closed", currentUser?.id))
        }

        route.get("/events/resolved/recent") {
            val currentUser = call.principal<JWTPrincipal>()?.payload?.subject?.let {
                userRepository.findByProviderUserId(it, AuthProvider.CLERK)
            }
            call.respond(eventRepository.getRecentlyResolvedEvents(currentUser?.id))
        }
    }

    override fun registerProtectedRoutes(route: Route) {

        route.post("/events/{id}/seen") {
            val eventId = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@post
            }
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
            eventRepository.markEventSeen(user.id, eventId)
            val updated = eventRepository.getById(eventId, user.id)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Event not found")
            call.respond(updated)
        }

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

        route.post("/events/{id}/bookmark") {
            val eventId = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@post
            }
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
            val added = bookmarkRepository.addBookmark(user.id, eventId)
            if (!added) {
                call.respond(HttpStatusCode.Conflict, "Already bookmarked")
                return@post
            }
            val updated = eventRepository.getById(eventId, user.id)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Event not found")
            call.respond(updated)
        }

        route.delete("/events/{id}/bookmark") {
            val eventId = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "User not found")
            val removed = bookmarkRepository.removeBookmark(user.id, eventId)
            if (!removed) {
                call.respond(HttpStatusCode.NotFound, "Bookmark not found")
                return@delete
            }
            val updated = eventRepository.getById(eventId, user.id)
                ?: return@delete call.respond(HttpStatusCode.NotFound, "Event not found")
            call.respond(updated)
        }

        route.post("/events") {
            val event = call.receive<Event>()
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

            val saved = eventRepository.save(event.copy(name = event.name.trim(), alias = event.alias?.trim()))
            if (saved.status == EventStatus.open || saved.status == EventStatus.hidden) scheduler.schedule(saved)
            val hydrated = eventRepository.getById(saved.id, user.id)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "Failed to load created event")
            call.respond(HttpStatusCode.Created, hydrated)
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
            // Reschedule if still open (schedule() cancels the old job automatically)
            if (updated.status == EventStatus.open || updated.status == EventStatus.hidden) scheduler.schedule(updated)
            else scheduler.cancel(updated.id)
            call.respond(updated)
        }

        route.delete("/events/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            scheduler.cancel(id)
            val deleted = eventRepository.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Event not found")
        }

        route.patch("/events/{id}/status") {
            val user = userRepository.findByProviderUserId(call.clerkUserId, AuthProvider.CLERK)
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, "User not found")
            if (!user.isAdmin) return@patch call.respond(HttpStatusCode.Forbidden, "Admin only")

            val id = try { Uuid.parse(call.parameters["id"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid id"); return@patch }

            val body = call.receive<UpdateStatusRequest>()
            val allowed = setOf(EventStatus.open.name, EventStatus.hidden.name, EventStatus.staking_closed.name)
            if (body.status.lowercase() !in allowed) {
                call.respond(HttpStatusCode.BadRequest, "Status must be one of: $allowed")
                return@patch
            }

            val updated = eventRepository.updateStatus(id, body.status)

            // If manually reopened, reschedule auto-close
            // If manually closed, cancel the scheduled job so it doesn't re-close
            if (body.status == EventStatus.open.name || body.status == EventStatus.hidden.name) {
                val event = eventRepository.getById(id, null)
                event?.let {
                    scheduler.schedule(Event(
                        id = it.id, topicId = it.topicId, format = it.format,
                        name = it.name, description = it.description,
                        closeTime = it.closeTime, status = it.status,
                        resolutionRule = it.resolutionRule, logoPath = it.logoPath,
                        pandaScoreId = it.pandaScoreId
                    ))
                }
            } else {
                scheduler.cancel(id)
            }

            if (!updated) {
                call.respond(HttpStatusCode.NotFound, "Event not found")
                return@patch
            }
            val hydrated = eventRepository.getById(id, user.id)
                ?: return@patch call.respond(HttpStatusCode.InternalServerError, "Failed to load updated event")
            call.respond(hydrated)
        }
    }
}