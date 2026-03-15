package com.otakuexchange.infra

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object RedisFactory {
    private val logger = LoggerFactory.getLogger(RedisFactory::class.java)
    private val url: String = System.getenv("REDIS_URL") ?: dotenv()["REDIS_URL"]

    lateinit var pool: JedisPool
        private set

    fun init() {
        logger.info("Initializing Redis connection pool...")
        pool = JedisPool(JedisPoolConfig().apply {
            maxTotal = 16
            maxIdle = 8
            minIdle = 2
            testOnBorrow = true
        }, url)
        logger.info("Redis connection pool initialized successfully")
    }
}