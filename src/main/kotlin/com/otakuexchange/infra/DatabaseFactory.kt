package com.otakuexchange.infra

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.github.cdimascio.dotenv.dotenv
import java.sql.Connection

object DatabaseFactory {
    fun init() {
        val url = System.getenv("DATABASE_URL") ?: dotenv()["DATABASE_URL"]
        Database.connect(url, driver = "org.postgresql.Driver")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ
    }
}