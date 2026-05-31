package io.github.sagakt.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySagaStateRepository : SagaStateRepository {
    private val mutex = Mutex()
    private val store = LinkedHashMap<SagaId, SagaRecord>()

    override suspend fun insert(record: SagaRecord): SagaRecord = mutex.withLock {
        require(record.id !in store) { "Saga ${record.id} already exists" }
        store[record.id] = record
        record
    }

    override suspend fun update(record: SagaRecord): SagaRecord = mutex.withLock {
        val current = store[record.id] ?: throw SagaNotFoundException(record.id)
        if (current.version != record.version - 1) {
            throw OptimisticLockException(record.id, current.version)
        }
        store[record.id] = record
        record
    }

    override suspend fun findById(id: SagaId): SagaRecord? = mutex.withLock { store[id] }

    override suspend fun findResumable(sagaName: String?, limit: Int): List<SagaRecord> = mutex.withLock {
        store.values.asSequence()
            .filter { it.status.isResumable }
            .filter { sagaName == null || it.sagaName == sagaName }
            .take(limit)
            .toList()
    }

    suspend fun snapshot(): List<SagaRecord> = mutex.withLock { store.values.toList() }

    suspend fun clear(): Unit = mutex.withLock { store.clear() }
}
