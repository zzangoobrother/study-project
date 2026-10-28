# PG 호출 격리 — Bulkhead 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** PG 를 부르는 동시 호출 수에 상한(결제 요청 50 · 조회 20, 대기 0)을 두어, PG 가 느려져도 Tomcat 스레드가 PG 일로 다 묶이지 않게 한다.

**아키텍처:** `PgResilientClient` 의 세 메서드에 Resilience4j `@Bulkhead`(Semaphore)를 붙이고 aspect-order 로 가장 바깥에 둔다 — `Bulkhead( CircuitBreaker( Retry( 호출 ) ) )`.
거절(`BulkheadFullException`)은 `PgPaymentGateway` 가 결제 요청이면 `NotSent`, 조회면 `Unavailable` 로 번역한다. `PaymentFacade` 는 바뀌지 않는다.
거절 수는 기본 지표가 없어 이벤트에 Micrometer 카운터를 붙인다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Resilience4j 2.2(`resilience4j-spring-boot3`, bulkhead 모듈 포함) / Micrometer / JUnit 5 · AssertJ / WireMock(`spring-cloud-contract-wiremock`)

**설계 문서:** `docs/superpowers/specs/2026-10-29-pg-bulkhead-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

- **작업 브랜치는 `main` 에서 딴 `feature/pg-bulkhead`** 다. 설계 문서와 이 계획서는 Task 1 커밋에 함께 넣는다.
- **응답 · 주석 · 커밋 메시지 · 문서는 한국어.** 식별자는 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞 공백. 끝에 빈 줄 하나 뒤 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. amend 금지.
- **`modules/` · `supports/` 는 고치지 않는다.**
- **수치는 설계 3 장 그대로다.** `pgRequest` `max-concurrent-calls: 50` · `pgQuery` `max-concurrent-calls: 20` · 둘 다 `max-wait-duration: 0`, Semaphore 방식.
- **감싸는 순서는 `Bulkhead( CircuitBreaker( Retry( 호출 ) ) )` — Bulkhead 가 가장 바깥이다.** `resilience4j.bulkhead.bulkhead-aspect-order: 0` (구현 중 판정으로 `PgBulkheadOrderConfig` 로 대체 — resilience4j 2.2.0 에 이 키가 없다, 설계 2 장). 기존 circuit 1 · retry 2, 값이 작을수록 바깥. (설계 2 장)
- **거절 번역:** 결제 요청 → `PgRequestResult.NotSent` + WARN `[orderId = {}] PG 동시 호출이 상한에 걸려 결제 요청을 보내지 않았습니다.` / 조회 → `PgQueryResult.Unavailable`. 번역은 `PgPaymentGateway` 에서만 한다. (설계 4 장)
- **`PaymentFacade` · 컨트롤러는 고치지 않는다.**
- **거절 카운터:** Micrometer 이름 `pg.bulkhead.rejected`, 태그 `name` = `pgRequest` | `pgQuery` (Prometheus `pg_bulkhead_rejected_total`). (설계 5 장)
- **`ktlintFormat` 을 실행하지 않는다.** 검증은 `ktlintCheck`. 130 자(`*Test.kt` 예외), star import 금지. 블록 주석 안 `/**` 금지.
- **주석은 "왜" 를 적는다.** 새 인용은 `(2026-10-29 PG 격리 설계 N 장)` 형식.
- 테스트: 한국어 `@DisplayName`, `@Nested inner class`, arrange/act/assert, 여러 단언은 `assertAll`. 서킷 레지스트리는 `@BeforeEach` 에서 reset(기존 `PgPaymentGatewayTest` 와 같다).
- 테스트 실행(저장소 루트 `loop-pack-be-l2-vol3-kotlin`, Docker Desktop 필요):
  `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :apps:commerce-api:test --tests '<패턴>' --console=plain`

## 검토 초점

1. **재시도 도중의 거절** — 앞선 시도가 PG 에 닿은 뒤 거절되면 `NotSent` 오판 · 이중 결제다. 순서가 틀리면 에러 없이 생긴다. → Task 1 `PgBulkheadConfigTest.wrapsBulkheadOutermost`(aspect 빈 순서 단언) + `rejectionIsNotRecordedByCircuit`(서킷 버퍼 2). (정정: 처음에는 `rejectsOnlyBeforeFirstAttempt` 를 지목했으나 그 시나리오는 순서가 뒤집혀도 같은 값이 나와 순서를 증명하지 못한다)
2. **허가 누수** — 읽기 타임아웃 · 4xx · 5xx 소진 등 예외로 끝난 호출이 허가를 돌려주지 않으면 상한이 점점 줄어 결국 모든 결제가 거절된다. → Task 1 `releasesPermit_afterFailures`
3. **거절이 서킷 실패로 세짐** — PG 는 정상인데 서킷이 열린다. → Task 1 `rejectionIsNotRecordedByCircuit`
4. **운영 값이 테스트용 덮어쓰기에 가려짐** — 상한 2 로 검증하는 동안 yml 의 50 · 20 · 0 이 틀려도 모른다. → Task 1 `PgBulkheadConfigTest`
5. **조회 거절이 상태를 바꿈** — `Unavailable` 이 아닌 다른 결과로 번역되면 동기화가 실패 확정할 수 있다. → Task 1 `returnsUnavailable_whenQueryBulkheadIsFull`

---

### Task 1: Bulkhead 적용 — 순서 · 번역 · 운영 값

**Files:**
- Modify: `apps/commerce-api/src/main/resources/application.yml` — `resilience4j.bulkhead` 블록(aspect-order 0 (구현 중 판정으로 `PgBulkheadOrderConfig` 로 대체 — resilience4j 2.2.0 에 이 키가 없다, 설계 2 장), 두 인스턴스). 기존 circuit/retry 위 주석의 "감싸는 순서" 설명을 Bulkhead 포함으로 고친다
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PgResilientClient.kt` — `requestPayment` 에 `@Bulkhead(name = "pgRequest")`, 조회 두 메서드에 `@Bulkhead(name = "pgQuery")`. KDoc 의 순서 설명 갱신
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PgPaymentGateway.kt` — `request` 에 `BulkheadFullException` catch(→ `NotSent`, `CallNotPermittedException` catch 옆). 조회는 기존 catch 가 받는다 — 구현자 판단으로 명시 catch 를 더해도 된다
- Modify: `docs/superpowers/specs/2026-10-13-payment-design.md` — 4.2 장 `NotSent` 원인에 Bulkhead 상한, 6.4 장 순서 그림, 6.5 장 신설(이 설계 링크) (설계 7 장)
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PgPaymentGatewayBulkheadTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PgBulkheadConfigTest.kt`
- Add (커밋만): 설계 문서 · 이 계획서

**Interfaces:**
- Produces: Bulkhead 인스턴스 이름 `pgRequest` · `pgQuery`(Task 2 가 `BulkheadRegistry` 에서 이 이름으로 꺼낸다)

- [ ] **Step 0: 브랜치** — `git switch main && git pull --ff-only && git switch -c feature/pg-bulkhead`

- [ ] **Step 1: 실패하는 테스트 작성**

  `PgBulkheadConfigTest` — 덮어쓰기 없는 `@SpringBootTest`(다른 통합 테스트와 컨텍스트를 공유한다):
  - `usesSpecifiedLimits`: `bulkheadRegistry.bulkhead("pgRequest").bulkheadConfig` 의 `maxConcurrentCalls` 50 · `maxWaitDuration` 0, `pgQuery` 20 · 0.

  `PgPaymentGatewayBulkheadTest` — `@AutoConfigureWireMock(port = 0)`, properties:
  `pg.base-url=http://localhost:${wiremock.server.port}`, `resilience4j.retry.instances.pgRequest.wait-duration=10ms`,
  `resilience4j.bulkhead.instances.pgRequest.max-concurrent-calls=2`, `resilience4j.bulkhead.instances.pgQuery.max-concurrent-calls=2`.
  KDoc 에 상한을 2 로 덮어쓰는 이유(50 을 채우는 테스트는 무겁다)와 운영 값은 `PgBulkheadConfigTest` 가 지킨다는 것을 적는다.
  동시 요청은 `CountDownLatch` 로 동시에 출발시키고, 스텁에 고정 지연(예: 500 ms, 읽기 타임아웃 1 s 미만)을 둬 실제로 겹치게 한다.

  `"결제 요청이 상한을 넘으면, "`:
  - `returnsNotSent_forOverflow`: 접수 스텁 + 지연, 요청 3 개 동시 → 결과 중 `NotSent` 정확히 1 · `Accepted` 2, WireMock 이 받은 결제 요청 2.
  - `rejectsOnlyBeforeFirstAttempt` (검토 초점 1): 5xx 스텁 + 지연, 요청 3 개 동시 → `NotSent` 1 · `Unknown` 2, WireMock 결제 요청 정확히 6(통과한 2 개 × 3 회).
  - `rejectionIsNotRecordedByCircuit` (검토 초점 3): 위와 같은 상황 뒤 `circuit("pgRequest").metrics` 의 `numberOfFailedCalls` 2 · `numberOfBufferedCalls` 2 · `numberOfNotPermittedCalls` 0.
  - `releasesPermit_afterFailures` (검토 초점 2): 읽기 타임아웃(지연 1.5 s) 요청 2 개, 4xx 요청 2 개, 5xx 소진 요청 2 개를 각각 순서대로 끝낸 뒤, 접수 스텁 + 지연으로 요청 2 개 동시 → 둘 다 `Accepted`(거절 0). 이어서 `bulkhead("pgRequest").metrics.availableConcurrentCalls` 2.

  `"조회가 상한을 넘으면, "`:
  - `returnsUnavailable_whenQueryBulkheadIsFull` (검토 초점 5): 상세 조회 스텁 + 지연, `findTransaction` 3 개 동시 → `Unavailable` 1 · `Found` 2.

- [ ] **Step 2: 실패 확인** — `--tests '*PgPaymentGatewayBulkheadTest*' --tests '*PgBulkheadConfigTest*'` → 설정 테스트는 기본 상한(25)이라 실패, 오버플로 테스트는 거절이 없어 실패(또는 `BulkheadFullException` 미번역)

- [ ] **Step 3: 구현** — 위 Files 대로. yml 주석에 설계 3 장의 근거 한 줄씩(50 = 평소 약 32 의 1.5 배, Tomcat 150 남김 / 20 = 조회 여유 / 대기 0 = 기다리면 스레드를 쥔다). 문서 3 곳 반영.

- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. 회귀: `--tests '*PgPaymentGateway*' --tests '*Payment*'` → 실패 0. `ktlintCheck` → PASS

- [ ] **Step 5: 커밋** — `feat : PG 동시 호출에 Bulkhead 상한을 두고 거절을 NotSent · Unavailable 로 번역한다` (설계 문서 · 계획서 포함)

---

### Task 2: 거절 수 지표

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PgBulkheadMetrics.kt`
- Test: Modify `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PgPaymentGatewayBulkheadTest.kt`

**Interfaces:**
- Consumes: Task 1 의 Bulkhead 인스턴스 `pgRequest` · `pgQuery`
- Produces: `@Component class PgBulkheadMetrics(bulkheadRegistry: BulkheadRegistry, meterRegistry: MeterRegistry)` — 생성 시 두 인스턴스의 `eventPublisher.onCallRejected` 에 `pg.bulkhead.rejected{name}` 카운터 증가를 구독. 카운터는 미리 등록해 0 부터 보이게 한다

- [ ] **Step 1: 실패하는 테스트 작성** — `PgPaymentGatewayBulkheadTest` 에 `MeterRegistry` 주입:
  - `countsRejection`: 호출 전 `meterRegistry.counter("pg.bulkhead.rejected", "name", "pgRequest").count()` 를 읽고, `returnsNotSent_forOverflow` 와 같은 상황 뒤 정확히 1 증가. `pgQuery` 카운터는 그대로.
  - `exposesCounterBeforeAnyRejection`: `meterRegistry.find("pg.bulkhead.rejected").tag("name", "pgQuery").counter()` 가 null 이 아니다.

- [ ] **Step 2: 실패 확인** — `--tests '*PgPaymentGatewayBulkheadTest*'` → 새 테스트 실패(카운터 없음)

- [ ] **Step 3: 구현** — KDoc 에 왜 직접 붙이는지(resilience4j-micrometer 2.2 의 Bulkhead 지표는 게이지 둘뿐 — 설계 5 장)와 측정(설계 8 장)에서 쓴다는 것.

- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. `ktlintCheck` → PASS

- [ ] **Step 5: 커밋** — `feat : PG Bulkhead 거절 수를 pg_bulkhead_rejected_total 지표로 남긴다`

---

### Task 3 (선택): 측정 — 가설 4

**실행 여부는 Task 2 뒤 사용자가 정한다.** 실행하지 않으면 계획은 Task 2 에서 끝난다.

**Files:**
- Modify: `loadtest/payment.js` · `loadtest/README.md` · `docker/loadtest-payment.override.yml` — 가설 3 의 `mixed` 모드에 결제 rps · 주문 상세 rps 를 환경 변수로 받게 하고(이미 있으면 재사용), commerce-api 에 Bulkhead 상한을 환경 변수(`RESILIENCE4J_BULKHEAD_INSTANCES_PGREQUEST_MAX_CONCURRENT_CALLS`)로 덮어쓸 수 있게 한다
- Modify: `loadtest/payment-metrics.sh` — `pg_bulkhead_rejected_total` · `resilience4j_bulkhead_available_concurrent_calls` 수집 추가
- Modify: `docs/superpowers/specs/2026-10-29-pg-bulkhead-design.md` — 8 장 아래 "결과" 절

**절차** — `docs/superpowers/plans/2026-10-17-payment-resilience-measurement.md` 의 공통 순서(환경 재기동 · PG 데우기 · TRUNCATE · Toxiproxy 상태 초기화)를 그대로 따른다.

- [ ] **Step 1: 측정 도구 변경 · 스모크** — 위 Files. 10 s 스모크로 새 지표가 CSV 에 찍히는지 확인
- [ ] **Step 2: 정상 부하 회귀** — 가설 3(B) 조건(결제 70 rps · 주문 상세 100 rps · 지연 없음, 120 s) → `pg_bulkhead_rejected_total` 0 (반목표 1)
- [ ] **Step 3: D(Bulkhead 끔, 상한 1000)** — 결제 150 rps · 주문 상세 100 rps · 지연 900 ms, 워밍업 10 s + 120 s. Tomcat 바쁜 스레드 최대가 200 이 아니면 측정 불성립 — 결제 rps 를 올려(Tomcat 200 포화까지) 다시 잰다
- [ ] **Step 4: E(Bulkhead 50)** — Step 3 와 같은 조건
- [ ] **Step 5: 판정 · 기록** — E 주문 상세 p99 ≤ D × 0.5, E 바쁜 스레드 최대 < 200. 부가: E 거절 수 · 결제 FAILED 비율 · 서킷 상태. 서킷이 열렸으면 원인과 함께 적는다. 설계 8 장 "결과" 절과 결제 설계 11.4 장 표에 한 행
- [ ] **Step 6: 커밋** — `docs : PG Bulkhead 측정(가설 4) 결과를 남긴다` (결과 원본은 `loadtest/results/` 에 기존 이름 규칙으로)
