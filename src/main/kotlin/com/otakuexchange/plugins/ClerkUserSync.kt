package com.otakuexchange.plugins

import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import com.otakuexchange.infra.RedisFactory
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

private val syncLogger = LoggerFactory.getLogger("ClerkUserSync")

private const val STARTING_BALANCE = 50000L       // $500 in cents

val ClerkUserSyncPlugin = createRouteScopedPlugin("ClerkUserSyncPlugin") {
    onCall { call ->
        val principal = call.principal<JWTPrincipal>() ?: return@onCall
        val clerkId = principal.payload.subject ?: return@onCall
        val userRepository = call.application.get<IUserRepository>()
        try {
            val existingUser = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
            if (existingUser == null) {
                val email = principal.payload.getClaim("email").asString()
                    ?: "$clerkId@clerk.placeholder"
                val username = principal.payload.getClaim("username").asString()
                    ?: clerkId
                syncLogger.info("Creating new user for Clerk ID: $clerkId")
                val newUser = userRepository.save(
                    User(
                        username = username,
                        email = email,
                        authProvider = AuthProvider.CLERK,
                        providerUserId = clerkId,
                        balance = STARTING_BALANCE
                    )
                )
                syncLogger.info("Created user ${newUser.id} for Clerk ID: $clerkId")
            } else {
                val avatarUrl = principal.payload.getClaim("avatarUrl").asString()
                if (avatarUrl != null && avatarUrl != existingUser.avatarUrl) {
                    userRepository.updateAvatarUrl(existingUser.id, avatarUrl)
                }
            }
        } catch (e: Exception) {
            syncLogger.error("Failed to sync Clerk user $clerkId: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}

fun Route.syncClerkUser() {
    install(ClerkUserSyncPlugin)
}