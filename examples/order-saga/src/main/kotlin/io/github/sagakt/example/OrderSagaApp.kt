package io.github.sagakt.example

import io.github.sagakt.core.SagaDefinition
import io.github.sagakt.core.SagaExecutor
import io.github.sagakt.core.RetryPolicy
import io.github.sagakt.core.SagaResult
import io.github.sagakt.core.saga
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.time.Duration.Companion.milliseconds

@SpringBootApplication
class OrderSagaApp

fun main(args: Array<String>) {
    SpringApplication.run(OrderSagaApp::class.java, *args)
}

data class OrderContext(
    val orderId: String,
    val userId: String,
    val sku: String,
    val quantity: Int,
    val amountCents: Long,
    val reservationId: String? = null,
    val chargeId: String? = null,
    val shipmentId: String? = null,
)

@Service
class InventoryClient {
    private val log = LoggerFactory.getLogger(javaClass)
    fun reserve(orderId: String, sku: String, qty: Int): String {
        log.info("reserve inventory order={} sku={} qty={}", orderId, sku, qty)
        return "res-$orderId"
    }
    fun release(reservationId: String) = log.info("release inventory res={}", reservationId)
}

@Service
class PaymentClient {
    private val log = LoggerFactory.getLogger(javaClass)
    fun charge(userId: String, amountCents: Long): String {
        if (amountCents > 1_000_000) error("payment declined: amount exceeds limit")
        log.info("charge user={} amount={}", userId, amountCents)
        return "ch-${userId}-${amountCents}"
    }
    fun refund(chargeId: String) = log.info("refund charge={}", chargeId)
}

@Service
class ShippingClient {
    private val log = LoggerFactory.getLogger(javaClass)
    fun createShipment(orderId: String): String {
        log.info("create shipment order={}", orderId)
        return "shp-$orderId"
    }
}

@Configuration
class OrderSagaConfig {
    @Bean
    fun orderSaga(
        inventory: InventoryClient,
        payment: PaymentClient,
        shipping: ShippingClient,
    ): SagaDefinition<OrderContext> = saga("order-fulfillment") {
        step("reserve-inventory") {
            action { ctx ->
                ctx.copy(reservationId = inventory.reserve(ctx.orderId, ctx.sku, ctx.quantity))
            }
            compensate { ctx -> ctx.reservationId?.let(inventory::release) }
            retry(RetryPolicy.Exponential(maxAttempts = 3, initial = 100.milliseconds))
        }
        step("charge-payment") {
            action { ctx ->
                ctx.copy(chargeId = payment.charge(ctx.userId, ctx.amountCents))
            }
            compensate { ctx -> ctx.chargeId?.let(payment::refund) }
        }
        step("create-shipment") {
            action { ctx ->
                ctx.copy(shipmentId = shipping.createShipment(ctx.orderId))
            }
        }
    }
}

data class PlaceOrderRequest(
    val orderId: String,
    val userId: String,
    val sku: String,
    val quantity: Int,
    val amountCents: Long,
)

data class PlaceOrderResponse(
    val sagaId: String,
    val outcome: String,
    val orderId: String,
    val chargeId: String?,
    val shipmentId: String?,
    val error: String?,
)

@RestController
class OrderController @Autowired constructor(
    private val executor: SagaExecutor,
    private val orderSaga: SagaDefinition<OrderContext>,
) {
    @PostMapping("/orders")
    fun place(@RequestBody req: PlaceOrderRequest): PlaceOrderResponse = runBlocking {
        val result = executor.execute(
            orderSaga,
            OrderContext(req.orderId, req.userId, req.sku, req.quantity, req.amountCents),
        )
        when (result) {
            is SagaResult.Completed -> PlaceOrderResponse(
                result.id.value, "COMPLETED",
                result.context.orderId, result.context.chargeId, result.context.shipmentId, null,
            )
            is SagaResult.Compensated -> PlaceOrderResponse(
                result.id.value, "COMPENSATED",
                result.context.orderId, null, null,
                "failed at ${result.failedStep}: ${result.cause.message}",
            )
            is SagaResult.CompensationFailed -> PlaceOrderResponse(
                result.id.value, "COMPENSATION_FAILED",
                result.context.orderId, null, null,
                "rollback failed at ${result.compensationStep}: ${result.compensationError.message}",
            )
        }
    }
}
