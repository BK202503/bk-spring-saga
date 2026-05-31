package io.github.sagakt.core

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/**
 * Resumes saga records that were interrupted by a JVM restart. Call [resumeAll]
 * during application startup (the Spring Boot starter wires this automatically).
 *
 * Sagas whose definition is not registered are skipped with a warning — those
 * typically indicate a removed or renamed saga that requires manual triage.
 */
class SagaResumer(
    private val executor: SagaExecutor,
    private val repository: SagaStateRepository,
    private val registry: SagaDefinitionRegistry,
) {
    private val log = LoggerFactory.getLogger(SagaResumer::class.java)

    suspend fun resumeAll(batchSize: Int = 100): ResumeReport {
        val resumed = mutableListOf<SagaId>()
        val skipped = mutableListOf<SagaId>()
        val failed = mutableListOf<Pair<SagaId, Throwable>>()

        val records = repository.findResumable(limit = batchSize)
        log.info("resume scan found {} sagas to resume", records.size)
        for (record in records) {
            val definition = registry.find(record.sagaName)
            if (definition == null) {
                log.warn("no definition registered for saga '{}' (id={}), skipping", record.sagaName, record.id)
                skipped += record.id
                continue
            }
            try {
                @Suppress("UNCHECKED_CAST")
                executor.resume(definition as SagaDefinition<Any>, record.id)
                resumed += record.id
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                log.error("resume failed for saga id={}", record.id, t)
                failed += record.id to t
            }
        }
        return ResumeReport(resumed, skipped, failed)
    }

    data class ResumeReport(
        val resumed: List<SagaId>,
        val skipped: List<SagaId>,
        val failed: List<Pair<SagaId, Throwable>>,
    )
}

interface SagaDefinitionRegistry {
    fun find(name: String): SagaDefinition<*>?
    fun all(): Collection<SagaDefinition<*>>
}

class MapSagaDefinitionRegistry(definitions: Iterable<SagaDefinition<*>>) : SagaDefinitionRegistry {
    private val byName: Map<String, SagaDefinition<*>> = definitions.associateBy { it.name }

    init {
        val grouped = definitions.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(grouped.isEmpty()) { "Duplicate saga names registered: $grouped" }
    }

    override fun find(name: String): SagaDefinition<*>? = byName[name]
    override fun all(): Collection<SagaDefinition<*>> = byName.values
}
