package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.repositories.IEntityRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateEntityRequest(
    val name: String,
    val abbreviatedName: String? = null,
    val logoPath: String,
    val color: String? = null,
    val pandaScoreId: Long? = null
)

class EntityController(
    private val entityRepository: IEntityRepository,
    private val userRepository: IUserRepository
) : IRouteController {

    private val devUserId: String? = runCatching {
        System.getenv("DEV_USER_ID") ?: dotenv()["DEV_USER_ID"]
    }.getOrNull()

    private suspend fun resolveAdmin(call: io.ktor.server.application.ApplicationCall): User? {
        val user = if (devUserId != null) {
            userRepository.findById(Uuid.parse(devUserId))
        } else {
            val clerkId = call.principal<JWTPrincipal>()?.payload?.subject ?: return null
            userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
        }
        return if (user?.isAdmin == true) user else null
    }

    override fun registerRoutes(route: Route) {
        route.get("/entities") {
            call.respond(entityRepository.getAll())
        }
    }

    override fun registerProtectedRoutes(route: Route) {
        route.post("/entities") {
            resolveAdmin(call) ?: return@post call.respond(HttpStatusCode.Forbidden, "Admin access required")
            val body = call.receive<CreateEntityRequest>()
            val entity = entityRepository.save(
                Entity(
                    name = body.name,
                    abbreviatedName = body.abbreviatedName,
                    logoPath = body.logoPath,
                    color = body.color,
                    pandaScoreId = body.pandaScoreId
                )
            )
            call.respond(HttpStatusCode.Created, entity)
        }
    }
}