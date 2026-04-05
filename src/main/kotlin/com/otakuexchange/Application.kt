package com.otakuexchange

import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.coroutines.launch
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.koin.ktor.ext.get
import com.otakuexchange.infra.DatabaseFactory
import com.otakuexchange.infra.RedisFactory
import com.otakuexchange.di.appModule
import com.otakuexchange.domain.services.EventSchedulerService
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
    configureCors()
    configureRouting()

    // Schedule auto-close for all open and hidden events on startup
    val scheduler = get<EventSchedulerService>()
    launch {
        println("Scheduling auto-close for all open and hidden events on startup...")
        scheduler.scheduleAll()
    }
}