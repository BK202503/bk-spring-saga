package io.github.sagakt.events.kafka

import io.github.sagakt.core.SagaEventPublisher
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate::class)
@ConditionalOnProperty(prefix = "saga.events.kafka", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(KafkaSagaEventProperties::class)
class KafkaSagaEventAutoConfiguration {

    @Bean
    @ConditionalOnBean(KafkaTemplate::class)
    @ConditionalOnMissingBean(SagaEventPublisher::class)
    fun kafkaSagaEventPublisher(
        kafkaTemplate: KafkaTemplate<String, ByteArray>,
        properties: KafkaSagaEventProperties,
    ): SagaEventPublisher = KafkaSagaEventPublisher(kafkaTemplate, properties.topic)
}
