# spring-saga-kt — 코틀린/스프링 부트용 코루틴 네이티브 Saga 오케스트레이터

> **TL;DR** — [`spring-saga-kt`](https://github.com/BK202503/bk-spring-saga)
> 만들었습니다. 코틀린 + 스프링 부트 환경에서 타입 안전한 suspend-네이티브 Saga DSL,
> 영속 재개, Kafka 이벤트 SPI를 가벼운 인-프로세스 라이브러리로 묶은 것. Apache-2.0,
> v0.1.0이 오늘 JitPack에 올라갔습니다.

## 왜 만들었나

마이크로서비스에서 한 요청 안에 두 가지 이상의 작업을 처리하는 순간 분산 트랜잭션이
필요해집니다. 답은 거의 항상 **Saga 패턴** — 로컬 트랜잭션들을 순차로 묶고, 실패 시
반대 순서로 보상(compensation)을 돌리는 흐름.

화이트보드에 그리기는 쉽지만 운영 환경에서 제대로 굴리려면 골치가 아픕니다. 코틀린 +
스프링 부트 기준 현실적인 옵션은 셋:

- **Axon Framework** — 동작은 하지만 무거움. 이벤트 스토어와 자체 런타임에 묶이고,
  API는 코틀린/코루틴을 전제로 설계되지 않음.
- **Temporal / Camunda 8** — 워크플로 엔진으로는 훌륭하지만, **별도 클러스터** 운영
  부담. 코드도 사이드카 프로세스에서 돌아감.
- **직접 구현** — 대부분의 팀이 선택. 매번 재시도 / 보상 순서 / 낙관적 락 /
  재시작 복구를 새로 만듦.

저도 직접 구현할 때마다 비슷한 400줄을 또 짜고 있어서, 그걸 뜯어내어 라이브러리로
만들었습니다.

## `spring-saga-kt`는 뭔가

JVM 안에서 도는 **선형 saga**입니다. 정방향 step → 실패 시 역순 compensation.
이디오메틱 코틀린 DSL:

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

`create-shipment`에서 예외가 나면 `charge-payment` → `reserve-inventory` 순으로
보상이 자동 실행됩니다. JVM이 step 중간에 죽어도, 다음 인스턴스가 영속화된 레코드를
읽어 `RUNNING` 상태인 saga를 그 지점부터 이어 돌립니다.

## 의도적인 설계 결정

- **sealed `SagaResult`.** `Completed` / `Compensated` / `CompensationFailed`
  세 가지로 종결됩니다. 컴파일러가 세 케이스 모두 핸들링하도록 강제하므로,
  실패한 환불을 모르고 넘어가는 일이 발생할 수 없습니다.
- **매 step마다 영속화.** 성공한 step마다 낙관적 락이 걸린 `UPDATE` 한 번. 두
  인스턴스가 동시에 같은 saga를 진행하려 들어도 version 경쟁에서 둘 중 하나만 이기죠.
- **재시도는 정방향만.** 보상은 기본적으로 재시도하지 **않습니다**. 실패한 환불을
  조용히 재시도하는 것보다 즉시 `COMPENSATION_FAILED` 상태로 표면화시키고 사람이
  보게 하는 편이 안전.
- **코루틴 네이티브.** 모든 action / compensation이 `suspend` 함수. 재시도 지연은
  `kotlinx.coroutines.delay` — `Thread.sleep`이 아니어서 스레드가 묶이지 않습니다.

## v0.1.0에 들어있는 것

| 모듈                          | 범위                                                        |
|-------------------------------|------------------------------------------------------------|
| `saga-core`                   | DSL, executor, 인메모리 저장소, 이벤트 SPI.                |
| `saga-storage-jdbc`           | 영속 상태. H2 / Postgres 자동 감지. CI에서 Postgres 검증.   |
| `saga-events-kafka`           | saga ID 키 기반 Kafka 이벤트 발행 (순서 보장).             |
| `saga-spring-boot-starter`    | 자동 설정, Micrometer 메트릭, 부팅 시 자동 재개.            |
| `examples/order-saga`         | reserve / charge / ship 3단계 REST 데모.                    |

CI는 PostgreSQL 16과 Kafka 브로커를 testcontainers로 실제 띄워 통합 테스트를
돌립니다. 로컬에서는 Docker 없으면 자동으로 스킵.

## 이걸 쓰면 좋은 경우

- 같은 JVM 안에서 도는 **영속 saga**가 필요할 때.
- 흐름이 **선형** — A → B → C, 실패 시 역순 보상.
- 코틀린 / 코루틴 / Spring Boot 자동 설정이 자연스러웠으면 할 때.

## 안 어울리는 경우

- **병렬 분기**, **타이머**, **시그널**, **휴먼 태스크**가 필요하면 Temporal /
  Camunda를 쓰세요.
- **별도 프로세스**에서 워크플로를 돌리고 싶다면 같음.
- Spring Boot 안 쓴다면 `saga-core` 단독으로 가능하지만 효용은 줄어듭니다.

## 설치

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0")
    // Kafka 이벤트 발행이 필요하면:
    implementation("com.github.BK202503.bk-spring-saga:saga-events-kafka:v0.1.0")
}
```

Maven Central은 작업 중. 오늘 시점에는 JitPack 경유.

## 다음 단계

- `saga-storage-r2dbc` — 논블로킹 영속화
- `saga-storage-redis` — 가벼운 / 샤딩 sagas
- `parallel { step(); step() }` 블록
- Sonatype OSSRH 등록 → Maven Central 배포

이슈 / PR / "내가 써봤더니 이런 게 깨졌더라" 류 피드백 모두 환영합니다:
<https://github.com/BK202503/bk-spring-saga>.

코틀린/스프링에서 Saga 패턴 어떻게 구현하고 계신가요? 댓글로 알려주세요.
