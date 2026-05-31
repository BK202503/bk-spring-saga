# Kotlin Weekly submission

**As of late 2026, Kotlin Weekly no longer uses a web form.** The
`www.kotlinweekly.net/submit-link` URL is dead (SSL cert mismatch). Submissions
are sent by email — confirmed on <https://kotlinweekly.net/> (the live front
page links to issues via Mailchimp and lists the submission address).

## How to submit

Send an email to:

- **To:** `mailinglist@kotlinweekly.net`
- **Subject:** `Link for submission - Kotlin Weekly`

## Email body template (copy-paste, then send)

```
Hi Kotlin Weekly team,

I'd like to submit a new Kotlin library for consideration in an upcoming issue.

Title: spring-saga-kt — coroutine-native Saga orchestrator for Spring Boot
URL:   https://github.com/BK202503/bk-spring-saga
Release notes: https://github.com/BK202503/bk-spring-saga/releases/tag/v0.1.0
License: Apache 2.0
Category: Libraries

One-line description:
A Kotlin-first, coroutine-native Saga orchestrator for Spring Boot — durable
resume of interrupted sagas, JDBC (H2/Postgres) and Kafka event modules, and a
Spring Boot autoconfigure starter.

Why it might be of interest to readers:
Distributed Saga implementations in the Kotlin/Spring world today either pull
in a heavyweight framework (Axon) or a separate workflow cluster (Temporal,
Camunda). spring-saga-kt fills the in-process middle ground: every action and
compensation is a suspend function, the executor persists progress at every
step boundary with optimistic locking, and orphaned sagas are resumed on
ApplicationReadyEvent. The DSL is small enough to fit on one slide:

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

CI runs the JDBC suite against real PostgreSQL and the event suite against a
real Kafka broker via testcontainers. v0.1.0 ships today on JitPack; Maven
Central is in progress.

Thanks for considering it, and for the newsletter — long-time reader.

Best,
<Your Name>
GitHub: https://github.com/BK202503
```

## Tips

- If you have a launch blog post (the one in `drafts/launch-post-en.md` ported
  to dev.to / Medium), link to that instead of the raw repo — the editor tends
  to favor articles over bare GitHub URLs.
- Sundays mid-morning UTC tends to align well with the next issue's cut-off.
- Don't bcc multiple newsletters in one email — one submission per email looks
  more deliberate.
- You can re-submit on each minor release — they're fine with it.
