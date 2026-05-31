# Contributing

Thanks for considering a contribution. This project is small and opinionated —
please open an issue before starting on anything substantial so we can align on
direction.

## Quick start

```bash
git clone https://github.com/BK202503/bk-spring-saga.git
cd bk-spring-saga
./gradlew build
```

JDK 17 required. The Gradle wrapper handles Gradle itself.

## Running the tests

```bash
./gradlew test                                    # unit + autoconfigure suites
SAGA_REQUIRE_DOCKER=1 ./gradlew test              # also Postgres + Kafka via testcontainers
```

The Docker-backed suites skip gracefully without `SAGA_REQUIRE_DOCKER=1` and a
running Docker daemon, so the default run stays fast on developer machines.

On macOS with Docker Desktop, if you see "Could not find a valid Docker
environment", create a one-line `~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/<you>/.docker/run/docker.sock
```

## Layout

| Module                     | Scope                                                     |
|----------------------------|-----------------------------------------------------------|
| `saga-core`                | Pure Kotlin DSL, executor, SPIs, in-memory repository.    |
| `saga-storage-jdbc`        | Durable state via JDBC + Jackson; H2 / Postgres schemas.  |
| `saga-events-kafka`        | Lifecycle event publisher + autoconfigure.                |
| `saga-spring-boot-starter` | Autoconfigure wiring everything together.                 |
| `examples/order-saga`      | Runnable demo. Not published.                             |

`saga-core` must stay Kotlin-only with no Spring or Jackson dependency. Anything
that requires those belongs in `saga-storage-jdbc` or a new module.

## Adding a storage backend

1. New module `saga-storage-<name>` depending on `saga-core`.
2. Implement `SagaStateRepository` with optimistic locking on `SagaRecord.version`.
3. Share the contract suite by reusing `jdbcContractTests`-style helpers (port
   the H2/Postgres pattern). PRs that only add unit tests against a real
   datastore are welcome; CI will run them under `SAGA_REQUIRE_DOCKER=1`.

## Style

- Kotlin official style, 4-space indent.
- Public API uses `suspend` functions rather than `CompletableFuture` / blocking calls.
- No annotations-driven magic in `saga-core`. Stay DSL-first.
- New public types get a one-paragraph KDoc explaining intent and invariants.
- Tests use Kotest `StringSpec`.

## Pull requests

- One concern per PR. Refactors and feature work go in separate PRs.
- Link an issue (or open one) describing the motivation.
- CI must pass; testcontainers suites included.
- Update `CHANGELOG.md` under `## [Unreleased]`.

## License

By contributing you agree that your contributions will be licensed under the
Apache License 2.0 (the same license as the project).
