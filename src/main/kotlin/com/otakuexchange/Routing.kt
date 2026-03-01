package com.otakuexchange

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.otakuexchange.domain.repositories.ITopicRepository
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureRouting(topicRepository: ITopicRepository) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("fu ln!")
        }

        get("/topics") {
            val topics = topicRepository.getTopics()
            call.respond(topics)
        }
    }
}
