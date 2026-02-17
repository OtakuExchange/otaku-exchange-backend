package com.otakuexchange.infra

import org.jetbrains.exposed.v1.jdbc.Database
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariConfig
import io.github.cdimascio.dotenv.dotenv

object DatabaseFactory {

    fun init() {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = System.getenv("DATABASE_URL") ?: dotenv()["DATABASE_URL"]
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)
    }
}
