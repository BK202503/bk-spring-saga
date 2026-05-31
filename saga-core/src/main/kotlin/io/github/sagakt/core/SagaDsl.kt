package io.github.sagakt.core

import kotlin.reflect.KClass

@DslMarker
annotation class SagaDslMarker

@SagaDslMarker
class SagaBuilder<Ctx : Any> @PublishedApi internal constructor(
    private val name: String,
    private val contextType: KClass<Ctx>,
) {
    private val steps = mutableListOf<SagaStep<Ctx>>()
    private var onComplete: (suspend (Ctx) -> Unit)? = null
    private var onFailure: (suspend (Ctx, Throwable) -> Unit)? = null

    fun step(name: String, configure: SagaStepBuilder<Ctx>.() -> Unit) {
        steps += SagaStepBuilder<Ctx>(name).apply(configure).build()
    }

    fun onComplete(block: suspend (Ctx) -> Unit) {
        onComplete = block
    }

    fun onFailure(block: suspend (Ctx, Throwable) -> Unit) {
        onFailure = block
    }

    @PublishedApi
    internal fun build(): SagaDefinition<Ctx> =
        SagaDefinition(name, steps.toList(), onComplete, onFailure, contextType)
}

@SagaDslMarker
class SagaStepBuilder<Ctx : Any> internal constructor(private val name: String) {
    private var action: (suspend (Ctx) -> Ctx)? = null
    private var compensate: (suspend (Ctx) -> Unit)? = null
    private var retryPolicy: RetryPolicy = RetryPolicy.None
    private var retryOn: (Throwable) -> Boolean = { true }

    fun action(block: suspend (Ctx) -> Ctx) {
        action = block
    }

    fun compensate(block: suspend (Ctx) -> Unit) {
        compensate = block
    }

    fun retry(policy: RetryPolicy) {
        retryPolicy = policy
    }

    fun retryOn(predicate: (Throwable) -> Boolean) {
        retryOn = predicate
    }

    internal fun build(): SagaStep<Ctx> {
        val a = action ?: error("Step '$name' is missing an action { ... } block")
        return SagaStep(name, a, compensate, retryPolicy, retryOn)
    }
}

inline fun <reified Ctx : Any> saga(
    name: String,
    configure: SagaBuilder<Ctx>.() -> Unit,
): SagaDefinition<Ctx> = SagaBuilder(name, Ctx::class).apply(configure).build()
