# GeekNews 게시글

URL: <https://news.hada.io/new>

GeekNews는 한국판 Hacker News로 짧은 제목 + 요약 형식. 자기 프로젝트는 "Show GN"
프리픽스로 올리는 게 관행. 제목 + summary(짧음) + 본문 구조.

## 제목

```
Show GN: spring-saga-kt — 코틀린/스프링용 코루틴 네이티브 Saga 오케스트레이터
```

## URL

```
https://github.com/BK202503/bk-spring-saga
```

## 한 줄 요약 (summary, 약 200자)

```
코틀린 + 스프링 부트 환경에서 분산 트랜잭션(Saga) 처리를 위한 인-프로세스 라이브러리.
모든 action/compensation이 suspend 함수, 매 step 영속화 + 낙관적 락 기반 자동 재개,
JDBC(H2/Postgres) · Kafka 이벤트 모듈, Spring Boot 자동 설정 포함. Apache 2.0.
```

## 본문 (Markdown)

```markdown
- 코틀린/스프링 부트 MSA에서 Saga 패턴 직접 구현할 때마다 같은 400줄을 반복해서
  짜고 있다는 걸 깨달아 추출.
- Axon은 무겁고, Temporal/Camunda는 별도 클러스터 필요. 그 사이 "인-프로세스 +
  코틀린 네이티브"를 채우는 게 목적.
- DSL: `saga<Ctx>("name") { step { action {...}; compensate {...}; retry(...) } }`
- 매 step 영속화 + version 기반 낙관적 락. JVM 죽어도 재시작 시
  ApplicationReadyEvent에서 자동 이어 실행.
- sealed SagaResult — Completed / Compensated / CompensationFailed. 컴파일러가
  세 케이스 핸들링 강제.
- saga-events-kafka 모듈로 라이프사이클 이벤트를 Kafka 토픽에 발행 (saga ID 키,
  순서 보장).
- CI는 PostgreSQL 16, Kafka 3.5를 testcontainers로 실제로 띄워 검증.
- v0.1.0 JitPack에서 즉시 사용 가능. Maven Central은 작업 중.

샘플 코드와 비교표는 README에 있습니다.

- 레포: https://github.com/BK202503/bk-spring-saga
- 영문 launch post: (작성 후 dev.to / Medium 링크 추가)
- 한글 launch post: (작성 후 velog 링크 추가)

피드백 / 이슈 / PR 환영. 특히 실사용 후 "이게 부족하다" 류 의견 환영합니다.
```

## 팁

- GeekNews는 짧고 정보 밀도 높은 본문 선호. 위처럼 불릿 7-10개가 적당.
- URL은 launch blog post가 더 좋지만 처음 올릴 때는 레포로 가도 OK.
- 별다른 셀프 홍보 없이 사실만 적기. "혁신적인" / "최초의" 같은 수식어 자제.
- 댓글로 질문 오면 짧고 사실적으로 답변. 비교가 들어가면 "X가 더 나은 케이스도
  있다"고 솔직하게.
