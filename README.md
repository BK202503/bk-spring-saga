# spring-saga-kt

A Kotlin-first, coroutine-native Saga orchestrator for Spring Boot.

```kotlin
val orderSaga = saga<OrderContext>("order-fulfillment") {
    step("reserve-inventory") {
        action     { ctx -> ctx.copy(reservationId = inventory.reserve(ctx)) }
        compensate { ctx -> inventory.release(ctx.reservationId!!) }
        retry(RetryPolicy.Exponential(maxAttempts = 3, initial = 100.milliseconds))
    }
    step("charge-payment") {
        action     { ctx -> ctx.copy(chargeId = payment.charge(ctx.userId, ctx.amountCents)) }
        compensate { ctx -> payment.refund(ctx.chargeId!!) }
    }
    step("create-shipment") {
        action { ctx -> ctx.copy(shipmentId = shipping.createShipment(ctx.orderId)) }
    }
}

val result: SagaResult<OrderContext> = executor.execute(orderSaga, OrderContext(...))
```

If `create-shipment` throws, compensations for `charge-payment` and `reserve-inventory`
run in reverse order. If the JVM dies between steps, the next instance picks up the
partially-executed saga and resumes from where it left off.

## Why this exists

Distributed sagas show up in every non-trivial microservice. The Kotlin/Spring options
to actually implement them are not great:

- **Axon / Eventuate Tram** — heavyweight, opinionated frameworks. Pull in an event store,
  message broker, and a whole runtime model. Not idiomatic Kotlin.
- **Camunda / Temporal** — fantastic, but they are workflow engines. Separate cluster,
  separate ops surface, and you write workflows in a sidecar process.
- **Roll your own** — most teams do, and reinvent retries, compensation ordering,
  optimistic locking, and resume-on-startup every time.

`spring-saga-kt` is the in-process, lightweight middle ground:

- Pure Kotlin DSL — `step { action { ... }; compensate { ... } }`. No XML, no annotations.
- `suspend` everywhere — natural integration with WebFlux, coroutines, and structured concurrency.
- Persistent by default — every step boundary is durably recorded; resume runs at startup.
- Pluggable storage — JDBC ships in the box; in-memory for tests; bring your own for Redis / R2DBC / Mongo.
- Spring Boot autoconfigure — drop in the starter, declare `@Bean SagaDefinition<Ctx>`, inject `SagaExecutor`.
- Micrometer metrics out of the box.

It is **not** a workflow engine. There are no timers, no signals, no parallel branches.
For a linear forward-then-compensate-on-failure flow — the 80% case — this is the
smallest thing that does the job.

## Install

Maven Central (planned):

```kotlin
dependencies {
    implementation("io.github.sagakt:saga-spring-boot-starter:0.1.0")
}
```

The starter pulls in `saga-core` and `saga-storage-jdbc`. If you only need the core
DSL (e.g., for tests or non-Spring projects), depend on `saga-core` alone.

## Quick start

### 1. Define a context

```kotlin
data class OrderContext(
    val orderId: String,
    val userId: String,
    val amountCents: Long,
    val reservationId: String? = null,
    val chargeId: String? = null,
    val shipmentId: String? = null,
)
```

The context is the durable state of the saga — pick fields you can serialize to JSON
(Jackson is used by default) and that capture everything a compensation needs to
undo a previous step.

### 2. Declare the saga as a Spring bean

```kotlin
@Configuration
class OrderSagaConfig {
    @Bean
    fun orderSaga(
        inventory: InventoryClient,
        payment: PaymentClient,
        shipping: ShippingClient,
    ): SagaDefinition<OrderContext> = saga("order-fulfillment") {
        step("reserve-inventory") {
            action     { ctx -> ctx.copy(reservationId = inventory.reserve(ctx)) }
            compensate { ctx -> ctx.reservationId?.let(inventory::release) }
            retry(RetryPolicy.Exponential(maxAttempts = 3, initial = 100.milliseconds))
        }
        step("charge-payment") {
            action     { ctx -> ctx.copy(chargeId = payment.charge(ctx.userId, ctx.amountCents)) }
            compensate { ctx -> ctx.chargeId?.let(payment::refund) }
        }
        step("create-shipment") {
            action { ctx -> ctx.copy(shipmentId = shipping.createShipment(ctx.orderId)) }
        }
        onComplete { ctx -> log.info("order ${ctx.orderId} fulfilled") }
        onFailure  { ctx, t -> log.warn("order ${ctx.orderId} failed: ${t.message}") }
    }
}
```

The bean name must be unique. The starter auto-registers every `SagaDefinition<*>`
bean into the `SagaDefinitionRegistry` used at resume time.

### 3. Execute

```kotlin
@RestController
class OrderController(
    private val executor: SagaExecutor,
    private val orderSaga: SagaDefinition<OrderContext>,
) {
    @PostMapping("/orders")
    suspend fun place(@RequestBody req: PlaceOrderRequest): ResponseEntity<Any> =
        when (val result = executor.execute(orderSaga, req.toContext())) {
            is SagaResult.Completed          -> ResponseEntity.ok(result.context)
            is SagaResult.Compensated        -> ResponseEntity.unprocessableEntity().body(result)
            is SagaResult.CompensationFailed -> ResponseEntity.status(500).body(result)
        }
}
```

