# v0.1.0 — initial public release

`spring-saga-kt` is a Kotlin-first, coroutine-native Saga orchestrator for
Spring Boot. This is the first public cut: the DSL surface, persistence
contract, and event SPI are usable end-to-end but considered pre-1.0 — minor
breaking changes possible until 1.0.

## What's in the box

- **`saga-core`** — pure Kotlin DSL (`saga<Ctx>("name") { step { ... } }`),
  suspend-based executor, in-memory state repository, resumer.
- **`saga-storage-jdbc`** — durable state with optimistic-lock-per-step.
  Auto-detects H2 / PostgreSQL via `DataSource` metadata; brings dialect-aware
  schemas. Verified against real Postgres in CI via testcontainers.
- **`saga-events-kafka`** — publishes `SagaEvent`s (Started / StepCompleted /
  StepFailed / Completed / Compensated / CompensationFailed) to a Kafka topic.
  Same `sagaId` key per saga so event order is preserved per partition.
  Verified against a real Kafka broker in CI via testcontainers.
- **`saga-spring-boot-starter`** — autoconfigure, properties, Micrometer
  metrics, `ApplicationReadyEvent` resume worker that picks up sagas
  interrupted by a JVM restart.
- **`examples/order-saga`** — runnable REST demo (reserve / charge / ship)
  showing the success path and the compensation path.

## Highlights vs. existing options

| Feature                | spring-saga-kt | Axon | Camunda/Temporal | Roll your own |
|------------------------|----------------|------|------------------|---------------|
| In-process             | ✓              | ✓    | ✗ (cluster)      | ✓             |
| Kotlin-first DSL       | ✓              | partial | ✗             | depends       |
| Coroutine-native       | ✓              | ✗    | ✗                | depends       |
| Durable resume         | ✓              | ✓    | ✓                | usually not   |
| Spring Boot autoconfig | ✓              | ✓    | ✓                | ✗             |

## Install (JitPack)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0")
    // optional, for publishing lifecycle events to Kafka:
    implementation("com.github.BK202503.bk-spring-saga:saga-events-kafka:v0.1.0")
}
```

## Verified backends

- H2 (unit), PostgreSQL 16 (testcontainers, CI)
- Kafka via Apache Kafka 3.5 (testcontainers, CI)

## Known limitations

- No parallel branches yet — sagas are strictly sequential.
- No timers / signals — for those use Temporal or Camunda.
- Maven Central publishing is still planned; install via JitPack for now.

## Thanks

First release. Issues, PRs, and use-case reports very welcome:
<https://github.com/BK202503/bk-spring-saga/issues>
