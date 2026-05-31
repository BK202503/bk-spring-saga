# r/Kotlin submission

Subreddit: <https://www.reddit.com/r/Kotlin/>
Cross-post: <https://www.reddit.com/r/SpringBoot/> (after the r/Kotlin post lands)

## Title

```
spring-saga-kt — a coroutine-native Saga orchestrator for Spring Boot
```

(Reddit ranks heavily on title clarity. Don't put "[Show]" / emoji / promo-y
language — the description does that work.)

## Flair

`Library` (r/Kotlin has this flair).

## Body (Markdown)

```markdown
Hi r/Kotlin,

I built a small library to scratch an itch I keep running into in Spring Boot
microservices: there is no good, lightweight, in-process Saga orchestrator
that actually feels Kotlin-native. Axon is heavy, Temporal/Camunda need a
separate cluster, and rolling-your-own reinvents retries + compensation
ordering + resume every single time.

So [spring-saga-kt](https://github.com/BK202503/bk-spring-saga) is the small
thing I wish existed:

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

**Highlights:**

- Every action / compensation is a `suspend` function. Retries use
  `kotlinx.coroutines.delay`, not `Thread.sleep`.
- Persistence at every step boundary with optimistic locking — JVM crash
  mid-saga → next instance resumes from the same step on `ApplicationReadyEvent`.
- Sealed `SagaResult` (`Completed` / `Compensated` / `CompensationFailed`) so
  the compiler forces you to handle failed compensations explicitly.
- Spring Boot 3 autoconfigure, Micrometer metrics, JDBC + Kafka modules.
- CI runs the JDBC suite against real Postgres and the event suite against a
  real Kafka broker via testcontainers.

**Install (JitPack, Maven Central pending):**

```kotlin
repositories { mavenCentral(); maven("https://jitpack.io") }
dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0")
}
```

**What it is NOT** — a workflow engine. No parallel branches, no timers, no
signals. If you need those, pick Temporal or Camunda. This is the smallest
thing that handles a durable linear A→B→C with rollback.

Apache 2.0, v0.1.0 just shipped. Issues, PRs, and especially "I tried it and
here's what's missing" feedback very welcome.

Repo: https://github.com/BK202503/bk-spring-saga
```

## Posting tips

- Best time for r/Kotlin: weekday 09:00–11:00 UTC.
- Don't link-spam the comments — answer technical questions only.
- If anyone asks "why not just X", thank them, link the Comparison section of
  the README, and move on.
- After ~24h, cross-post to r/SpringBoot with a short note: "Cross-posting
  from r/Kotlin in case it's useful here too."
