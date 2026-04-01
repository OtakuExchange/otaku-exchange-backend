package com.otakuexchange.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.otakuexchange.domain.repositories.IUserRepository
import io.ktor.http.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import io.github.cdimascio.dotenv.dotenv

private val authLogger = LoggerFactory.getLogger("ClerkAuth")

private val clerkSyncCache = ConcurrentHashMap<String, Long>()
private val clerkSyncLocks = ConcurrentHashMap<String, Mutex>()

fun Application.configureAuth() {

    val clerkJwksUrl = System.getenv("CLERK_JWKS_URL") ?: dotenv()["CLERK_JWKS_URL"]
    val clerkSyncTtlMs = (System.getenv("CLERK_SYNC_TTL_MS") ?: dotenv()["CLERK_SYNC_TTL_MS"])
        ?.toLongOrNull()
        ?.coerceIn(0L, 86_400_000L)
        ?: 300_000L

    val clerkJwkProvider = JwkProviderBuilder(URI(clerkJwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("clerk_optional") {
            authHeader { call ->
                val bearerToken = call.request.headers[HttpHeaders.Authorization]
                    ?.takeIf { it.startsWith("Bearer ") }
                    ?.removePrefix("Bearer ")

                if (bearerToken != null) {
                    return@authHeader HttpAuthHeader.Single("Bearer", bearerToken)
                }

                val cookieToken = call.request.headers[HttpHeaders.Cookie]
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("__session=") }
                    ?.removePrefix("__session=")
                    ?: return@authHeader null

                HttpAuthHeader.Single("Bearer", cookieToken)
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
                    verifier.verify(token)
                    verifier
                } catch (e: Exception) {
                    authLogger.warn("Clerk JWT rejected: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
            validate { credential ->
                val sub = credential.payload.subject ?: run {
                    authLogger.warn("Clerk JWT rejected: missing sub claim")
                    return@validate null
                }
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token invalid or expired")
            }
        }

        jwt("clerk") {
            authHeader { call ->
                // 1. Try Authorization: Bearer <token> header first (works through Cloudflare)
                val bearerToken = call.request.headers[HttpHeaders.Authorization]
                    ?.takeIf { it.startsWith("Bearer ") }
                    ?.removePrefix("Bearer ")

                if (bearerToken != null) {
                    return@authHeader HttpAuthHeader.Single("Bearer", bearerToken)
                }

                // 2. Fall back to __session cookie (local dev)
                val cookieToken = call.request.headers[HttpHeaders.Cookie]
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("__session=") }
                    ?.removePrefix("__session=")
                    ?: return@authHeader null

                HttpAuthHeader.Single("Bearer", cookieToken)
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
                    verifier.verify(token)
                    verifier
                } catch (e: Exception) {
                    authLogger.warn("Clerk JWT rejected: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
            validate { credential ->
                val sub = credential.payload.subject ?: run {
                    authLogger.warn("Clerk JWT rejected: missing sub claim")
                    return@validate null
                }
                val userRepository = application.get<IUserRepository>()
                val mutex = clerkSyncLocks.computeIfAbsent(sub) { Mutex() }
                mutex.lock()
                try {
                    val nowMs = System.currentTimeMillis()
                    val lastSync = clerkSyncCache[sub]
                    val shouldSync = lastSync == null || (clerkSyncTtlMs == 0L) || (nowMs - lastSync) >= clerkSyncTtlMs
                    if (shouldSync) {
                        syncClerkUser(credential.payload, userRepository)
                        clerkSyncCache[sub] = nowMs
                    }
                } finally {
                    mutex.unlock()
                }
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token invalid or expired")
            }
        }
    }
}

val ApplicationCall.userId: String
    get() = principal<JWTPrincipal>()!!.payload.subject

val ApplicationCall.clerkUserId: String
    get() = userId