Result is a sealed class — the compiler forces you to handle the three terminal states.

## Persistence model

Every saga execution is a single row in `saga_state`:

| column                | type           | notes                                   |
|-----------------------|----------------|-----------------------------------------|
| `id`                  | varchar PK     | `SagaId` (UUID by default)              |
| `saga_name`           | varchar        | matches `SagaDefinition.name`           |
| `status`              | varchar        | `RUNNING` / `COMPENSATING` / terminal   |
| `context_payload`     | blob           | Jackson-encoded JSON of the context     |
| `context_type`        | varchar        | FQCN, used for sanity-checking resumes  |
| `completed_steps`     | clob (JSON)    | step names in execution order           |
| `current_step_index`  | int            | next step to run                        |
| `last_error`          | clob nullable  | error message if a step failed          |
| `created_at`          | timestamp      |                                         |
| `updated_at`          | timestamp      |                                         |
| `version`             | bigint         | optimistic lock                         |

Each step boundary is one `UPDATE` with `version = version + 1 WHERE version = old`.
A version mismatch throws `OptimisticLockException`, which prevents two concurrent
resumers from driving the same saga.

## Resume on startup

When the application emits `ApplicationReadyEvent`, the starter scans for records
in `RUNNING` or `COMPENSATING` states and resumes them against the registered
`SagaDefinition` of the same name. Sagas whose definition has been removed are
logged and skipped — they require manual triage (you removed the code that knows
how to compensate them).

Tune via:

```yaml
saga:
  resume-on-startup: true       # default
  resume-batch-size: 100        # default
```

## Retries

Per-step retry policies. The executor sleeps via `kotlinx.coroutines.delay`, not
`Thread.sleep`, so retries do not pin a thread:

```kotlin
step("flaky") {
    action { ... }
    retry(RetryPolicy.Exponential(
        maxAttempts = 5,
        initial = 100.milliseconds,
        multiplier = 2.0,
        max = 5.seconds,
        jitter = 0.2,             // 20% random jitter
    ))
    retryOn { it is IOException } // do not retry on non-transient errors
}
```

Retries only apply to *forward* steps. Compensations do not retry by default — if a
compensation fails, the saga lands in `COMPENSATION_FAILED` and surfaces immediately.
This is intentional: silently retrying a failed refund is worse than alerting on it.

## Storage backends

| Backend       | Module                      | Status   |
|---------------|-----------------------------|----------|
| In-memory     | `saga-core`                 | shipped  |
| JDBC          | `saga-storage-jdbc`         | shipped  |
| R2DBC         | `saga-storage-r2dbc`        | planned  |
| Redis         | `saga-storage-redis`        | planned  |
| MongoDB       | `saga-storage-mongo`        | planned  |

Implement `SagaStateRepository` to plug in your own:

```kotlin
interface SagaStateRepository {
    suspend fun insert(record: SagaRecord): SagaRecord
    suspend fun update(record: SagaRecord): SagaRecord   // optimistic-lock on version
    suspend fun findById(id: SagaId): SagaRecord?
    suspend fun findResumable(sagaName: String? = null, limit: Int = 100): List<SagaRecord>
}
```

## Observability

When Micrometer is on the classpath, the starter publishes:

- `saga.started{saga}` / `saga.completed{saga}` / `saga.compensated{saga}` / `saga.compensation_failed{saga}`
- `saga.step.completed{saga,step}` / `saga.step.retried{saga,step}` / `saga.step.failed{saga,step,exception}`
- `saga.compensation.step.completed{saga,step}` / `saga.compensation.step.failed{saga,step,exception}`

All also log at INFO/WARN/ERROR with correlation by `SagaId`.

## Comparison

| Feature                | spring-saga-kt | Axon | Camunda / Temporal | Roll your own |
|------------------------|----------------|------|--------------------|---------------|
| In-process             | ✓              | ✓    | ✗ (separate cluster) | ✓             |
| Kotlin DSL             | ✓              | partial | ✗               | depends       |
| Coroutine-native       | ✓              | ✗    | ✗                  | depends       |
| Durable resume         | ✓              | ✓    | ✓                  | usually not   |
| Parallel branches      | ✗              | ✓    | ✓                  | depends       |
| Timers / signals       | ✗              | ✓    | ✓                  | depends       |
| Spring Boot autoconfig | ✓              | ✓    | ✓                  | ✗             |

If you need timers, parallel branches, or human-in-the-loop, pick Temporal or Camunda.
If you need a linear durable saga and want it in 50 lines of idiomatic Kotlin, this.

## Running the example

```bash
./gradlew :examples:order-saga:bootRun
```

```bash
# happy path
curl -s -X POST localhost:8080/orders -H 'content-type: application/json' \
  -d '{"orderId":"o-1","userId":"u-1","sku":"SKU-A","quantity":1,"amountCents":1999}'

# triggers compensation (amount over limit -> payment declined)
curl -s -X POST localhost:8080/orders -H 'content-type: application/json' \
  -d '{"orderId":"o-2","userId":"u-1","sku":"SKU-A","quantity":1,"amountCents":2000000}'
```

## Status

Pre-1.0. The DSL surface and `SagaStateRepository` contract should be considered
stable but minor breaking changes are possible until 1.0.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
