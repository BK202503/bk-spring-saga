package io.github.sagakt.core

interface SagaMetrics {
    fun sagaStarted(sagaName: String) {}
    fun sagaCompleted(sagaName: String) {}
    fun sagaCompensated(sagaName: String) {}
    fun sagaCompensationFailed(sagaName: String) {}
    fun stepCompleted(sagaName: String, stepName: String, attempts: Int) {}
    fun stepFailed(sagaName: String, stepName: String, error: Throwable) {}
    fun compensationCompleted(sagaName: String, stepName: String) {}
    fun compensationFailed(sagaName: String, stepName: String, error: Throwable) {}

    companion object {
        val NoOp: SagaMetrics = object : SagaMetrics {}
    }
}
