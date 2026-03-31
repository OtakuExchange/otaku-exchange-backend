package com.otakuexchange.testutil

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

private const val TEST_SECRET = "test-secret-for-unit-tests-only"
private const val TEST_ISSUER = "test"

fun Application.installTestClerkJwt() {
    install(Authentication) {
        jwt("clerk") {
            verifier(
                JWT.require(Algorithm.HMAC256(TEST_SECRET))
                    .withIssuer(TEST_ISSUER)
                    .build()
            )
            validate { credential ->
                credential.payload.subject?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token invalid or expired")
            }
        }
    }
}

fun createTestJwt(sub: String): String =
    JWT.create()
        .withIssuer(TEST_ISSUER)
        .withSubject(sub)
        .sign(Algorithm.HMAC256(TEST_SECRET))
