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
private const val DAILY_REWARD = 2000L            // $20 in cents
private const val DAILY_REWARD_TTL = 86400L        // 24 hours in seconds

val ClerkUserSyncPlugin = createRouteScopedPlugin("ClerkUserSyncPlugin") {
    onCall { call ->
        val principal = call.principal<JWTPrincipal>() ?: return@onCall
        val clerkId = principal.payload.subject ?: return@onCall
        val userRepository = call.application.get<IUserRepository>()

        try {
            val existingUser = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)

            if (existingUser == null) {
                // New user — create with starting balance
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

                // Set daily reward key so they don't get double reward on first day
                RedisFactory.pool.getResource().use { jedis ->
                    jedis.setex("daily:reward:${newUser.id}", DAILY_REWARD_TTL, "1")
                }

            } else {
                // Existing user — check daily reward
                val rewardKey = "daily:reward:${existingUser.id}"
                val alreadyClaimed = RedisFactory.pool.getResource().use { jedis ->
                    jedis.exists(rewardKey)
                }

                if (!alreadyClaimed) {
                    userRepository.addBalance(existingUser.id, DAILY_REWARD)
                    RedisFactory.pool.getResource().use { jedis ->
                        jedis.setex(rewardKey, DAILY_REWARD_TTL, "1")
                    }
                    syncLogger.info("Daily reward of $${DAILY_REWARD / 100} awarded to user ${existingUser.id}")
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