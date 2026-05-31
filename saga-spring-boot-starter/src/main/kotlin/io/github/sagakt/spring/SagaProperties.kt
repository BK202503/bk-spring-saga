package io.github.sagakt.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "saga")
data class SagaProperties(
    /** Storage backend selection. */
    val storage: Storage = Storage.JDBC,
    /** Schema bootstrap: when true, the starter creates the saga_state table on startup. */
    val initializeSchema: Boolean = true,
    /** Override the saga_state table name. */
    val tableName: String = "saga_state",
    /** Resume orphaned sagas on application startup. */
    val resumeOnStartup: Boolean = true,
    /** Maximum number of records to resume in a single startup scan. */
    val resumeBatchSize: Int = 100,
    /** Publish Micrometer metrics. */
    val metricsEnabled: Boolean = true,
) {
    enum class Storage { JDBC, IN_MEMORY }
}
