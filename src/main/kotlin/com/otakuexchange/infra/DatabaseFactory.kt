package com.otakuexchange.infra

import org.slf4j.LoggerFactory
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.github.cdimascio.dotenv.dotenv
import java.sql.Connection


object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init() {
        logger.info("Initializing database connection...")
        val url = System.getenv("DATABASE_URL") ?: dotenv()["DATABASE_URL"]
        Database.connect(url, driver = "org.postgresql.Driver")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ
        logger.info("Database initialized successfully")
    }
}