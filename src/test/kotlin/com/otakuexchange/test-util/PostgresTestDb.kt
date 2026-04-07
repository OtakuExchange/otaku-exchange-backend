package com.otakuexchange.testutil

import com.otakuexchange.infra.tables.BookmarkTable
import com.otakuexchange.infra.tables.CommentLikeTable
import com.otakuexchange.infra.tables.CommentTable
import com.otakuexchange.infra.tables.EntityTable
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.EventSubtopicTable
import com.otakuexchange.infra.tables.MarketTable
import com.otakuexchange.infra.tables.SubtopicTable
import com.otakuexchange.infra.tables.TopicTable
import com.otakuexchange.infra.tables.UserEventViewTable
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import com.otakuexchange.infra.tables.DailyStreakTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestDb {
    fun connect(postgres: PostgreSQLContainer<*>): Database {
        return Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
    }

    fun createSchema(db: Database) {
        transaction(db) {
            SchemaUtils.create(
                TopicTable,
                SubtopicTable,
                EventTable,
                EventSubtopicTable,
                UserTable,
                UserEventViewTable,
                EntityTable,
                BookmarkTable,
                CommentTable,
                CommentLikeTable,
                MarketTable,
                MarketPoolTable,
                StakeTable,
                DailyStreakTable
            )
        }
    }

    fun truncateAll(db: Database) {
        transaction(db) {
            exec(
                """
                TRUNCATE TABLE
                  markets,
                  comment_likes,
                  comments,
                  bookmarks,
                  user_event_views,
                  stakes,
                  market_pools,
                  entities,
                  event_subtopics,
                  subtopics,
                  events,
                  topics,
                  users,
                  daily_streaks
                CASCADE
                """.trimIndent()
            )
        }
    }
}

