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
    val orderController   = get<IRouteController>(named("orderController"))

    routing {
        get("/health") {
            call.respondText("ok")
        }

        // Public GETs — sync user if JWT is present, no auth required
        authenticate("clerk", optional = true) {
            syncClerkUser()
            authController.registerRoutes(this)
            topicController.registerRoutes(this)
            marketController.registerRoutes(this)
            eventController.registerRoutes(this)
            orderController.registerRoutes(this)
        }

        // Protected writes — valid Clerk JWT required
        authenticate("clerk") {
            topicController.registerProtectedRoutes(this)
            marketController.registerProtectedRoutes(this)
            eventController.registerProtectedRoutes(this)
            orderController.registerProtectedRoutes(this)
        }
    }
}