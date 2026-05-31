package io.github.sagakt.events.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "saga.events.kafka")
data class KafkaSagaEventProperties(
    /** Enable Kafka publishing. Off by default so adding the module does not surprise existing apps. */
    val enabled: Boolean = false,
    /** Topic for saga lifecycle events. Same topic for all sagas; partitioning is by saga id. */
    val topic: String = "saga.events",
)
