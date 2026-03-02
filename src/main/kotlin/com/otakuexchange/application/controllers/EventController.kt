package com.otakuexchange.application.controllers

import com.otakuexchange.domain.repositories.IEventRepository
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.http.HttpStatusCode

class EventController(private val eventRepository: IEventRepository) : IRouteController {
    override fun registerRoutes(route: Route) {
        route.get("/events/{topicId}") {
            val topicId = call.parameters["topicId"]?.toIntOrNull()
            if (topicId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid topicId")
                return@get
            }
            val events = eventRepository.getEventsByTopicId(topicId)
            call.respond(events)
        }
    }
}