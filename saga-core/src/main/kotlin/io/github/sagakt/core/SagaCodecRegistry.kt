package io.github.sagakt.core

/**
 * Resolves a codec for a given saga definition. The executor uses this to
 * serialize context before handing it to the repository.
 *
 * Implementations may use the [SagaDefinition.contextType] to look up a codec,
 * fall back to a default JSON codec, or accept explicit registrations.
 */
interface SagaCodecRegistry {
    fun <Ctx : Any> codecFor(definition: SagaDefinition<Ctx>): SagaContextCodec<Ctx>
}
