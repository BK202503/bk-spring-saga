package io.github.sagakt.storage.jdbc

import io.github.sagakt.core.OptimisticLockException
import io.github.sagakt.core.SagaId
import io.github.sagakt.core.SagaRecord
import io.github.sagakt.core.SagaStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.scopes.StringSpecRootScope
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.sql.DataSource

/**
 * Shared test contract for [JdbcSagaStateRepository] implementations. Both the
 * H2 and PostgreSQL specs call [jdbcContractTests] to inherit the same suite,
 * so anything that passes against H2 must also pass against real Postgres.
 */
internal fun StringSpecRootScope.jdbcContractTests(
    label: String,
    dialect: SqlDialect,
    dataSource: () -> DataSource,
) {
    fun cleanRepo(): JdbcSagaStateRepository {
        val ds = dataSource()
        ds.connection.use { c ->
            c.createStatement().use { st -> st.execute("DROP TABLE IF EXISTS saga_state") }
        }
        val repo = JdbcSagaStateRepository(ds, dialect = dialect)
        repo.initializeSchema()
        return repo
    }

    "[$label] dialect auto-detect matches explicit dialect" {
        val ds = dataSource()
        SqlDialect.detect(ds) shouldBe dialect
    }

    "[$label] insert then findById round-trips a record" {
        val repo = cleanRepo()
        val rec = newRecord()
        runBlocking { repo.insert(rec) }
        val found = runBlocking { repo.findById(rec.id) }
        found.shouldNotBeNull()
        found.sagaName shouldBe "demo"
        found.completedSteps shouldBe listOf("a")
        found.contextPayload.decodeToString() shouldBe """{"x":1}"""
    }

    "[$label] update with stale version throws OptimisticLockException" {
        val repo = cleanRepo()
        val rec = newRecord()
        runBlocking { repo.insert(rec) }
        runBlocking { repo.update(rec.copy(version = 1, status = SagaStatus.COMPLETED)) }
        shouldThrow<OptimisticLockException> {
            runBlocking { repo.update(rec.copy(version = 1, status = SagaStatus.RUNNING)) }
        }
    }

    "[$label] findResumable returns only resumable statuses" {
        val repo = cleanRepo()
        runBlocking {
            repo.insert(newRecord(status = SagaStatus.RUNNING))
            repo.insert(newRecord(status = SagaStatus.COMPLETED))
            repo.insert(newRecord(status = SagaStatus.COMPENSATING))
            repo.insert(newRecord(status = SagaStatus.COMPENSATED))
        }
        runBlocking { repo.findResumable(limit = 100) } shouldHaveSize 2
    }

    "[$label] findResumable filters by saga name" {
        val repo = cleanRepo()
        runBlocking {
            repo.insert(newRecord(status = SagaStatus.RUNNING).copy(sagaName = "a"))
            repo.insert(newRecord(status = SagaStatus.RUNNING).copy(sagaName = "b"))
        }
        runBlocking { repo.findResumable(sagaName = "a", limit = 100) } shouldHaveSize 1
    }

    "[$label] preserves a large binary payload exactly" {
        val repo = cleanRepo()
        val bytes = ByteArray(64 * 1024) { (it % 251).toByte() }
        val rec = newRecord().copy(contextPayload = bytes)
        runBlocking { repo.insert(rec) }
        val found = runBlocking { repo.findById(rec.id) }
        found!!.contextPayload.size shouldBe bytes.size
        found.contextPayload.contentEquals(bytes) shouldBe true
    }
}

internal fun newRecord(
    id: SagaId = SagaId.random(),
    version: Long = 0,
    status: SagaStatus = SagaStatus.RUNNING,
): SagaRecord {
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

