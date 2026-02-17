package com

import io.ktor.server.application.*
import com.otakuexchange.infra.DatabaseFactory

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
     // Initialize the database connection first
    DatabaseFactory.init()

    configureRouting()
}
