package io.github.sagakt.core

/**
 * Storage contract for saga execution state. Implementations are expected to
 * provide optimistic locking via [SagaRecord.version]: a save with a stale
 * version must throw [OptimisticLockException].
 *
 * All methods are suspending so non-blocking drivers (R2DBC, reactive Redis)
 * can implement the contract without thread blocking.
 */
interface SagaStateRepository {
    /** Insert a new record. Throws if a record with the same id already exists. */
    suspend fun insert(record: SagaRecord): SagaRecord

    /** Update an existing record. Throws [OptimisticLockException] on version mismatch. */
    suspend fun update(record: SagaRecord): SagaRecord

    suspend fun findById(id: SagaId): SagaRecord?

    /**
     * Returns records in resumable states ([SagaStatus.isResumable]). Used by the
     * resume worker on startup to pick up sagas that were interrupted.
     */
    suspend fun findResumable(sagaName: String? = null, limit: Int = 100): List<SagaRecord>
}

class OptimisticLockException(id: SagaId, expectedVersion: Long) :
    RuntimeException("Saga $id was modified by another writer (expected version $expectedVersion)")

class SagaNotFoundException(id: SagaId) :
    RuntimeException("Saga $id not found")
