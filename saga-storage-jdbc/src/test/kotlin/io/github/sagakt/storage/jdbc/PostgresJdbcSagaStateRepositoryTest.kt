package io.github.sagakt.storage.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Repository contract verified against a real PostgreSQL via testcontainers.
 *
 * Behaviour matrix:
 * - Docker reachable -> container starts and the full contract suite runs.
 * - Docker missing / unreachable -> the spec registers a single skipped test
 *   and the build stays green. Set `SAGA_REQUIRE_DOCKER=1` (CI does) to instead
 *   fail loudly so the contract is never silently bypassed.
 */
class PostgresJdbcSagaStateRepositoryTest : StringSpec({
    val requireDocker = System.getenv("SAGA_REQUIRE_DOCKER") == "1"

    val container = runCatching {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("saga")
            .withUsername("saga")
            .withPassword("saga")
            .also { it.start() }
    }

    if (container.isFailure) {
        val err = container.exceptionOrNull()
        if (requireDocker) {
            throw IllegalStateException(
                "SAGA_REQUIRE_DOCKER=1 but Docker is not reachable: ${err?.message}",
                err,
            )
        }
        "[postgres] suite skipped: Docker not reachable (${err?.javaClass?.simpleName}: ${err?.message})"
            .config(enabled = false) { }
    } else {
        val pg = container.getOrThrow()
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = pg.jdbcUrl
                username = pg.username
                password = pg.password
                driverClassName = pg.driverClassName
                maximumPoolSize = 4
            },
        )

        afterSpec {
            ds.close()
            pg.stop()
        }

        jdbcContractTests("postgres", SqlDialect.POSTGRESQL) { ds }
    }
})
