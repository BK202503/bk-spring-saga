# Changelog

All notable changes to this project will be documented in this file. The format
is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] — 2026-05-31

Initial public release.

### Added
- `saga-core`: Kotlin DSL (`saga<Ctx>("name") { step { ... } }`), coroutine-based
  executor, in-memory state repository, retry policies (None / Fixed /
  Exponential with jitter), `SagaResult` sealed class, resumer.
- `saga-core`: `SagaEvent` sealed hierarchy and `SagaEventPublisher` SPI.
  Executor emits events at every step / saga boundary.
- `saga-storage-jdbc`: durable state with optimistic-lock-per-step. Auto-detect
  for H2 and PostgreSQL via `DataSource` metadata, dialect-specific schemas.
- `saga-events-kafka`: `KafkaSagaEventPublisher` (Jackson JSON payload,
  `sagaId` key for per-saga ordering, `event_type` / `saga_name` headers) +
  self-contained Spring Boot autoconfigure.
- `saga-spring-boot-starter`: autoconfigure, `SagaProperties`, Micrometer
  metrics, `ApplicationReadyEvent`-based resume worker.
- `examples/order-saga`: runnable REST demo of a three-step saga with
  compensations and retries.
- CI: GitHub Actions workflow runs the full suite including testcontainers
  PostgreSQL and Kafka under `SAGA_REQUIRE_DOCKER=1`.
- Docs: README with motivation, install (JitPack), persistence model,
  comparison table, resume semantics, event topic schema.

### Limitations
- Parallel step branches not supported (sequential only).
- No timer / signal semantics — for those use Temporal / Camunda.
- Maven Central publication pending; install via JitPack for now.

[Unreleased]: https://github.com/BK202503/bk-spring-saga/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/BK202503/bk-spring-saga/releases/tag/v0.1.0
