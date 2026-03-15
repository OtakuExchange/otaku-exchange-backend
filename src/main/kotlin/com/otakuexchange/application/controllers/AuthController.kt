package com.otakuexchange.application.controllers

import com.otakuexchange.plugins.clerkUserId
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.repositories.IUserRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class UpdateUsernameRequest(val username: String)

class AuthController(
    private val userRepository: IUserRepository
) : IRouteController {

    override fun registerRoutes(route: Route) {
        route.authenticate("clerk") {
            patch("/users/me/username") {
                val clerkId = call.clerkUserId
                val req = call.receive<UpdateUsernameRequest>()

                val user = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, "User not found")

                if (userRepository.findByUsername(req.username) != null) {
                    call.respond(HttpStatusCode.Conflict, "Username already taken")
                    return@patch
                }

                val updated = userRepository.updateUsername(user.id, req.username)
                call.respond(updated)
            }
        }
    }
}
