package io.github.sagakt.storage.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.sagakt.core.OptimisticLockException
import io.github.sagakt.core.SagaId
import io.github.sagakt.core.SagaRecord
import io.github.sagakt.core.SagaStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.sql.DataSource

class JdbcSagaStateRepositoryTest : StringSpec({
    fun newDb(): DataSource {
        val cfg = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:saga-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 4
        }
        return HikariDataSource(cfg)
    }

    fun newRepo(): JdbcSagaStateRepository {
        val repo = JdbcSagaStateRepository(newDb())
        repo.initializeSchema()
        return repo
    }

    fun newRecord(id: SagaId = SagaId.random(), version: Long = 0, status: SagaStatus = SagaStatus.RUNNING): SagaRecord {
        val now = Instant.now()
        return SagaRecord(
            id = id,
            sagaName = "demo",
            status = status,
            contextPayload = """{"x":1}""".toByteArray(),
            contextType = "demo.Ctx",
            completedSteps = listOf("a"),
            currentStepIndex = 1,
            lastError = null,
            createdAt = now,
            updatedAt = now,
            version = version,
        )
    }

    "insert then findById round-trips a record" {
        val repo = newRepo()
        val rec = newRecord()
        runBlocking { repo.insert(rec) }
        val found = runBlocking { repo.findById(rec.id) }
        found.shouldNotBeNull()
        found.sagaName shouldBe "demo"
        found.completedSteps shouldBe listOf("a")
        found.contextPayload.decodeToString() shouldBe """{"x":1}"""
    }

    "update with stale version throws OptimisticLockException" {
        val repo = newRepo()
        val rec = newRecord()
        runBlocking { repo.insert(rec) }
        runBlocking { repo.update(rec.copy(version = 1, status = SagaStatus.COMPLETED)) }
        shouldThrow<OptimisticLockException> {
            runBlocking { repo.update(rec.copy(version = 1, status = SagaStatus.RUNNING)) }
        }
    }

    "findResumable returns only resumable statuses" {
        val repo = newRepo()
        runBlocking {
            repo.insert(newRecord(status = SagaStatus.RUNNING))
            repo.insert(newRecord(status = SagaStatus.COMPLETED))
            repo.insert(newRecord(status = SagaStatus.COMPENSATING))
            repo.insert(newRecord(status = SagaStatus.COMPENSATED))
        }
        val resumable = runBlocking { repo.findResumable(limit = 100) }
        resumable shouldHaveSize 2
    }

    "findResumable filters by saga name" {
        val repo = newRepo()
        runBlocking {
            repo.insert(newRecord(status = SagaStatus.RUNNING).copy(sagaName = "a"))
            repo.insert(newRecord(status = SagaStatus.RUNNING).copy(sagaName = "b"))
        }
        runBlocking { repo.findResumable(sagaName = "a", limit = 100) } shouldHaveSize 1
    }
})
