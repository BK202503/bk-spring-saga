package io.github.sagakt.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * Drives the execution of a [SagaDefinition]. The executor is stateless; all
 * state lives in the [SagaStateRepository], which makes the executor safe to
 * share across coroutines and across requests.
 *
 * On each step boundary the executor persists progress, so a JVM crash mid-saga
 * leaves a resumable record that [resume] can pick up.
 */
class SagaExecutor(
    private val repository: SagaStateRepository,
    private val codecRegistry: SagaCodecRegistry,
    private val clock: Clock = Clock.systemUTC(),
    private val metrics: SagaMetrics = SagaMetrics.NoOp,
    private val eventPublisher: SagaEventPublisher = SagaEventPublisher.NoOp,
) {
    private val log = LoggerFactory.getLogger(SagaExecutor::class.java)

    private suspend fun emit(event: SagaEvent) {
        try {
            eventPublisher.publish(event)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            log.warn("event publish failed for {} id={}", event.type, event.sagaId, t)
        }
    }

    suspend fun <Ctx : Any> execute(
        definition: SagaDefinition<Ctx>,
        initial: Ctx,
        id: SagaId = SagaId.random(),
    ): SagaResult<Ctx> {
        val codec = codecRegistry.codecFor(definition)
        val now = Instant.now(clock)
        val record = SagaRecord(
            id = id,
            sagaName = definition.name,
            status = SagaStatus.RUNNING,
            contextPayload = codec.encode(initial),
            contextType = definition.contextType.qualifiedName ?: definition.contextType.java.name,
            completedSteps = emptyList(),
            currentStepIndex = 0,
            createdAt = now,
            updatedAt = now,
            version = 0,
        )
        val saved = repository.insert(record)
        metrics.sagaStarted(definition.name)
        emit(SagaEvent.Started(saved.id, definition.name, now))
        log.info("saga {} started id={}", definition.name, saved.id)
        return runForward(definition, codec, saved, initial)
    }

    suspend fun <Ctx : Any> resume(definition: SagaDefinition<Ctx>, id: SagaId): SagaResult<Ctx> {
        val record = repository.findById(id) ?: throw SagaNotFoundException(id)
        require(record.sagaName == definition.name) {
            "Saga $id is '${record.sagaName}', not '${definition.name}'"
        }
        val codec = codecRegistry.codecFor(definition)
        val context = codec.decode(record.contextPayload)
        log.info("saga {} resuming id={} status={}", definition.name, id, record.status)
        return when (record.status) {
            SagaStatus.RUNNING -> runForward(definition, codec, record, context)
            SagaStatus.COMPENSATING -> runCompensation(
                definition, codec, record, context,
                cause = RuntimeException(record.lastError ?: "Resumed compensation"),
                failedStep = inferFailedStep(definition, record),
            )
            SagaStatus.COMPLETED -> SagaResult.Completed(record.id, context)
            SagaStatus.COMPENSATED -> SagaResult.Compensated(
                record.id, context,
                cause = RuntimeException(record.lastError ?: "Already compensated"),
                failedStep = inferFailedStep(definition, record),
            )
            SagaStatus.COMPENSATION_FAILED -> SagaResult.CompensationFailed(
                record.id, context,
                compensationError = RuntimeException(record.lastError ?: "Compensation failed"),
                compensationStep = "unknown",
                originalCause = RuntimeException("Resumed in COMPENSATION_FAILED"),
                originalFailedStep = inferFailedStep(definition, record),
            )
        }
    }

    private fun <Ctx : Any> inferFailedStep(definition: SagaDefinition<Ctx>, record: SagaRecord): String =
        definition.steps.getOrNull(record.currentStepIndex)?.name ?: "unknown"

    private suspend fun <Ctx : Any> runForward(
        definition: SagaDefinition<Ctx>,
        codec: SagaContextCodec<Ctx>,
        initialRecord: SagaRecord,
        initialContext: Ctx,
    ): SagaResult<Ctx> {
        var record = initialRecord
        var context = initialContext

        for (i in record.currentStepIndex until definition.steps.size) {
            val step = definition.steps[i]
            val outcome = executeStepWithRetry(step, context)
            when (outcome) {
                is StepOutcome.Success -> {
                    context = outcome.context
                    val isLast = i + 1 == definition.steps.size
                    record = record.copy(
                        contextPayload = codec.encode(context),
                        completedSteps = record.completedSteps + step.name,
                        currentStepIndex = i + 1,
                        status = if (isLast) SagaStatus.COMPLETED else SagaStatus.RUNNING,
                        lastError = null,
                        updatedAt = Instant.now(clock),
                        version = record.version + 1,
                    )
                    record = repository.update(record)
                    metrics.stepCompleted(definition.name, step.name, outcome.attempts)
                    emit(
                        SagaEvent.StepCompleted(
                            record.id, definition.name, record.updatedAt,
                            step.name, outcome.attempts,
                        ),
                    )
                }
                is StepOutcome.Failure -> {
                    record = record.copy(
                        status = SagaStatus.COMPENSATING,
                        lastError = formatError(outcome.cause),
                        updatedAt = Instant.now(clock),
                        version = record.version + 1,
                    )
                    record = repository.update(record)
                    metrics.stepFailed(definition.name, step.name, outcome.cause)
                    emit(
                        SagaEvent.StepFailed(
                            record.id, definition.name, record.updatedAt,
                            step.name,
                            outcome.cause::class.qualifiedName ?: outcome.cause::class.java.name,
                            outcome.cause.message,
                        ),
                    )
                    log.warn(
                        "saga {} step {} failed; entering compensation id={}",
                        definition.name, step.name, record.id, outcome.cause,
                    )
                    return runCompensation(definition, codec, record, context, outcome.cause, step.name)
                }
            }
        }

        try {
            definition.onComplete?.invoke(context)
        } catch (t: Throwable) {
            log.warn("saga {} onComplete callback threw id={}", definition.name, record.id, t)
        }
        metrics.sagaCompleted(definition.name)
        emit(SagaEvent.Completed(record.id, definition.name, Instant.now(clock)))
        log.info("saga {} completed id={}", definition.name, record.id)
        return SagaResult.Completed(record.id, context)
    }

    private suspend fun <Ctx : Any> runCompensation(
        definition: SagaDefinition<Ctx>,
        codec: SagaContextCodec<Ctx>,
        initialRecord: SagaRecord,
        context: Ctx,
        cause: Throwable,
        failedStep: String,
    ): SagaResult<Ctx> {
        var record = initialRecord

        val completedIndices = record.completedSteps.mapNotNull { name ->
            definition.steps.indexOfFirst { it.name == name }.takeIf { it >= 0 }
        }
        for (i in completedIndices.reversed()) {
            val step = definition.steps[i]
            val compensate = step.compensate ?: continue
            try {
                compensate(context)
                metrics.compensationCompleted(definition.name, step.name)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                record = record.copy(
                    status = SagaStatus.COMPENSATION_FAILED,
                    lastError = "Compensation of '${step.name}' failed: ${formatError(t)}",
                    updatedAt = Instant.now(clock),
                    version = record.version + 1,
                )
                record = repository.update(record)
                metrics.compensationFailed(definition.name, step.name, t)
                metrics.sagaCompensationFailed(definition.name)
                emit(
                    SagaEvent.CompensationFailed(
                        record.id, definition.name, record.updatedAt,
                        step.name,
                        t::class.qualifiedName ?: t::class.java.name,
                        t.message,
                    ),
                )
                log.error(
                    "saga {} compensation failed for step {} id={}",
                    definition.name, step.name, record.id, t,
                )
                return SagaResult.CompensationFailed(
                    id = record.id,
                    context = context,
                    compensationError = t,
                    compensationStep = step.name,
                    originalCause = cause,
                    originalFailedStep = failedStep,
                )
            }
        }

        record = record.copy(
            status = SagaStatus.COMPENSATED,
            updatedAt = Instant.now(clock),
            version = record.version + 1,
        )
        record = repository.update(record)
        try {
            definition.onFailure?.invoke(context, cause)
        } catch (t: Throwable) {
            log.warn("saga {} onFailure callback threw id={}", definition.name, record.id, t)
        }
        metrics.sagaCompensated(definition.name)
        emit(
            SagaEvent.Compensated(
                record.id, definition.name, Instant.now(clock),
                failedStep,
                cause::class.qualifiedName ?: cause::class.java.name,
                cause.message,
            ),
        )
        log.info("saga {} compensated id={}", definition.name, record.id)
        return SagaResult.Compensated(record.id, context, cause, failedStep)
    }

    private suspend fun <Ctx : Any> executeStepWithRetry(
        step: SagaStep<Ctx>,
        context: Ctx,
    ): StepOutcome<Ctx> {
        var attempt = 0
        while (true) {
            try {
                val next = step.action(context)
                return StepOutcome.Success(next, attempt + 1)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (!step.retryOn(t)) return StepOutcome.Failure(t, attempt + 1)
                val delay = step.retryPolicy.delayFor(attempt)
                    ?: return StepOutcome.Failure(t, attempt + 1)
                log.debug("saga step {} attempt {} failed, retrying in {}", step.name, attempt + 1, delay, t)
                delay(delay.inWholeMilliseconds)
                attempt++
            }
        }
    }

    private fun formatError(t: Throwable): String {
        val cls = t::class.qualifiedName ?: t::class.java.name
        return "$cls: ${t.message ?: ""}".take(MAX_ERROR_LENGTH)
    }

    private sealed interface StepOutcome<Ctx : Any> {
        data class Success<Ctx : Any>(val context: Ctx, val attempts: Int) : StepOutcome<Ctx>
        data class Failure<Ctx : Any>(val cause: Throwable, val attempts: Int) : StepOutcome<Ctx>
    }

    companion object {
        private const val MAX_ERROR_LENGTH = 2000
    }
}
