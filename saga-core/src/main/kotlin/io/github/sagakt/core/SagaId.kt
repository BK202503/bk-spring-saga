package io.github.sagakt.core

import java.util.UUID

@JvmInline
value class SagaId(val value: String) {
    override fun toString(): String = value

    companion object {
        fun random(): SagaId = SagaId(UUID.randomUUID().toString())
    }
}
