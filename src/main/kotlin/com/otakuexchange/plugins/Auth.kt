package com.otakuexchange.plugins

import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier())
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token invalid or expired")
            }
        }
    }
}

val ApplicationCall.userId: String
    get() = principal<JWTPrincipal>()!!.payload.subject