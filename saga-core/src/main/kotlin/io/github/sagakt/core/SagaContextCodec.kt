package io.github.sagakt.core

/**
 * Encodes and decodes a saga's context to/from the byte payload that storage
 * adapters persist. Implementations are typically JSON-based but the contract
 * does not require it.
 */
interface SagaContextCodec<Ctx : Any> {
    fun encode(context: Ctx): ByteArray
    fun decode(payload: ByteArray): Ctx
}
