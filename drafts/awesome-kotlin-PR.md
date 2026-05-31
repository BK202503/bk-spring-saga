# awesome-kotlin PR

Target repo: <https://github.com/KotlinBy/awesome-kotlin>
(They also accept entries at <https://github.com/Heapy/awesome-kotlin> — submit
to both if you want maximum coverage.)

## 1. Fork + add entry

`Linux/Windows/Mac` users:

```bash
gh repo fork KotlinBy/awesome-kotlin --clone
cd awesome-kotlin
git checkout -b add-spring-saga-kt
```

## 2. Edit `links/data.yml` (or the appropriate sub-file)

Find the section for **Spring / Frameworks / Saga / Microservices** (existing
section names vary; pick the closest). Insert this entry — keep alphabetical
order within the section:

```yaml
- name: spring-saga-kt
  desc: "Kotlin-first, coroutine-native Saga orchestrator for Spring Boot. Durable resume, JDBC (H2/Postgres) and Kafka event modules, autoconfigure starter."
  href: https://github.com/BK202503/bk-spring-saga
  tags:
    - Spring
    - Coroutines
    - Saga
    - Microservices
```

If the project uses a different schema (some sub-lists are plain Markdown
bullets), the equivalent line:

```markdown
- [spring-saga-kt](https://github.com/BK202503/bk-spring-saga) — Kotlin-first,
  coroutine-native Saga orchestrator for Spring Boot. Durable resume, JDBC
  (H2/Postgres) and Kafka event modules, autoconfigure starter.
```

## 3. Commit + push + open PR

```bash
git add -A
git commit -m "Add spring-saga-kt — coroutine-native Saga orchestrator for Spring Boot"
git push -u origin add-spring-saga-kt
gh pr create --title "Add spring-saga-kt — coroutine-native Saga orchestrator for Spring Boot" \
  --body "$(cat <<'EOF'
### What

Adds [spring-saga-kt](https://github.com/BK202503/bk-spring-saga) to the Spring
/ microservices section.

### Why it fits this list

- Apache 2.0, public on GitHub since 2026-05-31, v0.1.0 tagged.
- Kotlin-first DSL, every action/compensation is a suspend function.
- Spring Boot 3 autoconfigure with Micrometer metrics and durable resume on
  ApplicationReadyEvent.
- JDBC (H2 + Postgres, schema dialect auto-detected) and Kafka event publisher
  modules ship in the same release.
- CI runs the JDBC suite against real Postgres and the event suite against a
  real Kafka broker via testcontainers.

### Comparable existing entries

This fills a gap not covered by any current Saga / workflow entries on the
list — the existing options (Axon, Temporal SDK) are either Java-first or
out-of-process workflow engines.

### Checklist

- [x] Project license is OSI-approved (Apache 2.0).
- [x] Project has a public release on GitHub.
- [x] Description is one sentence and under 200 chars.
- [x] Entry added in the matching section, alphabetically.
EOF
)"
```
