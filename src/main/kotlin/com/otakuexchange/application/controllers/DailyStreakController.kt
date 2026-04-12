package com.otakuexchange.application.controllers

import com.otakuexchange.domain.repositories.IDailyStreakRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.IBalanceTransactionRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.plugins.clerkUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.otakuexchange.domain.BalanceTransactionType

class DailyStreakController(
    private val streakRepository: IDailyStreakRepository,
    private val userRepository: IUserRepository,
    private val balanceTransactionRepository: IBalanceTransactionRepository
) : IRouteController {

    override fun registerRoutes(route: Route) {}

    override fun registerProtectedRoutes(route: Route) {

        route.get("/rewards/daily") {
            val clerkId = call.clerkUserId
            val user = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
                ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
            val status = streakRepository.getStatus(user.id)
            call.respond(status)
        }

        route.post("/rewards/daily/claim") {
            val clerkId = call.clerkUserId
            val user = userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
                ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

            val result = runCatching {
                streakRepository.claim(user.id)
            }.getOrElse { e ->
                return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Claim failed")
            }

            val totalReward = result.rewardCents + result.comebackBonusCents
            val updatedUser = userRepository.findById(user.id)
            if (updatedUser != null) {
                balanceTransactionRepository.record(
                    userId       = user.id,
                    amount       = totalReward,
                    balanceAfter = updatedUser.balance,
                    type         = BalanceTransactionType.DAILY_REWARD,
                    referenceId  = null
                )
            }

            call.respond(result)
        }
    }
}