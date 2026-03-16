package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.repositories.IEventRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

class EventController(private val eventRepository: IEventRepository) : IRouteController {

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

    }

    override fun registerProtectedRoutes(route: Route) {

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