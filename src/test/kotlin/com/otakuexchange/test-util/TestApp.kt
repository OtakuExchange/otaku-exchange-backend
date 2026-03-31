package com.otakuexchange.testutil

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

fun testApp(
    publicRoutes: (Route.() -> Unit)? = null,
    protectedRoutes: (Route.() -> Unit)? = null,
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
) {
    testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            installTestClerkJwt()
            routing {
                publicRoutes?.invoke(this)
                authenticate("clerk") {
                    protectedRoutes?.invoke(this)
                }
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }
        block(client)
    }
}
