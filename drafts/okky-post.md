# OKKY 게시글

게시판: <https://okky.kr/articles?category=projects> → "프로젝트" 카테고리

## 제목

```
[OSS] 코틀린/스프링 부트용 코루틴 네이티브 Saga 라이브러리 만들었습니다 — spring-saga-kt
```

## 태그

`Kotlin` `Spring` `Spring Boot` `MSA` `Saga` `오픈소스`

## 본문 (Markdown)

```markdown
안녕하세요. 코틀린/스프링 부트 환경에서 분산 트랜잭션(Saga) 처리할 때 매번 비슷한
코드를 반복해서 짜는 게 아쉬워 라이브러리로 정리해봤습니다.

**spring-saga-kt** — Apache 2.0, v0.1.0 막 릴리즈했습니다.
👉 https://github.com/BK202503/bk-spring-saga

## 왜 만들었나

Saga 패턴 구현 옵션이 코틀린/스프링에는 사실상 셋뿐이었습니다.

- **Axon** — 무거움. 이벤트 스토어와 자체 런타임에 묶이고, 코틀린/코루틴 친화적이지
  않음.
- **Temporal / Camunda** — 별도 클러스터 운영 필요. 코드도 사이드카 프로세스.
- **직접 구현** — 재시도, 보상 순서, 낙관적 락, 재시작 복구를 매번 새로 만듦.

그래서 직접 구현할 때마다 짜던 ~400줄을 뜯어내서 라이브러리로 만들었습니다.

## 어떻게 생겼나

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

`create-shipment`에서 예외 나면 → `charge-payment`, `reserve-inventory` 순으로
보상 실행. JVM이 step 중간에 죽으면 → 다음 인스턴스가 영속화된 레코드 읽어서
이어 실행.

## 주요 결정

- **모든 action/compensation은 suspend 함수**. 재시도 지연은 코루틴 delay라
  스레드 안 묶임.
- **매 step 경계마다 영속화** + 낙관적 락. 두 인스턴스가 같은 saga 못 잡음.
- **sealed `SagaResult`** (Completed / Compensated / CompensationFailed) — 컴파일러가
  세 케이스 다 처리하도록 강제. 실패한 환불을 모르고 넘어가는 일 불가.
- **보상은 기본적으로 재시도 안 함**. 실패한 환불을 조용히 재시도하는 게 더 위험.

## 구성

- `saga-core` — DSL, executor, 인메모리 저장소
- `saga-storage-jdbc` — H2/PostgreSQL 영속 (자동 감지)
- `saga-events-kafka` — 라이프사이클 이벤트 Kafka 발행
- `saga-spring-boot-starter` — 자동 설정, Micrometer 메트릭, 부팅 시 자동 재개

CI는 PostgreSQL 16과 Kafka를 testcontainers로 실제 띄워서 검증.

## 설치

```kotlin
repositories { mavenCentral(); maven("https://jitpack.io") }
dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0")
}
```

Maven Central은 작업 중, 지금은 JitPack 경유.

## 안 어울리는 경우

병렬 분기, 타이머, 시그널, 휴먼 태스크 필요하시면 Temporal/Camunda 가시는 게
맞습니다. 이건 그냥 선형 saga의 80% 케이스를 최소 코드로 잡는 게 목적입니다.

---

피드백 / 이슈 / PR 환영합니다. 특히 "이런 케이스에서 부족하더라" 류의 실사용
피드백이 제일 도움이 됩니다. 감사합니다!

레포: https://github.com/BK202503/bk-spring-saga
```

## 게시 팁

- 평일 오전 9–11시가 OKKY 트래픽 피크.
- 댓글로 "왜 X 안 쓰고 이거 쓰냐" 류 질문 오면 README 비교표 링크하면서 짧게 답변.
- 일주일 정도 후에 사용 후기 / 별표 수 적어서 부담스럽지 않은 정도로 한 번 더
  업데이트 글 (선택).
