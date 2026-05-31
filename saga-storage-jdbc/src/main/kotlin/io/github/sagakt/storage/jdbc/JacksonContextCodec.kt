package io.github.sagakt.storage.jdbc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.sagakt.core.SagaCodecRegistry
import io.github.sagakt.core.SagaContextCodec
import io.github.sagakt.core.SagaDefinition

class JacksonContextCodec<Ctx : Any>(
    private val mapper: ObjectMapper,
    private val type: Class<Ctx>,
) : SagaContextCodec<Ctx> {
    override fun encode(context: Ctx): ByteArray = mapper.writeValueAsBytes(context)
    override fun decode(payload: ByteArray): Ctx = mapper.readValue(payload, type)
}

class JacksonCodecRegistry(
    mapper: ObjectMapper = defaultMapper(),
) : SagaCodecRegistry {
    private val mapper: ObjectMapper = mapper

    override fun <Ctx : Any> codecFor(definition: SagaDefinition<Ctx>): SagaContextCodec<Ctx> =
        JacksonContextCodec(mapper, definition.contextType.java)

    companion object {
        fun defaultMapper(): ObjectMapper = jacksonObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
    }
}
