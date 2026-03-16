package com.otakuexchange

import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import com.otakuexchange.infra.DatabaseFactory
import com.otakuexchange.infra.RedisFactory
import com.otakuexchange.di.appModule
import com.otakuexchange.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()
    RedisFactory.init()

    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    configureExceptionHandling()
    configureAuth()
    configureRouting()
}