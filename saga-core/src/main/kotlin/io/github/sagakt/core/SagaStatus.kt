package io.github.sagakt.core

enum class SagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    COMPENSATION_FAILED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == COMPENSATED || this == COMPENSATION_FAILED

    val isResumable: Boolean
        get() = this == RUNNING || this == COMPENSATING
}
