package io.github.sagakt.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private data class OrderCtx(
    val orderId: String,
    val reservationId: String? = null,
    val chargeId: String? = null,
    val shipped: Boolean = false,
) : Serializable

class SagaExecutorTest : StringSpec({
    fun newExecutor(): Pair<SagaExecutor, InMemorySagaStateRepository> {
        val repo = InMemorySagaStateRepository()
        return SagaExecutor(repo, JvmCodecRegistry()) to repo
    }

    "executes all steps and returns completed result" {
        val (executor, _) = newExecutor()
        val def = saga<OrderCtx>("order") {
            step("reserve") { action { it.copy(reservationId = "r-1") } }
            step("charge") { action { it.copy(chargeId = "c-1") } }
            step("ship") { action { it.copy(shipped = true) } }
        }

        val result = runBlocking { executor.execute(def, OrderCtx("o-1")) }

        result.shouldBeInstanceOf<SagaResult.Completed<OrderCtx>>()
        result.context shouldBe OrderCtx("o-1", "r-1", "c-1", true)
    }

    "runs compensations in reverse for failed step" {
        val (executor, _) = newExecutor()
        val compensated = mutableListOf<String>()
        val def = saga<OrderCtx>("order") {
            step("reserve") {
                action { it.copy(reservationId = "r-1") }
                compensate { compensated += "reserve" }
            }
            step("charge") {
                action { it.copy(chargeId = "c-1") }
                compensate { compensated += "charge" }
            }
            step("ship") {
                action { error("shipping carrier down") }
                compensate { compensated += "ship" }
            }
        }

        val result = runBlocking { executor.execute(def, OrderCtx("o-1")) }

        result.shouldBeInstanceOf<SagaResult.Compensated<OrderCtx>>()
        result.failedStep shouldBe "ship"
        compensated shouldContainExactly listOf("charge", "reserve")
    }

    "retries transient failures up to the policy limit" {
        val (executor, _) = newExecutor()
        val attempts = AtomicInteger()
        val def = saga<OrderCtx>("order") {
            step("flaky") {
                action {
                    if (attempts.incrementAndGet() < 3) error("transient")
                    it.copy(reservationId = "r")
                }
                retry(RetryPolicy.Fixed(maxAttempts = 5, delay = 1.milliseconds))
            }
        }

        val result = runBlocking { executor.execute(def, OrderCtx("o")) }

        result.shouldBeInstanceOf<SagaResult.Completed<OrderCtx>>()
        attempts.get() shouldBe 3
    }

    "stops retrying when retryOn returns false" {
        val (executor, _) = newExecutor()
        val def = saga<OrderCtx>("order") {
            step("bad") {
                action { error("fatal") }
                retry(RetryPolicy.Fixed(maxAttempts = 10, delay = 1.milliseconds))
                retryOn { it !is IllegalStateException }
            }
        }

        val result = runBlocking { executor.execute(def, OrderCtx("o")) }
        result.shouldBeInstanceOf<SagaResult.Compensated<OrderCtx>>()
    }

    "reports COMPENSATION_FAILED when a compensation step throws" {
        val (executor, _) = newExecutor()
        val def = saga<OrderCtx>("order") {
            step("reserve") {
                action { it.copy(reservationId = "r-1") }
                compensate { error("rollback broken") }
            }
            step("charge") {
                action { error("declined") }
                compensate { /* never reached for this step */ }
            }
        }

        val result = runBlocking { executor.execute(def, OrderCtx("o")) }

        val failed = result.shouldBeInstanceOf<SagaResult.CompensationFailed<OrderCtx>>()
        failed.compensationStep shouldBe "reserve"
        failed.originalFailedStep shouldBe "charge"
    }

    "persists progress after each step" {
        val (executor, repo) = newExecutor()
        val def = saga<OrderCtx>("order") {
            step("a") { action { it.copy(reservationId = "r") } }
            step("b") { action { it.copy(chargeId = "c") } }
        }

        val result = runBlocking { executor.execute(def, OrderCtx("o")) }
        val record = runBlocking { repo.findById(result.id) }
        record shouldNotBe null
        record!!.status shouldBe SagaStatus.COMPLETED
        record.completedSteps shouldContainExactly listOf("a", "b")
        record.version shouldBe 2
    }

    "resume picks up a RUNNING record mid-saga" {
        val repo = InMemorySagaStateRepository()
        val executor = SagaExecutor(repo, JvmCodecRegistry())

        val crashAfterFirst = AtomicInteger(0)
        val crashingDef = saga<OrderCtx>("order") {
            step("a") { action { it.copy(reservationId = "r") } }
            step("b") {
                action {
                    if (crashAfterFirst.getAndIncrement() == 0) {
                        throw SimulatedCrash()
                    }
                    it.copy(chargeId = "c")
                }
            }
            step("c") { action { it.copy(shipped = true) } }
        }

        val firstAttempt = runBlocking { executor.execute(crashingDef, OrderCtx("o")) }
        // Simulated crash compensated the saga in this design, so to test pure
        // resume we craft a RUNNING record manually.
        runBlocking { repo.clear() }
        val id = SagaId.random()
        val now = java.time.Instant.now()
        val codec = JvmContextCodec<OrderCtx>()
        runBlocking {
            repo.insert(
                SagaRecord(
                    id = id,
                    sagaName = "order",
                    status = SagaStatus.RUNNING,
                    contextPayload = codec.encode(OrderCtx("o", reservationId = "r")),
                    contextType = OrderCtx::class.qualifiedName!!,
                    completedSteps = listOf("a"),
                    currentStepIndex = 1,
                    createdAt = now,
                    updatedAt = now,
                    version = 1,
                ),
            )
        }

        val healingDef = saga<OrderCtx>("order") {
            step("a") { action { error("should not run again") } }
            step("b") { action { it.copy(chargeId = "c") } }
            step("c") { action { it.copy(shipped = true) } }
        }

        val resumed = runBlocking { executor.resume(healingDef, id) }
        resumed.shouldBeInstanceOf<SagaResult.Completed<OrderCtx>>()
        resumed.context.chargeId shouldBe "c"
        resumed.context.shipped shouldBe true
        firstAttempt shouldNotBe null
    }

    "rejects empty step list" {
        shouldThrow<IllegalArgumentException> {
            saga<OrderCtx>("empty") {}
        }
    }

    "rejects duplicate step names" {
        shouldThrow<IllegalArgumentException> {
            saga<OrderCtx>("dup") {
                step("x") { action { it } }
                step("x") { action { it } }
            }
        }
    }

    "invokes onComplete on success" {
        val (executor, _) = newExecutor()
        var called: OrderCtx? = null
        val def = saga<OrderCtx>("order") {
            step("a") { action { it.copy(reservationId = "r") } }
            onComplete { called = it }
        }
        runBlocking { executor.execute(def, OrderCtx("o")) }
        called?.reservationId shouldBe "r"
    }

    "invokes onFailure on compensation" {
        val (executor, _) = newExecutor()
        var seen: Throwable? = null
        val def = saga<OrderCtx>("order") {
            step("a") {
                action { it }
                compensate { }
            }
            step("b") { action { error("nope") } }
            onFailure { _, t -> seen = t }
        }
        runBlocking { executor.execute(def, OrderCtx("o")) }
        seen shouldNotBe null
        seen!!.message shouldBe "nope"
    }
})

private class SimulatedCrash : RuntimeException("simulated crash")
