package com.otakuexchange.plugins

import io.ktor.server.plugins.statuspages.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RouteHandler")

fun Application.configureExceptionHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled error in route ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
        }
    }
}