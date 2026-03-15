package com.otakuexchange.plugins

import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

private val syncLogger = LoggerFactory.getLogger("ClerkUserSync")

val ClerkUserSync = createApplicationPlugin("ClerkUserSync") {
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>("clerk") ?: return@on
        val clerkId = principal.payload.subject ?: return@on
        val userRepository = call.application.get<IUserRepository>()

        try {
            if (userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK) == null) {
                val email    = principal.payload.getClaim("email").asString()
                    ?: "$clerkId@clerk.placeholder"
                val username = principal.payload.getClaim("username").asString()
                    ?: clerkId

                syncLogger.info("Creating new user for Clerk ID: $clerkId")
                userRepository.save(
                    User(
                        username       = username,
                        email          = email,
                        authProvider   = AuthProvider.CLERK,
                        providerUserId = clerkId,
                        balance        = 500L,
                    )
                )
                syncLogger.info("Created user for Clerk ID: $clerkId")
            }
        } catch (e: Exception) {
            syncLogger.error("Failed to sync Clerk user $clerkId: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
