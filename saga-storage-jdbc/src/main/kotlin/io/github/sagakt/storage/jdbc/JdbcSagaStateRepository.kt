package io.github.sagakt.storage.jdbc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.sagakt.core.OptimisticLockException
import io.github.sagakt.core.SagaId
import io.github.sagakt.core.SagaNotFoundException
import io.github.sagakt.core.SagaRecord
import io.github.sagakt.core.SagaStateRepository
import io.github.sagakt.core.SagaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * JDBC-backed [SagaStateRepository]. The repository owns a [DataSource] and
 * runs all blocking JDBC calls on [Dispatchers.IO] so it stays suspending-safe
 * from non-blocking event loops.
 *
 * The schema is in `schema.sql` on the classpath; call [initializeSchema] once
 * at startup (the Spring Boot starter does this automatically), or run the SQL
 * via your own migration tooling.
 */
class JdbcSagaStateRepository(
    private val dataSource: DataSource,
    private val mapper: ObjectMapper = JacksonCodecRegistry.defaultMapper(),
    private val tableName: String = "saga_state",
) : SagaStateRepository {

    fun initializeSchema() {
        val sql = JdbcSagaStateRepository::class.java
            .getResource("/io/github/sagakt/storage/jdbc/schema.sql")
            ?.readText()
            ?.let { if (tableName == "saga_state") it else it.replace("saga_state", tableName) }
            ?: error("schema.sql not found on classpath")
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                for (stmt in sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
                    st.execute(stmt)
                }
            }
        }
    }

    override suspend fun insert(record: SagaRecord): SagaRecord = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO $tableName
                  (id, saga_name, status, context_payload, context_type,
                   completed_steps, current_step_index, last_error,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                bindRecord(ps, record)
                ps.executeUpdate()
            }
            record
        }
    }

    override suspend fun update(record: SagaRecord): SagaRecord = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE $tableName SET
                  status = ?, context_payload = ?, context_type = ?,
                  completed_steps = ?, current_step_index = ?, last_error = ?,
                  updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, record.status.name)
                ps.setBytes(2, record.contextPayload)
                ps.setString(3, record.contextType)
                ps.setString(4, mapper.writeValueAsString(record.completedSteps))
                ps.setInt(5, record.currentStepIndex)
                ps.setString(6, record.lastError)
                ps.setTimestamp(7, Timestamp.from(record.updatedAt))
                ps.setLong(8, record.version)
                ps.setString(9, record.id.value)
                ps.setLong(10, record.version - 1)
                val rows = ps.executeUpdate()
                if (rows == 0) {
                    val existing = findByIdBlocking(conn, record.id)
                        ?: throw SagaNotFoundException(record.id)
                    throw OptimisticLockException(record.id, existing.version)
                }
            }
            record
        }
    }

    override suspend fun findById(id: SagaId): SagaRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn -> findByIdBlocking(conn, id) }
    }

    override suspend fun findResumable(sagaName: String?, limit: Int): List<SagaRecord> =
        withContext(Dispatchers.IO) {
            val statuses = SagaStatus.entries.filter { it.isResumable }
            val placeholders = statuses.joinToString(",") { "?" }
            val sql = buildString {
                append("SELECT * FROM $tableName WHERE status IN ($placeholders)")
                if (sagaName != null) append(" AND saga_name = ?")
                append(" ORDER BY updated_at ASC")
                append(" LIMIT ?")
            }
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    var idx = 1
                    statuses.forEach { ps.setString(idx++, it.name) }
                    if (sagaName != null) ps.setString(idx++, sagaName)
                    ps.setInt(idx, limit)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) add(mapRow(rs))
                        }
                    }
                }
            }
        }

    private fun findByIdBlocking(conn: Connection, id: SagaId): SagaRecord? {
        return conn.prepareStatement("SELECT * FROM $tableName WHERE id = ?").use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (rs.next()) mapRow(rs) else null
            }
        }
    }

    private fun bindRecord(ps: PreparedStatement, record: SagaRecord) {
        ps.setString(1, record.id.value)
        ps.setString(2, record.sagaName)
        ps.setString(3, record.status.name)
        ps.setBytes(4, record.contextPayload)
        ps.setString(5, record.contextType)
        ps.setString(6, mapper.writeValueAsString(record.completedSteps))
        ps.setInt(7, record.currentStepIndex)
        ps.setString(8, record.lastError)
        ps.setTimestamp(9, Timestamp.from(record.createdAt))
        ps.setTimestamp(10, Timestamp.from(record.updatedAt))
        ps.setLong(11, record.version)
    }

    @Suppress("LongMethod")
    private fun mapRow(rs: ResultSet): SagaRecord {
        val completedJson = rs.getString("completed_steps") ?: "[]"
        val completed: List<String> = mapper.readValue(completedJson)
        return SagaRecord(
            id = SagaId(rs.getString("id")),
            sagaName = rs.getString("saga_name"),
            status = SagaStatus.valueOf(rs.getString("status")),
            contextPayload = rs.getBytes("context_payload"),
            contextType = rs.getString("context_type"),
            completedSteps = completed,
            currentStepIndex = rs.getInt("current_step_index"),
            lastError = rs.getString("last_error"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version"),
        )
    }
}
