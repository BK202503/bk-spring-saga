package io.github.sagakt.core

class SagaStep<Ctx : Any> internal constructor(
    val name: String,
    val action: suspend (Ctx) -> Ctx,
    val compensate: (suspend (Ctx) -> Unit)?,
    val retryPolicy: RetryPolicy,
    val retryOn: (Throwable) -> Boolean,
) {
    override fun toString(): String = "SagaStep(name='$name')"
}
