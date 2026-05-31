package io.github.sagakt.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.sagakt.core.InMemorySagaStateRepository
import io.github.sagakt.core.MapSagaDefinitionRegistry
import io.github.sagakt.core.SagaCodecRegistry
import io.github.sagakt.core.SagaDefinition
import io.github.sagakt.core.SagaDefinitionRegistry
import io.github.sagakt.core.SagaEventPublisher
import io.github.sagakt.core.SagaExecutor
import io.github.sagakt.core.SagaMetrics
import io.github.sagakt.core.SagaResumer
import io.github.sagakt.core.SagaStateRepository
import io.github.sagakt.storage.jdbc.JacksonCodecRegistry
import io.github.sagakt.storage.jdbc.JdbcSagaStateRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import javax.sql.DataSource

@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@EnableConfigurationProperties(SagaProperties::class)
class SagaAutoConfiguration {

    private fun resolveMapper(objectMappers: ObjectProvider<ObjectMapper>): ObjectMapper =
        objectMappers.ifAvailable ?: JacksonCodecRegistry.defaultMapper()

    @Bean
    @ConditionalOnMissingBean
    fun sagaCodecRegistry(objectMappers: ObjectProvider<ObjectMapper>): SagaCodecRegistry =
        JacksonCodecRegistry(resolveMapper(objectMappers))

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "saga", name = ["storage"], havingValue = "JDBC", matchIfMissing = true)
    fun jdbcSagaStateRepository(
        dataSource: DataSource,
        objectMappers: ObjectProvider<ObjectMapper>,
        properties: SagaProperties,
    ): SagaStateRepository {
        val repo = JdbcSagaStateRepository(dataSource, resolveMapper(objectMappers), properties.tableName)
        if (properties.initializeSchema) repo.initializeSchema()
        return repo
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "saga", name = ["storage"], havingValue = "IN_MEMORY")
    fun inMemorySagaStateRepository(): SagaStateRepository = InMemorySagaStateRepository()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "saga", name = ["metrics-enabled"], havingValue = "true", matchIfMissing = true)
    fun micrometerSagaMetrics(registries: ObjectProvider<MeterRegistry>): SagaMetrics =
        registries.ifAvailable?.let(::MicrometerSagaMetrics) ?: SagaMetrics.NoOp

    @Bean
    @ConditionalOnMissingBean(SagaMetrics::class)
    fun noopSagaMetrics(): SagaMetrics = SagaMetrics.NoOp

    @Bean
    @ConditionalOnMissingBean
    fun sagaDefinitionRegistry(definitions: ObjectProvider<SagaDefinition<*>>): SagaDefinitionRegistry =
        MapSagaDefinitionRegistry(definitions.orderedStream().toList())

    @Bean
    @ConditionalOnMissingBean(SagaEventPublisher::class)
    fun noopSagaEventPublisher(): SagaEventPublisher = SagaEventPublisher.NoOp

    @Bean
    @ConditionalOnMissingBean
    fun sagaExecutor(
        repository: SagaStateRepository,
        codecRegistry: SagaCodecRegistry,
        metrics: SagaMetrics,
        eventPublisher: SagaEventPublisher,
    ): SagaExecutor = SagaExecutor(repository, codecRegistry, metrics = metrics, eventPublisher = eventPublisher)

    @Bean
    @ConditionalOnMissingBean
    fun sagaResumer(
        executor: SagaExecutor,
        repository: SagaStateRepository,
        registry: SagaDefinitionRegistry,
    ): SagaResumer = SagaResumer(executor, repository, registry)

    @Bean
    fun sagaStartupResumer(resumer: SagaResumer, properties: SagaProperties): SagaStartupResumer =
        SagaStartupResumer(resumer, properties)
}
