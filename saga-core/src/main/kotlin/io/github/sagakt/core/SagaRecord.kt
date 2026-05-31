package io.github.sagakt.core

import java.time.Instant

/**
 * Persistent representation of a saga's execution state. Storage adapters
 * round-trip this record to/from their backing store.
 *
 * Context is stored as an opaque byte payload (typically JSON) so storage
 * modules do not need to know the concrete context type.
 */
data class SagaRecord(
    val id: SagaId,
    val sagaName: String,
    val status: SagaStatus,
    val contextPayload: ByteArray,
    val contextType: String,
    val completedSteps: List<String>,
    val currentStepIndex: Int,
    val lastError: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SagaRecord) return false
        return id == other.id && version == other.version
    }

    override fun hashCode(): Int = 31 * id.hashCode() + version.hashCode()
}
