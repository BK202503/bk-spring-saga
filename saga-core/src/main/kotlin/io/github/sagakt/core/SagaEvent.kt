package io.github.sagakt.core

import java.time.Instant

/**
 * Lifecycle event emitted by the [SagaExecutor] at each saga/step boundary.
 *
 * Wire a [SagaEventPublisher] (e.g. the Kafka one in `saga-events-kafka`) to
 * forward these to a message broker so downstream services can react to saga
 * outcomes without polling the state table.
 */
sealed class SagaEvent {
    abstract val sagaId: SagaId
    abstract val sagaName: String
    abstract val occurredAt: Instant
    val type: String get() = this::class.simpleName ?: "SagaEvent"

    data class Started(
        override val sagaId: SagaId,
        override val sagaName: String,
        override val occurredAt: Instant,
    ) : SagaEvent()

    data class StepCompleted(
        override val sagaId: SagaId,
        override val sagaName: String,
        override val occurredAt: Instant,
        val stepName: String,
        val attempts: Int,
    ) : SagaEvent()

    data class StepFailed(
        override val sagaId: SagaId,
        override val sagaName: String,
        override val occurredAt: Instant,
        val stepName: String,
        val errorType: String,
        val errorMessage: String?,
    ) : SagaEvent()

    data class Completed(
        override val sagaId: SagaId,
        override val sagaName: String,
        override val occurredAt: Instant,
    ) : SagaEvent()

    data class Compensated(
        override val sagaId: SagaId,
        override val sagaName: String,
        override val occurredAt: Instant,
        val failedStep: String,
        val causeType: String,
        val causeMessage: String?,
    ) : SagaEvent()

    data class CompensationFailed(
        override val sagaId: SagaId,
        override val sagaName: String,
        override val occurredAt: Instant,
        val compensationStep: String,
        val errorType: String,
        val errorMessage: String?,
    ) : SagaEvent()
}

interface SagaEventPublisher {
    suspend fun publish(event: SagaEvent)

    companion object {
        val NoOp: SagaEventPublisher = object : SagaEventPublisher {
            override suspend fun publish(event: SagaEvent) {}
        }
    }
}
