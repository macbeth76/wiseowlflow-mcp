package com.wiseowlflow.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

class DatabaseConfig(
    private val jdbcUrl: String,
    private val username: String? = null,
    private val password: String? = null,
    private val maxPoolSize: Int = 10,
    private val minIdle: Int = 2
) {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    fun connect(): Database {
        logger.info { "Connecting to database..." }

        val config = HikariConfig().apply {
            this.jdbcUrl = this@DatabaseConfig.jdbcUrl
            this@DatabaseConfig.username?.let { this.username = it }
            this@DatabaseConfig.password?.let { this.password = it }
            this.maximumPoolSize = maxPoolSize
            this.minimumIdle = minIdle
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            this.poolName = "WiseOwlFlowPool"
            this.addDataSourceProperty("cachePrepStmts", "true")
            this.addDataSourceProperty("prepStmtCacheSize", "250")
            this.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        dataSource = HikariDataSource(config)
        database = Database.connect(dataSource)

        logger.info { "Database connected successfully" }
        return database
    }

    fun runMigrations() {
        logger.info { "Running database migrations..." }

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()

        val result = flyway.migrate()
        logger.info { "Applied ${result.migrationsExecuted} migrations" }
    }

    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
            logger.info { "Database connection closed" }
        }
    }

    companion object {
        fun fromEnvironment(): DatabaseConfig {
            val databaseUrl = System.getenv("DATABASE_URL")
                ?: "postgresql://wiseowlflow:wiseowlflow_dev@localhost:5432/wiseowlflow"

            // Parse DATABASE_URL if it's in postgres:// format
            val jdbcUrl = if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
                val uri = java.net.URI(databaseUrl.replace("postgres://", "postgresql://"))
                val userInfo = uri.userInfo?.split(":")
                val host = uri.host
                val port = if (uri.port > 0) uri.port else 5432
                val database = uri.path.removePrefix("/")

                "jdbc:postgresql://$host:$port/$database" +
                    (userInfo?.let { "?user=${it[0]}&password=${it.getOrElse(1) { "" }}" } ?: "")
            } else {
                databaseUrl
            }

            return DatabaseConfig(jdbcUrl)
        }
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction { block() }
