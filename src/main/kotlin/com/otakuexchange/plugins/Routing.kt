package com.otakuexchange.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import org.koin.ktor.ext.get
import com.otakuexchange.application.controllers.IRouteController
import org.koin.core.qualifier.named
import io.ktor.server.response.*

fun Application.configureRouting() {
    install(ContentNegotiation) {
        json()
    }

    val controllers = listOf(
        get<IRouteController>(named("topicController")),
        get<IRouteController>(named("marketController")),
        get<IRouteController>(named("eventController")),
        get<IRouteController>(named("authController"))
    )

    routing {
        get("/health") {
            call.respondText("ok")
        }
        controllers.forEach { it.registerRoutes(this) }
    }
}
