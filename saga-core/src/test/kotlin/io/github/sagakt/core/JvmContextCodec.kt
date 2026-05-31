package io.github.sagakt.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * Test-only codec that uses Java serialization so we don't pull Jackson into
 * the core module. Production code uses the Jackson-based codec in the JDBC
 * storage module.
 */
class JvmContextCodec<Ctx : Any> : SagaContextCodec<Ctx> {
    override fun encode(context: Ctx): ByteArray {
        require(context is Serializable) { "Context must be Serializable for JvmContextCodec" }
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(context) }
        return bos.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(payload: ByteArray): Ctx =
        ObjectInputStream(ByteArrayInputStream(payload)).use { it.readObject() } as Ctx
}

class JvmCodecRegistry : SagaCodecRegistry {
    override fun <Ctx : Any> codecFor(definition: SagaDefinition<Ctx>): SagaContextCodec<Ctx> =
        JvmContextCodec()
}
