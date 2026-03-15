package com.otakuexchange.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import io.github.cdimascio.dotenv.dotenv

private val authLogger = LoggerFactory.getLogger("ClerkAuth")

fun Application.configureAuth() {

    val clerkJwksUrl = System.getenv("CLERK_JWKS_URL") ?: dotenv()["CLERK_JWKS_URL"]

    val clerkJwkProvider = JwkProviderBuilder(URL(clerkJwksUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("clerk") {
            authHeader { call ->
                val token = call.request.headers[HttpHeaders.Cookie]
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("__session=") }
                    ?.removePrefix("__session=")
                    ?: return@authHeader null
                HttpAuthHeader.Single("Bearer", token)
            }
            verifier { header ->
                val token = (header as? HttpAuthHeader.Single)?.blob
                    ?: run {
                        authLogger.warn("Clerk auth: missing or malformed Authorization header")
                        return@verifier null
                    }
                try {
                    val decoded = JWT.decode(token)
                    val jwk = clerkJwkProvider.get(decoded.keyId)
                    val verifier = JWT.require(Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null))
                        .acceptLeeway(10)
                        .build()
                    verifier.verify(token) // verify eagerly so we can catch and log the real error
                    verifier
                } catch (e: Exception) {
                    authLogger.warn("Clerk JWT rejected: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
            validate { credential ->
                authLogger.debug("Clerk JWT: sub=${credential.payload.subject}, exp=${credential.payload.expiresAt}")
                if (credential.payload.subject != null) JWTPrincipal(credential.payload)
                else {
                    authLogger.warn("Clerk JWT rejected: missing sub claim")
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token invalid or expired")
            }
        }
    }
}

val ApplicationCall.userId: String
    get() = principal<JWTPrincipal>()!!.payload.subject

// Clerk-specific: the JWT subject is the Clerk user ID (e.g. "user_2abc...")
val ApplicationCall.clerkUserId: String
    get() = userId