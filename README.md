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

### Option A — JitPack (recommended while pre-1.0)

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:main-SNAPSHOT")
}
```

Or pin to a release tag once published (e.g. `v0.1.0`):

```kotlin
implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0")
```

`main-SNAPSHOT` always tracks the latest `main` — convenient for trying it out, but
prefer a tag in production so your builds are reproducible. JitPack triggers a build
the first time anyone requests a new coordinate, so the first download is slower.

The starter pulls in `saga-core` and `saga-storage-jdbc`. If you only need the core
DSL (no Spring), depend on `saga-core` alone:

```kotlin
implementation("com.github.BK202503.bk-spring-saga:saga-core:main-SNAPSHOT")
```

For Maven (`pom.xml`):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.BK202503.bk-spring-saga</groupId>
    <artifactId>saga-spring-boot-starter</artifactId>
    <version>main-SNAPSHOT</version>
</dependency>
```

### Option B — Local install (for hacking on the library itself)

Clone, install to your local Maven repository, then depend on the snapshot
coordinate:

```bash
git clone https://github.com/BK202503/bk-spring-saga.git
cd bk-spring-saga
./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral() }

dependencies {
    implementation("io.github.sagakt:saga-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

### Option C — Maven Central (planned)

`io.github.sagakt:saga-spring-boot-starter:0.1.0` will be the coordinate once the
Sonatype namespace is verified. Tracking issue welcome.

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

| Backend       | Module                      | Tested against        | Status   |
|---------------|-----------------------------|-----------------------|----------|
| In-memory     | `saga-core`                 | unit                  | shipped  |
| JDBC — H2     | `saga-storage-jdbc`         | unit                  | shipped  |
| JDBC — Postgres | `saga-storage-jdbc`       | testcontainers (CI)   | shipped  |
| R2DBC         | `saga-storage-r2dbc`        | —                     | planned  |
| Redis         | `saga-storage-redis`        | —                     | planned  |
| MongoDB       | `saga-storage-mongo`        | —                     | planned  |

The JDBC backend auto-detects the SQL dialect from `DataSource` metadata and picks
the matching schema. To override, pass `dialect = SqlDialect.POSTGRESQL` explicitly
when constructing `JdbcSagaStateRepository`.

Implement `SagaStateRepository` to plug in your own:

```kotlin
interface SagaStateRepository {
    suspend fun insert(record: SagaRecord): SagaRecord
    suspend fun update(record: SagaRecord): SagaRecord   // optimistic-lock on version
    suspend fun findById(id: SagaId): SagaRecord?
    suspend fun findResumable(sagaName: String? = null, limit: Int = 100): List<SagaRecord>
}
```

## Event publishing (Kafka)

Drop in `saga-events-kafka` to publish lifecycle events to a Kafka topic so
downstream services can react to saga outcomes without polling state:

```kotlin
dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:main-SNAPSHOT")
    implementation("com.github.BK202503.bk-spring-saga:saga-events-kafka:main-SNAPSHOT")
    implementation("org.springframework.kafka:spring-kafka")
}
```

```yaml
saga:
  events:
    kafka:
      enabled: true
      topic: saga.events
```

Each saga produces a strictly-ordered stream of events on the same partition (key
= `sagaId`):

| Event                  | Payload fields                                            |
|------------------------|-----------------------------------------------------------|
| `Started`              | sagaId, sagaName, occurredAt                              |
| `StepCompleted`        | + stepName, attempts                                      |
| `StepFailed`           | + stepName, errorType, errorMessage                       |
| `Completed`            | (terminal success)                                        |
| `Compensated`          | + failedStep, causeType, causeMessage                     |
| `CompensationFailed`   | + compensationStep, errorType, errorMessage               |

Kafka record headers `event_type` and `saga_name` are set so consumers can filter
without parsing the body.

If Kafka is temporarily unavailable, the executor logs the publish failure and
continues — saga progress and the state row are durable independently of the
broker, so a Kafka outage cannot break the saga itself.

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

## Testing

`./gradlew test` runs the unit suites (saga-core, H2 against the JDBC backend,
the Spring Boot autoconfigure test).

Integration suites that require Docker — PostgreSQL via testcontainers, Kafka
via testcontainers — skip automatically when no Docker daemon is reachable, so
local builds stay green. CI sets `SAGA_REQUIRE_DOCKER=1` to make those same
suites fail loudly instead of skipping, ensuring the contract is exercised on
every push. See `.github/workflows/ci.yml`.

To run the Docker-backed suites locally:

```bash
SAGA_REQUIRE_DOCKER=1 ./gradlew :saga-storage-jdbc:test :saga-events-kafka:test
```

If you are on macOS with Docker Desktop and see "Could not find a valid Docker
environment", point testcontainers at the right socket by writing a one-line
`~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/<you>/.docker/run/docker.sock
```

## Status

Pre-1.0. The DSL surface and `SagaStateRepository` contract should be considered
stable but minor breaking changes are possible until 1.0.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
