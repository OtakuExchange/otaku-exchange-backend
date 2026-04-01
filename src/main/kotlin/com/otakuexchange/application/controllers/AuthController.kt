package com.otakuexchange.application.controllers

import com.otakuexchange.plugins.clerkUserId
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.User
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class UpdateUsernameRequest(val username: String)

@Serializable
data class CurrentUserResponse(
    val id: String,
    val username: String,
    val email: String,
    val balance: Long,
    val lockedBalance: Long,
    val isAdmin: Boolean
)

class AuthController(
    private val userRepository: IUserRepository
) : IRouteController {

    private companion object {
        private const val STARTING_BALANCE = 50000L // $500 in cents
    }

    private suspend fun ensureClerkUser(call: io.ktor.server.application.ApplicationCall): User {
        val clerkId = call.clerkUserId
        val existing = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
        val payload = call.principal<JWTPrincipal>()?.payload
        val avatarUrl = payload?.getClaim("avatarUrl")?.asString()

        if (existing == null) {
            val email = payload?.getClaim("email")?.asString() ?: "$clerkId@clerk.placeholder"
            val username = payload?.getClaim("username")?.asString() ?: clerkId
            return userRepository.save(
                User(
                    username = username,
                    email = email,
                    authProvider = AuthProvider.CLERK,
                    providerUserId = clerkId,
                    balance = STARTING_BALANCE,
                    avatarUrl = avatarUrl
                )
            )
        }

        if (avatarUrl != null && avatarUrl != existing.avatarUrl) {
            userRepository.updateAvatarUrl(existing.id, avatarUrl)
        }

        return existing
    }

    override fun registerRoutes(route: Route) {
        // no public routes
    }

    override fun registerProtectedRoutes(route: Route) {
        route.get("/users/me") {
            val user = ensureClerkUser(call)
            call.respond(
                CurrentUserResponse(
                    id = user.id.toString(),
                    username = user.username,
                    email = user.email,
                    balance = user.balance,
                    lockedBalance = user.lockedBalance,
                    isAdmin = user.isAdmin
                )
            )
        }

        route.patch("/users/me/username") {
            val req = call.receive<UpdateUsernameRequest>()
            val user = ensureClerkUser(call)
            if (userRepository.findByUsername(req.username) != null) {
                call.respond(HttpStatusCode.Conflict, "Username already taken")
                return@patch
            }
            val updated = userRepository.updateUsername(user.id, req.username)
            call.respond(updated)
        }
    }
}