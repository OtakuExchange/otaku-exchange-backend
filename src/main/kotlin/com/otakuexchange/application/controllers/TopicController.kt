package com.otakuexchange.application.controllers

import com.otakuexchange.domain.repositories.ITopicRepository

import io.ktor.server.routing.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import org.koin.ktor.ext.get

class TopicController(private val topicRepository: ITopicRepository) : IRouteController {
    override fun registerRoutes(route: Route) {
        route.get("/topics") {
            val topics = topicRepository.getTopics()
            call.respond(topics)
        }
    }
}