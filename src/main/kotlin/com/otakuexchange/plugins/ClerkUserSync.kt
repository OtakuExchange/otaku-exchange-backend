package com.otakuexchange.plugins

import com.auth0.jwt.interfaces.Payload
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import org.slf4j.LoggerFactory

private val syncLogger = LoggerFactory.getLogger("ClerkUserSync")

private const val STARTING_BALANCE = 50000L       // $500 in cents

suspend fun syncClerkUser(payload: Payload, userRepository: IUserRepository) {
    val clerkId = payload.subject ?: return
    try {
        val existingUser = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
        if (existingUser == null) {
            val email = payload.getClaim("email").asString() ?: "$clerkId@clerk.placeholder"
            val username = payload.getClaim("username").asString() ?: clerkId
            val avatarUrl = payload.getClaim("avatarUrl").asString()
            syncLogger.info("Creating new user for Clerk ID: $clerkId")
            val newUser = userRepository.save(
                User(
                    username = username,
                    email = email,
                    authProvider = AuthProvider.CLERK,
                    providerUserId = clerkId,
                    balance = STARTING_BALANCE,
                    avatarUrl = avatarUrl
                )
            )
            syncLogger.info("Created user ${newUser.id} for Clerk ID: $clerkId")
        } else {
            val avatarUrl = payload.getClaim("avatarUrl").asString()
            if (avatarUrl != null && avatarUrl != existingUser.avatarUrl) {
                userRepository.updateAvatarUrl(existingUser.id, avatarUrl)
            }
        }
    } catch (e: Exception) {
        syncLogger.error("Failed to sync Clerk user $clerkId: ${e.javaClass.simpleName}: ${e.message}", e)
    }
}
