package io.github.sagakt.core

import kotlin.reflect.KClass

class SagaDefinition<Ctx : Any> internal constructor(
    val name: String,
    val steps: List<SagaStep<Ctx>>,
    val onComplete: (suspend (Ctx) -> Unit)?,
    val onFailure: (suspend (Ctx, Throwable) -> Unit)?,
    val contextType: KClass<Ctx>,
) {
    init {
        require(name.isNotBlank()) { "Saga name must not be blank" }
        require(steps.isNotEmpty()) { "Saga '$name' must declare at least one step" }
        val duplicates = steps.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Saga '$name' has duplicate step names: $duplicates" }
    }

    fun stepByName(name: String): SagaStep<Ctx>? = steps.firstOrNull { it.name == name }
}
