# 사람이 확인해야 하는 결제의 경보 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 사람이 PG 를 조회해야 하는 결제 사건(결제 설계 9.1 · 9.5 장)이 생길 때마다 `payment_manual_review_total{reason}` 을 정확히 한 번 올리고, Prometheus 경보 규칙으로 드러낸다.

**아키텍처:** application 에 `ManualReviewRecorder` 인터페이스와 `ManualReviewReason` enum 을 두고, infrastructure 의 Micrometer 구현이 트랜잭션이 있으면 커밋 뒤에, 없으면 즉시 카운터를 올린다.
`PaymentFacade` 는 결제를 FAILED 로 닫는 다섯 자리(콜백은 ERROR 로그 자리)에서 `record` 를 부른다. 경보 규칙은 `docker/grafana` 의 Prometheus 에 둔다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Micrometer / Spring `TransactionSynchronization` / JUnit 5 · AssertJ · mockito-kotlin(`@MockitoBean`) / Testcontainers(MySQL) / Prometheus(`promtool`)

**설계 문서:** `docs/superpowers/specs/2026-10-30-payment-manual-review-alert-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

- **작업 브랜치는 `main`(4daf82f6) 에서 딴 `feature/payment-manual-review-alert`** 다. 설계 문서와 이 계획서는 Task 1 커밋에 함께 넣는다.
- **응답 · 주석 · 커밋 메시지 · 문서는 한국어.** 식별자는 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞 공백. 끝에 빈 줄 하나 뒤 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. amend 금지.
- **`modules/` · `supports/` 는 고치지 않는다.**
- **지표:** Micrometer 이름 `payment.manual.review`, 태그 `reason` — 값은 `unlinked_callback` · `ambiguous_transaction` · `amount_mismatch` · `order_mismatch` · `key_owner_conflict` 다섯뿐. 기동 시 다섯 모두 미리 등록. (설계 1 · 2 장)
- **커밋 뒤에만 센다:** `TransactionSynchronizationManager.isSynchronizationActive()` 면 `afterCommit` 에 등록, 아니면 즉시. (설계 3 장)
- **이유는 호출 자리로 정한다, 실패 사유 문자열로 정하지 않는다.** **상태가 실제로 바뀐 경우에만 기록한다.** (설계 2 장)
- **결제 · 주문 상태 전이와 그 순서, 기존 ERROR 로그는 바꾸지 않는다.** (설계 1 장 반목표 3)
- **`ktlintFormat` 을 실행하지 않는다.** 검증은 `ktlintCheck`. 130 자(`*Test.kt` 예외), star import 금지. 블록 주석 안 `/**` 금지.
- **주석은 "왜" 를 적는다.** 새 인용은 `(2026-10-30 결제 경보 설계 N 장)` 형식.
- 테스트: 한국어 `@DisplayName`, `@Nested inner class`, arrange/act/assert, 여러 단언 `assertAll`, `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()`. 카운터는 **증가분**으로 단언(공유 컨텍스트). 존재 확인은 `meterRegistry.find(...)` — `counter()` 는 없으면 만들어 단언을 가린다.
- 테스트 실행(저장소 루트 `loop-pack-be-l2-vol3-kotlin`, Docker Desktop 필요):
  `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :apps:commerce-api:test --tests '<패턴>' --console=plain`

## 검토 초점

1. **같은 결제가 동기화 주기마다 다시 세짐** — 모르는 키가 둘 이상인 결제는 1 분 전까지 매 주기 다시 발견된다. → Task 2 `countsAmbiguousOnlyWhenClosed`
2. **롤백된 시도가 세짐** — `@Version` 충돌로 롤백된 확정이 경보를 울린다. → Task 1 `doesNotCount_whenRolledBack` · `countsOnlyAfterCommit`
3. **같은 사유 문자열의 두 이유가 섞임** — `ambiguous_transaction` 과 `key_owner_conflict` 는 같은 `REASON_AMBIGUOUS` 로 닫는다. → Task 2 `countsAmbiguousOnlyWhenClosed` 의 `key_owner_conflict` 불변 단언
4. **정상 흐름이 세짐** — SUCCESS · PG FAILED 확정, `REASON_NOT_REACHED` 포기, 정상 콜백에서 다섯 카운터가 오르면 경보가 상시 울린다. → Task 2 `doesNotCount_forNormalOutcomes`
5. **사건 전에 지표가 없음** — 경보식이 "없음" 과 "0" 을 구분해야 한다. → Task 1 `registersAllReasonsOnStartup`

---

### Task 1: 기록기 — 이유 · 인터페이스 · 커밋 후 증가

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/ManualReviewReason.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/ManualReviewRecorder.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/MicrometerManualReviewRecorder.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/MicrometerManualReviewRecorderTest.kt`
- Add (커밋만): 설계 문서 · 이 계획서

**Interfaces:**
- Produces:
  - `enum class ManualReviewReason(val tag: String)` — `UNLINKED_CALLBACK("unlinked_callback")`, `AMBIGUOUS_TRANSACTION("ambiguous_transaction")`, `AMOUNT_MISMATCH("amount_mismatch")`, `ORDER_MISMATCH("order_mismatch")`, `KEY_OWNER_CONFLICT("key_owner_conflict")`
  - `interface ManualReviewRecorder { fun record(reason: ManualReviewReason) }`
  - `@Component class MicrometerManualReviewRecorder(meterRegistry: MeterRegistry) : ManualReviewRecorder` — 카운터 이름 `payment.manual.review`, 태그 `reason`

- [ ] **Step 0: 브랜치** — `git switch main && git pull --ff-only && git switch -c feature/payment-manual-review-alert`

- [ ] **Step 1: 실패하는 테스트 작성** — `@SpringBootTest`(덮어쓰기 없음), `MeterRegistry` · `TransactionTemplate` · `ManualReviewRecorder` 주입:
  - `registersAllReasonsOnStartup` (검토 초점 5): 다섯 `reason` 모두 `meterRegistry.find("payment.manual.review").tag("reason", r.tag).counter()` 가 null 아님.
  - `countsImmediately_outsideTransaction`: 트랜잭션 밖 `record(AMOUNT_MISMATCH)` → `amount_mismatch` +1, 나머지 그대로.
  - `countsOnlyAfterCommit` (검토 초점 2): `transactionTemplate.executeWithoutResult { record(ORDER_MISMATCH); 블록 안에서 증가분 0 을 확인 }` → 블록이 끝난 뒤 +1.
  - `doesNotCount_whenRolledBack` (검토 초점 2): `transactionTemplate.executeWithoutResult { record(KEY_OWNER_CONFLICT); throw IllegalStateException() }` 를 `assertThrows` 로 감싼 뒤 → 증가분 0.

- [ ] **Step 2: 실패 확인** — `--tests '*MicrometerManualReviewRecorderTest*'` → 컴파일 실패

- [ ] **Step 3: 구현** — 위 Interfaces. KDoc: 왜 커밋 뒤인지(설계 3 장), 왜 인터페이스를 application 에 두는지(캐시와 같은 배치, 설계 4 장), 왜 미리 등록하는지.

- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. `ktlintCheck` → PASS

- [ ] **Step 5: 커밋** — `feat : 수동 확인 결제를 커밋 뒤에만 세는 기록기를 추가한다` (설계 문서 · 계획서 포함)

---

### Task 2: 결제 흐름 연결 · 경보 규칙 · 문서

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` — 생성자에 `ManualReviewRecorder`, 다섯 자리에서 `record`
- Modify: 결제 테스트 — `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentSyncIntegrationTest.kt`, `PaymentFacadeIntegrationTest.kt`
- Create: `docker/grafana/alert-rules.yml`
- Modify: `docker/grafana/prometheus.yml` (`rule_files`), `docker/monitoring-compose.yml` (규칙 파일 마운트)
- Modify: `docs/superpowers/specs/2026-10-13-payment-design.md`(9.1 · 9.5 장 끝 한 줄씩), `docs/superpowers/specs/2026-10-28-order-expiration-design.md`(6 장 고아 거래 항목 한 줄)

**Interfaces:**
- Consumes: Task 1 의 `ManualReviewRecorder.record(reason: ManualReviewReason)` 와 enum 값
- `PaymentFacade` 내부 변경:
  - `handleCallback` — 이어지는 결제가 없는 분기(ERROR 로그 자리)에서 `UNLINKED_CALLBACK`
  - `giveUpIfExpired(..., reason: String, reviewReason: ManualReviewReason?)` — 실제로 `fail` 했을 때만 `reviewReason` 을 기록. `Unlinked.Ambiguous` 분기만 `AMBIGUOUS_TRANSACTION`, `Unlinked.None` 은 `null`
  - `confirm` — 주문 불일치 `ORDER_MISMATCH`, 금액 불일치 `AMOUNT_MISMATCH` (각 `fail` 바로 뒤)
  - `closeIfKeyOwnedByOther` — `fail` 직전 상태가 최종이 아니었을 때만 `KEY_OWNER_CONFLICT`

- [ ] **Step 1: 실패하는 테스트 작성** — 두 테스트 클래스에 `MeterRegistry` 를 주입하고 증가분 헬퍼를 둔다. 각 사건을 만드는 기존 테스트가 있으면 거기에 증가분 단언을 더하고, 없으면 새로 만든다:
  - `PaymentSyncIntegrationTest`:
    - `unlinked_callback` +1 — 이어지는 결제가 없는 콜백.
    - `countsAmbiguousOnlyWhenClosed` (검토 초점 1 · 3): PG 목록에 모르는 키 둘. `createdAt + 1 분` 전 시각으로 `sync` 를 세 번 → 증가분 0. 1 분 뒤 `sync` → `ambiguous_transaction` +1 · `key_owner_conflict` 0. 그 뒤 `sync` 두 번 더 → 그대로.
    - `amount_mismatch` +1 · `order_mismatch` +1 — 각 불일치 확정. 같은 결제를 다시 `sync` 해도 그대로.
    - `key_owner_conflict` +1 — 동기화 경로(요청됨 결제가 PG 목록의 키를 연결하려는데 그 키가 다른 결제 소유).
    - `doesNotCount_forNormalOutcomes` (검토 초점 4): SUCCESS 확정 · PG FAILED 확정 · `REASON_NOT_REACHED` 포기(1 분 뒤 PG 목록 비어 있음) · 키로 찾은 정상 콜백 → 다섯 카운터 모두 증가분 0.
  - `PaymentFacadeIntegrationTest`:
    - `key_owner_conflict` +1 — 결제 요청 tx2 경로(`paymentGateway.request` 가 다른 결제가 가진 키로 `Accepted`).

- [ ] **Step 2: 실패 확인** — `--tests '*PaymentSyncIntegrationTest*' --tests '*PaymentFacadeIntegrationTest*'` → 새 단언 실패

- [ ] **Step 3: 구현**
  - `PaymentFacade` — 위 Interfaces. 상태 전이 · ERROR 로그는 그대로 두고 `record` 한 줄씩. KDoc 에 "닫을 때 센다(발견할 때가 아니라)" 와 이유를 호출 자리로 정하는 이유(설계 2 장).
  - `docker/grafana/alert-rules.yml` — 설계 4 장의 규칙 그대로(`PaymentManualReviewNeeded`, `increase(payment_manual_review_total[5m]) > 0`, `severity: page`, summary · description 문구 그대로).
  - `prometheus.yml` 에 `rule_files: ['/etc/prometheus/alert-rules.yml']`, compose 에 `./grafana/alert-rules.yml:/etc/prometheus/alert-rules.yml` 마운트.
  - 문서 세 곳 한 줄씩(설계 6 장).

- [ ] **Step 4: 통과 확인**
  - 같은 테스트 명령 → PASS. 회귀: `--tests '*Payment*' --tests '*Order*'` → 실패 0. `ktlintCheck` → PASS
  - 규칙 문법: `docker run --rm -v "$PWD/docker/grafana:/cfg" --entrypoint promtool prom/prometheus check rules /cfg/alert-rules.yml` → `SUCCESS: 1 rules found`
  - 설정 문법: `docker run --rm -v "$PWD/docker/grafana:/etc/prometheus" --entrypoint promtool prom/prometheus check config /etc/prometheus/prometheus.yml` → `SUCCESS`

- [ ] **Step 5: 커밋** — `feat : 수동 확인이 필요한 결제를 payment_manual_review_total 로 세고 Prometheus 경보 규칙을 둔다`
