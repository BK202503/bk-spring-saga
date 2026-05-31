package io.github.sagakt.core

sealed interface SagaResult<Ctx : Any> {
    val id: SagaId
    val context: Ctx
    val isSuccess: Boolean

    data class Completed<Ctx : Any>(
        override val id: SagaId,
        override val context: Ctx,
    ) : SagaResult<Ctx> {
        override val isSuccess: Boolean = true
    }

    data class Compensated<Ctx : Any>(
        override val id: SagaId,
        override val context: Ctx,
        val cause: Throwable,
        val failedStep: String,
    ) : SagaResult<Ctx> {
        override val isSuccess: Boolean = false
    }

    data class CompensationFailed<Ctx : Any>(
        override val id: SagaId,
        override val context: Ctx,
        val compensationError: Throwable,
        val compensationStep: String,
        val originalCause: Throwable,
        val originalFailedStep: String,
    ) : SagaResult<Ctx> {
        override val isSuccess: Boolean = false
    }
}
