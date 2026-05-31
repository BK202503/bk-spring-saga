package io.github.sagakt.spring

import io.github.sagakt.core.SagaCodecRegistry
import io.github.sagakt.core.SagaDefinition
import io.github.sagakt.core.SagaExecutor
import io.github.sagakt.core.SagaResult
import io.github.sagakt.core.SagaStateRepository
import io.github.sagakt.core.saga
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

data class DemoCtx(val id: String, val charged: Boolean = false, val shipped: Boolean = false)

@SpringBootTest(
    classes = [SagaAutoConfigurationTest.TestApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:saga-autoconf;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class SagaAutoConfigurationTest {

    @Autowired lateinit var executor: SagaExecutor
    @Autowired lateinit var repository: SagaStateRepository
    @Autowired lateinit var codecRegistry: SagaCodecRegistry
    @Autowired lateinit var demoSaga: SagaDefinition<DemoCtx>

    @Test
    fun `autoconfigured executor runs a saga to completion`() {
        val result = runBlocking { executor.execute(demoSaga, DemoCtx("order-1")) }
        val completed = result.shouldBeInstanceOf<SagaResult.Completed<DemoCtx>>()
        completed.context shouldBe DemoCtx("order-1", charged = true, shipped = true)

        val record = runBlocking { repository.findById(completed.id) }
        record!!.completedSteps shouldBe listOf("charge", "ship")
    }

    @Configuration
    @EnableAutoConfiguration(exclude = [])
    open class TestApp {
        @Bean
        open fun demoSaga(): SagaDefinition<DemoCtx> = saga("demo") {
            step("charge") { action { it.copy(charged = true) } }
            step("ship") { action { it.copy(shipped = true) } }
        }
    }
}
