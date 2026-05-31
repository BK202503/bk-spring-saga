package io.github.sagakt.events.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.sagakt.core.InMemorySagaStateRepository
import io.github.sagakt.core.SagaCodecRegistry
import io.github.sagakt.core.SagaContextCodec
import io.github.sagakt.core.SagaDefinition
import io.github.sagakt.core.SagaExecutor
import io.github.sagakt.core.saga
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.io.Serializable
import java.time.Duration
import java.util.UUID

private data class DemoCtx(val orderId: String, val charged: Boolean = false) : Serializable

private class JvmCodec<Ctx : Any> : SagaContextCodec<Ctx> {
    override fun encode(context: Ctx): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(bos).use { it.writeObject(context) }
        return bos.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(payload: ByteArray): Ctx =
        java.io.ObjectInputStream(java.io.ByteArrayInputStream(payload)).use { it.readObject() } as Ctx
}

private class JvmCodecRegistry : SagaCodecRegistry {
    override fun <Ctx : Any> codecFor(definition: SagaDefinition<Ctx>): SagaContextCodec<Ctx> = JvmCodec()
}

class KafkaSagaEventPublisherTest : StringSpec({
    val requireDocker = System.getenv("SAGA_REQUIRE_DOCKER") == "1"

    val kafka = runCatching {
        KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).also { it.start() }
    }

    if (kafka.isFailure) {
        val err = kafka.exceptionOrNull()
        if (requireDocker) {
            throw IllegalStateException(
                "SAGA_REQUIRE_DOCKER=1 but Kafka container could not start: ${err?.message}",
                err,
            )
        }
        "[kafka] suite skipped: Docker not reachable (${err?.javaClass?.simpleName})"
            .config(enabled = false) { }
    } else {
        val container = kafka.getOrThrow()
        val topic = "saga.events.test"

        val producerFactory = DefaultKafkaProducerFactory<String, ByteArray>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to container.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.LINGER_MS_CONFIG to 0,
            ),
        )
        val template = KafkaTemplate(producerFactory)
        val publisher = KafkaSagaEventPublisher(template, topic)
        val executor = SagaExecutor(
            repository = InMemorySagaStateRepository(),
            codecRegistry = JvmCodecRegistry(),
            eventPublisher = publisher,
        )
        val mapper = ObjectMapper()

        fun newConsumer(): KafkaConsumer<String, ByteArray> = KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to container.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ),
            StringDeserializer(),
            ByteArrayDeserializer(),
        ).apply { subscribe(listOf(topic)) }

        fun KafkaConsumer<String, ByteArray>.drain(expected: Int, timeoutMs: Long = 10_000): List<ConsumerRecord<String, ByteArray>> {
            val out = mutableListOf<ConsumerRecord<String, ByteArray>>()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (out.size < expected && System.currentTimeMillis() < deadline) {
                poll(Duration.ofMillis(500)).forEach(out::add)
            }
            return out
        }

        afterSpec {
            template.destroy()
            container.stop()
        }

        "publishes Started + StepCompleted + Completed on the happy path" {
            val def = saga<DemoCtx>("demo-success") {
                step("charge") { action { it.copy(charged = true) } }
            }
            val consumer = newConsumer()
            consumer.use { c ->
                runBlocking { executor.execute(def, DemoCtx("o-1")) }
                val records = c.drain(expected = 3)
                records.size shouldBe 3

                records.forEach { r -> r.key() shouldBe records[0].key() }

                val types = records.map { r ->
                    String(r.headers().lastHeader("event_type").value())
                }
                types shouldBe listOf("Started", "StepCompleted", "Completed")

                val sagaNames = records.map { r ->
                    String(r.headers().lastHeader("saga_name").value())
                }
                sagaNames.toSet() shouldBe setOf("demo-success")

                val stepCompleted: JsonNode = mapper.readTree(records[1].value())
                stepCompleted["stepName"].asText() shouldBe "charge"
                stepCompleted["attempts"].asInt() shouldBe 1
            }
        }

        "publishes StepFailed + Compensated for the failure path" {
            val def = saga<DemoCtx>("demo-failure") {
                step("charge") {
                    action { it.copy(charged = true) }
                    compensate { /* uncharge */ }
                }
                step("ship") { action { error("carrier down") } }
            }
            val consumer = newConsumer()
            consumer.use { c ->
                runBlocking { executor.execute(def, DemoCtx("o-2")) }
                val records = c.drain(expected = 5)
                val types = records.map { String(it.headers().lastHeader("event_type").value()) }
                types shouldContainAll listOf("Started", "StepCompleted", "StepFailed", "Compensated")

                val failed = mapper.readTree(records.first { String(it.headers().lastHeader("event_type").value()) == "StepFailed" }.value())
                failed["stepName"].asText() shouldBe "ship"
                failed["errorMessage"].asText() shouldContain "carrier"

                val compensated = mapper.readTree(records.first { String(it.headers().lastHeader("event_type").value()) == "Compensated" }.value())
                compensated["failedStep"].asText() shouldBe "ship"
            }
        }
    }
})
