package io.github.sagakt.events.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.sagakt.core.SagaEvent
import io.github.sagakt.core.SagaEventPublisher
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

/**
 * Publishes [SagaEvent]s to a Kafka topic.
 *
 * - Payload: Jackson JSON, including a top-level `type` field for polymorphic decoding.
 * - Key: `sagaId.value` so events for the same saga land on the same partition and
 *   stay strictly ordered.
 * - Headers: `event_type` (e.g. "Started") for cheap filtering without parsing the body.
 *
 * Failures are propagated to the caller; the executor catches and logs them so a
 * downstream Kafka outage cannot crash the saga itself.
 */
class KafkaSagaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
    private val topic: String,
    private val mapper: ObjectMapper = defaultMapper(),
) : SagaEventPublisher {
    private val log = LoggerFactory.getLogger(KafkaSagaEventPublisher::class.java)

    override suspend fun publish(event: SagaEvent) {
        val payload = mapper.writeValueAsBytes(event)
        val record = ProducerRecord(topic, null, null, event.sagaId.value, payload).apply {
            headers().add(RecordHeader("event_type", event.type.toByteArray()))
            headers().add(RecordHeader("saga_name", event.sagaName.toByteArray()))
        }
        val metadata = kafkaTemplate.send(record).await().recordMetadata
        log.debug(
            "published {} for saga {} to {}-{}@{}",
            event.type, event.sagaId, metadata.topic(), metadata.partition(), metadata.offset(),
        )
    }

    companion object {
        fun defaultMapper(): ObjectMapper = jacksonObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
