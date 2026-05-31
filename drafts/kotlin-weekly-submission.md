# Kotlin Weekly submission

URL: <https://www.kotlinweekly.net/submit-link>

Kotlin Weekly is a curated newsletter (~10k subscribers, every Sunday). The
editor (Antonio Leiva) picks from submissions. Submissions usually run in the
next 1–2 issues if accepted.

## Form fields

**Your name:** `<your name>`

**Your email:** `<your email>`

**Link:** `https://github.com/BK202503/bk-spring-saga`
*(or, if your launch blog post is live, link to the post instead — Kotlin
Weekly prefers articles with context over raw repos)*

**Category:** `Libraries` (if a launch repo) or `Articles` (if a blog post)

**Short description (one sentence, ~200 chars):**

> spring-saga-kt is a Kotlin-first, coroutine-native Saga orchestrator for
> Spring Boot — durable resume, JDBC and Kafka modules, autoconfigure starter,
> Apache 2.0.

**Longer description (a paragraph, optional but recommended):**

> Coroutine-native Saga orchestrator for Spring Boot 3. A small DSL
> (`saga<Ctx>("name") { step { action {...}; compensate {...}; retry(...) } }`)
> with optimistic-lock-per-step persistence, automatic resume of orphaned
> sagas on application startup, and a Kafka event SPI for downstream services.
> Aimed at the in-process middle ground between Axon (heavyweight) and Temporal
> (separate cluster).

## Tips

- If you have a launch post (`drafts/launch-post-en.md` ported to dev.to or
  Medium), submit the post URL — newsletters favor articles over raw repos.
- Re-submit each minor release; Kotlin Weekly is fine with that.
- Antonio sometimes replies asking for clarifications — keep an eye on the
  email you submitted.
