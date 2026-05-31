# spring-saga-kt — a coroutine-native Saga orchestrator for Spring Boot

> **TL;DR** — I built [`spring-saga-kt`](https://github.com/BK202503/bk-spring-saga),
> a small Apache-2.0 library that gives Kotlin + Spring Boot apps a typed,
> suspend-native Saga DSL with durable resume and a Kafka event SPI. v0.1.0
> just shipped on JitPack.

## The pain

Distributed transactions show up the moment a microservice does more than one
thing in a single request. The canonical pattern is the **Saga**: a sequence of
local steps, each with a compensating action that runs in reverse on failure.
Simple to draw on a whiteboard, painful to get right in production.

In the Kotlin + Spring Boot world today, your realistic options are:

- **Axon Framework** — works, but heavyweight. You buy into an event store, an
  opinionated runtime, and an API that was not designed for Kotlin or
  coroutines.
- **Temporal / Camunda 8** — fantastic engines, but they are *workflow
  systems*. You run a separate cluster, write code in a sidecar process, and
  invest real ops budget.
- **Roll your own** — most teams do, and reinvent retries, compensation
  ordering, optimistic locking, and resume-on-restart every single time.

Every time I rolled my own I shipped roughly the same 400 lines of Kotlin. So
I extracted it.

## What `spring-saga-kt` is

A linear forward-then-compensate-on-failure saga, in-process, idiomatic Kotlin:

```kotlin
val orderSaga = saga<OrderContext>("order-fulfillment") {
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
}

val result = executor.execute(orderSaga, OrderContext(...))
```

If `create-shipment` throws, the compensations for `charge-payment` and
`reserve-inventory` run in reverse order. If the JVM dies between steps, the
next instance reads the durable row, finds it in `RUNNING`, and resumes from
where the crash happened.

## The design choices

A few decisions made on purpose:

- **Sealed `SagaResult`.** Either `Completed`, `Compensated`, or
  `CompensationFailed`. The compiler forces you to handle the three terminal
  states — you cannot accidentally ignore a refund that failed.
- **Persistent at every step boundary.** Each successful step writes a single
  optimistic-locked `UPDATE`. Two concurrent resumers cannot drive the same
  saga, because the second one loses the version race.
- **Retries are forward-only.** Compensations *don't* retry by default.
  Silently retrying a failed refund is worse than alerting on it, so the saga
  surfaces a `COMPENSATION_FAILED` and lets a human decide.
- **Coroutine-native.** Every action and compensation is a `suspend` function.
  Retries use `kotlinx.coroutines.delay`, not `Thread.sleep`, so a retrying
  saga does not pin a thread.

## What's in the box at v0.1.0

| Module                       | Scope                                                          |
|------------------------------|----------------------------------------------------------------|
| `saga-core`                  | DSL, executor, in-memory repo, event SPI.                      |
| `saga-storage-jdbc`          | Durable state. H2 / Postgres auto-detected; CI runs Postgres.  |
| `saga-events-kafka`          | Lifecycle events on a Kafka topic, partitioned by saga id.     |
| `saga-spring-boot-starter`   | Autoconfigure, Micrometer metrics, resume on `ApplicationReady`. |
| `examples/order-saga`        | Runnable REST demo of reserve / charge / ship.                 |

CI runs the JDBC suite against a real PostgreSQL 16 and the event suite against
a real Kafka broker, both via testcontainers. Skip locally when Docker is
absent; required in CI.

## When this is the right tool

- You need a durable saga **in the same JVM** as your domain code.
- Your flow is **linear**: A then B then C, compensate in reverse.
- You want Kotlin idioms, coroutines, and Spring Boot autoconfigure.

## When it isn't

- You need **parallel branches**, **timers**, **signals**, or **human tasks** —
  use Temporal or Camunda.
- You want **out-of-process** workflow execution — same.
- You are not on Spring Boot — saga-core works standalone, but the value is
  much smaller.

## Install

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0")
    // optional, for Kafka event publishing:
    implementation("com.github.BK202503.bk-spring-saga:saga-events-kafka:v0.1.0")
}
```

Maven Central is planned; JitPack ships v0.1.0 today.

## What's next

- `saga-storage-r2dbc` for non-blocking persistence
- `saga-storage-redis` for ephemeral / sharded sagas
- `parallel { step(); step() }` blocks
- Sonatype OSSRH / Maven Central namespace

Issues, PRs, and especially "I tried it and here's what broke" reports very
welcome: <https://github.com/BK202503/bk-spring-saga>.

If you ship Sagas in Kotlin/Spring today, I would love to hear how you do it.
