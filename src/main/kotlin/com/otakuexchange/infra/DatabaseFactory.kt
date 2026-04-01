package com.otakuexchange.infra

import org.slf4j.LoggerFactory
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.github.cdimascio.dotenv.dotenv
import java.sql.Connection

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource


object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private var dataSource: HikariDataSource? = null

    fun init() {
        logger.info("Initializing database connection...")
        val env = System.getenv("DATABASE_URL")
        val url = env ?: dotenv()["DATABASE_URL"]
        val maxPoolSize = (System.getenv("DB_MAX_POOL_SIZE") ?: dotenv()["DB_MAX_POOL_SIZE"])
            ?.toIntOrNull()
            ?.coerceIn(1, 50)
            ?: 10
        val minIdle = (System.getenv("DB_MIN_IDLE") ?: dotenv()["DB_MIN_IDLE"])
            ?.toIntOrNull()
            ?.coerceIn(0, maxPoolSize)
            ?: 2
        val ds = dataSource ?: HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = maxPoolSize
            minimumIdle = minIdle
            connectionTimeout = 10_000
            idleTimeout = 600_000
            keepaliveTime = 60_000
            isAutoCommit = false
            poolName = "OtakuExchangeHikari"
        }).also { dataSource = it }

        Database.connect(ds)
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ
        logger.info("Database initialized successfully")
    }
}