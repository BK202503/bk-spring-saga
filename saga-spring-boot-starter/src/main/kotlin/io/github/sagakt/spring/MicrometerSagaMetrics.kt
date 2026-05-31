package io.github.sagakt.spring

import io.github.sagakt.core.SagaMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags

class MicrometerSagaMetrics(private val registry: MeterRegistry) : SagaMetrics {
    override fun sagaStarted(sagaName: String) {
        registry.counter("saga.started", "saga", sagaName).increment()
    }

    override fun sagaCompleted(sagaName: String) {
        registry.counter("saga.completed", "saga", sagaName).increment()
    }

    override fun sagaCompensated(sagaName: String) {
        registry.counter("saga.compensated", "saga", sagaName).increment()
    }

    override fun sagaCompensationFailed(sagaName: String) {
        registry.counter("saga.compensation_failed", "saga", sagaName).increment()
    }

    override fun stepCompleted(sagaName: String, stepName: String, attempts: Int) {
        registry.counter(
            "saga.step.completed",
            Tags.of("saga", sagaName, "step", stepName),
        ).increment()
        if (attempts > 1) {
            registry.counter(
                "saga.step.retried",
                Tags.of("saga", sagaName, "step", stepName),
            ).increment(attempts.toDouble() - 1)
        }
    }

    override fun stepFailed(sagaName: String, stepName: String, error: Throwable) {
        registry.counter(
            "saga.step.failed",
            Tags.of(
                "saga", sagaName,
                "step", stepName,
                "exception", error::class.simpleName ?: "Throwable",
            ),
        ).increment()
    }

    override fun compensationCompleted(sagaName: String, stepName: String) {
        registry.counter(
            "saga.compensation.step.completed",
            Tags.of("saga", sagaName, "step", stepName),
        ).increment()
    }

    override fun compensationFailed(sagaName: String, stepName: String, error: Throwable) {
        registry.counter(
            "saga.compensation.step.failed",
            Tags.of(
                "saga", sagaName,
                "step", stepName,
                "exception", error::class.simpleName ?: "Throwable",
            ),
        ).increment()
    }
}
