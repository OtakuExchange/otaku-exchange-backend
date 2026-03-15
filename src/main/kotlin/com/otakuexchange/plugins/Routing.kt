package com.otakuexchange.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.auth.*
import org.koin.ktor.ext.get
import com.otakuexchange.application.controllers.IRouteController
import org.koin.core.qualifier.named
import io.ktor.server.response.*

fun Application.configureRouting() {
    install(ContentNegotiation) {
        json()
    }

    val authController    = get<IRouteController>(named("authController"))
    val topicController   = get<IRouteController>(named("topicController"))
    val marketController  = get<IRouteController>(named("marketController"))
    val eventController   = get<IRouteController>(named("eventController"))

    routing {
        get("/health") {
            call.respondText("ok")
        }

        // Public: register / login / OAuth flows
        authController.registerRoutes(this)
        topicController.registerRoutes(this)
        marketController.registerRoutes(this)
        eventController.registerRoutes(this)

        // Protected: all resource endpoints require a valid Clerk JWT
        authenticate("clerk") {
            
        }
    }
}
