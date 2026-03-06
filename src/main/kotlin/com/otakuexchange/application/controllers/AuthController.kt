package com.otakuexchange.application.controllers

import com.otakuexchange.plugins.JwtConfig
import com.otakuexchange.plugins.userId
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import com.otakuexchange.domain.repositories.IUserRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt

import kotlin.uuid.Uuid

// ── Request / Response shapes ─────────────────────────────────────────────────

@Serializable data class RegisterRequest(val username: String, val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String)
@Serializable data class UpdateUsernameRequest(val username: String)

// ── OAuth config ──────────────────────────────────────────────────────────────

private object OAuthConfig {
    val googleClientId     = System.getenv("GOOGLE_CLIENT_ID")     ?: error("GOOGLE_CLIENT_ID not set")
    val googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: error("GOOGLE_CLIENT_SECRET not set")
    val googleRedirectUri  = System.getenv("GOOGLE_REDIRECT_URI")  ?: "http://localhost:8080/auth/google/callback"

    val discordClientId     = System.getenv("DISCORD_CLIENT_ID")     ?: error("DISCORD_CLIENT_ID not set")
    val discordClientSecret = System.getenv("DISCORD_CLIENT_SECRET") ?: error("DISCORD_CLIENT_SECRET not set")
    val discordRedirectUri  = System.getenv("DISCORD_REDIRECT_URI")  ?: "http://localhost:8080/auth/discord/callback"
}

// ── OAuth response shapes ─────────────────────────────────────────────────────

@Serializable private data class GoogleTokenResponse(val access_token: String)
@Serializable private data class GoogleUserInfo(val id: String, val email: String, val name: String)
@Serializable private data class DiscordTokenResponse(val access_token: String)
@Serializable private data class DiscordUserInfo(val id: String, val username: String, val email: String)

// ── Controller ────────────────────────────────────────────────────────────────

class AuthController(
    private val userRepository: IUserRepository
) : IRouteController {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    override fun registerRoutes(route: Route) {

        // ── Email / Password ──────────────────────────────────────────────────

        route.post("/auth/register") {
            val req = call.receive<RegisterRequest>()

            if (userRepository.findByEmail(req.email) != null) {
                call.respond(HttpStatusCode.Conflict, "Email already in use")
                return@post
            }

            val user = userRepository.save(
                User(
                    username     = req.username,
                    email        = req.email,
                    passwordHash = BCrypt.hashpw(req.password, BCrypt.gensalt()),
                    authProvider = AuthProvider.EMAIL
                )
            )
            call.respond(AuthResponse(JwtConfig.generateToken(user)))
        }

        route.post("/auth/login") {
            val req = call.receive<LoginRequest>()
            val user = userRepository.findByEmail(req.email)

            if (user == null || user.passwordHash == null || !BCrypt.checkpw(req.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            call.respond(AuthResponse(JwtConfig.generateToken(user)))
        }

        // ── Google OAuth ──────────────────────────────────────────────────────

        route.get("/auth/google") {
            val url = "https://accounts.google.com/o/oauth2/v2/auth?" + listOf(
                "client_id=${OAuthConfig.googleClientId}",
                "redirect_uri=${OAuthConfig.googleRedirectUri}",
                "response_type=code",
                "scope=openid email profile"
            ).joinToString("&")
            call.respondRedirect(url)
        }

        route.get("/auth/google/callback") {
            val code = call.request.queryParameters["code"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing code")

            val tokenResponse = httpClient.post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("code=$code&client_id=${OAuthConfig.googleClientId}&client_secret=${OAuthConfig.googleClientSecret}&redirect_uri=${OAuthConfig.googleRedirectUri}&grant_type=authorization_code")
            }.body<GoogleTokenResponse>()

            val googleUser = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                bearerAuth(tokenResponse.access_token)
            }.body<GoogleUserInfo>()

            val user = userRepository.findByProviderUserId(googleUser.id, AuthProvider.GOOGLE)
                ?: userRepository.save(
                    User(
                        username       = googleUser.name,
                        email          = googleUser.email,
                        authProvider   = AuthProvider.GOOGLE,
                        providerUserId = googleUser.id
                    )
                )

            call.respond(AuthResponse(JwtConfig.generateToken(user)))
        }

        // ── Discord OAuth ─────────────────────────────────────────────────────

        route.get("/auth/discord") {
            val url = "https://discord.com/api/oauth2/authorize?" + listOf(
                "client_id=${OAuthConfig.discordClientId}",
                "redirect_uri=${OAuthConfig.discordRedirectUri}",
                "response_type=code",
                "scope=identify email"
            ).joinToString("&")
            call.respondRedirect(url)
        }

        route.get("/auth/discord/callback") {
            val code = call.request.queryParameters["code"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing code")

            val tokenResponse = httpClient.post("https://discord.com/api/oauth2/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("code=$code&client_id=${OAuthConfig.discordClientId}&client_secret=${OAuthConfig.discordClientSecret}&redirect_uri=${OAuthConfig.discordRedirectUri}&grant_type=authorization_code")
            }.body<DiscordTokenResponse>()

            val discordUser = httpClient.get("https://discord.com/api/users/@me") {
                bearerAuth(tokenResponse.access_token)
            }.body<DiscordUserInfo>()

            val user = userRepository.findByProviderUserId(discordUser.id, AuthProvider.DISCORD)
                ?: userRepository.save(
                    User(
                        username       = discordUser.username,
                        email          = discordUser.email,
                        authProvider   = AuthProvider.DISCORD,
                        providerUserId = discordUser.id
                    )
                )

            call.respond(AuthResponse(JwtConfig.generateToken(user)))
        }

        // ── User profile ──────────────────────────────────────────────────────

        route.patch("/users/me/username") {
            val userId = Uuid.parse(call.userId)
            val req = call.receive<UpdateUsernameRequest>()

            if (userRepository.findByUsername(req.username) != null) {
                call.respond(HttpStatusCode.Conflict, "Username already taken")
                return@patch
            }

            val updated = userRepository.updateUsername(userId, req.username)
            call.respond(updated)
        }
    }
}