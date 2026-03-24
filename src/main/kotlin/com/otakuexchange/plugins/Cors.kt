package com.otakuexchange.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Cookie)
        allowCredentials = true

        // Allow your Vercel frontend
        allowHost("fillybexchange.vercel.app", schemes = listOf("https"))

        // Allow local development
        allowHost("localhost:5173", schemes = listOf("http"))
        allowHost("localhost:5174", schemes = listOf("http"))
    }
}