package io.github.sagakt.spring

import io.github.sagakt.core.SagaResumer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

/**
 * Listens for application-ready and resumes any orphaned saga records. We use
 * [ApplicationReadyEvent] (not a `@PostConstruct`) so the resume scan runs only
 * after the full context — including user-supplied saga definitions — is up.
 */
class SagaStartupResumer(
    private val resumer: SagaResumer,
    private val properties: SagaProperties,
) {
    private val log = LoggerFactory.getLogger(SagaStartupResumer::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!properties.resumeOnStartup) {
            log.info("saga.resume-on-startup=false, skipping resume scan")
            return
        }
        val report = runBlocking { resumer.resumeAll(properties.resumeBatchSize) }
        log.info(
            "saga resume scan finished: resumed={} skipped={} failed={}",
            report.resumed.size, report.skipped.size, report.failed.size,
        )
    }
}
