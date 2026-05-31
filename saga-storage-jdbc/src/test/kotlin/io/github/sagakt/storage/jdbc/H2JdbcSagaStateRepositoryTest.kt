package io.github.sagakt.storage.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec

class H2JdbcSagaStateRepositoryTest : StringSpec({
    val ds = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:saga-h2-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 4
        },
    )
    afterSpec { ds.close() }

    jdbcContractTests("h2", SqlDialect.H2) { ds }
})
