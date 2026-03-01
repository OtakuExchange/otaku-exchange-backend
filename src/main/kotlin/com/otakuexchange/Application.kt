package com.otakuexchange

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import com.otakuexchange.infra.DatabaseFactory
import com.otakuexchange.infra.repositories.NeonTopicRepository
import com.otakuexchange.domain.repositories.ITopicRepository

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
     // Initialize the database connection first
    DatabaseFactory.init()
    val topicRepository: ITopicRepository = NeonTopicRepository()

    configureRouting(topicRepository)
}
