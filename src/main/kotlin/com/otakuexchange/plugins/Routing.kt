package com.otakuexchange.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.auth.*
import org.koin.ktor.ext.get
import com.otakuexchange.application.controllers.IRouteController
import org.koin.core.qualifier.named
import io.ktor.http.*
import io.ktor.server.response.*

fun Application.configureRouting() {
    install(ContentNegotiation) {
        json()
    }

    val authController     = get<IRouteController>(named("authController"))
    val adminController    = get<IRouteController>(named("adminController"))
    val topicController    = get<IRouteController>(named("topicController"))
    val marketController   = get<IRouteController>(named("marketController"))
    val eventController    = get<IRouteController>(named("eventController"))
    val subtopicController = get<IRouteController>(named("subtopicController"))
    val orderController    = get<IRouteController>(named("orderController"))
    val rankController     = get<IRouteController>(named("rankController"))
    val stakeController    = get<IRouteController>(named("stakeController"))
    val dailyStreakController = get<IRouteController>(named("dailyStreakController"))
    val entityController = get<IRouteController>(named("entityController"))

    routing {
        // Handle CORS preflight for all routes
        options("{...}") {
            call.respond(HttpStatusCode.OK)
        }

        get("/health") {
            call.respondText("ok")
        }

        // Public GETs — sync user if JWT is present, no auth required
        authenticate("clerk", optional = true) {
            authController.registerRoutes(this)
            topicController.registerRoutes(this)
            marketController.registerRoutes(this)
            eventController.registerRoutes(this)
            orderController.registerRoutes(this)
            subtopicController.registerRoutes(this)
            rankController.registerRoutes(this)
            stakeController.registerRoutes(this)      // public: pool list + preview
            entityController.registerRoutes(this)
        }

        // Protected writes — valid Clerk JWT required
        authenticate("clerk") {
            topicController.registerProtectedRoutes(this)
            marketController.registerProtectedRoutes(this)
            eventController.registerProtectedRoutes(this)
            orderController.registerProtectedRoutes(this)
            subtopicController.registerProtectedRoutes(this)
            adminController.registerProtectedRoutes(this)
            stakeController.registerProtectedRoutes(this)  // protected: place stake + resolve
            dailyStreakController.registerProtectedRoutes(this)
            entityController.registerProtectedRoutes(this)
        }
    }
}