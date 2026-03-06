package com.otakuexchange.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.otakuexchange.domain.user.User
import java.util.Date

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: error("JWT_SECRET not set")
    private val issuer = System.getenv("JWT_ISSUER") ?: "otaku-exchange"
    private val expiryMs = 1000L * 60 * 60 * 24 * 7  // 7 days

    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    fun generateToken(user: User): String = JWT.create()
        .withIssuer(issuer)
        .withSubject(user.id.toString())
        .withClaim("username", user.username)
        .withClaim("email", user.email)
        .withExpiresAt(Date(System.currentTimeMillis() + expiryMs))
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()
}