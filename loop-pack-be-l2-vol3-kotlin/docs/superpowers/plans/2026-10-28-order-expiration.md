# 결제되지 않은 주문의 만료와 재고 · 쿠폰 복구 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 생성 후 30 분이 지나도록 `PAYMENT_PENDING` · `PAYMENT_FAILED` 인 주문을 `CANCELED` 로 바꾸고, 그 주문이 차감한 재고와 쓴 쿠폰을 되돌린다.

**아키텍처:** 주문 · 상품 · 쿠폰 각 도메인에 조건부 UPDATE 하나씩(`cancel`, `increaseStock`, `restore`)을 더하고,
`application/order/OrderExpirationFacade` 가 셋을 한 트랜잭션으로 잇는다. 판정은 `cancel` 의 영향 행 수 하나다 —
결제 시작과 같은 기대 상태 집합에서 출발하므로 둘 중 하나만 이기고, 이긴 트랜잭션에서만 복구가 돈다. 스케줄러가 60 s 마다 100 건씩 부른다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / JPA(JPQL 벌크 UPDATE) / Spring `@Scheduled` · `TransactionTemplate` / JUnit 5 · AssertJ · mockito-kotlin(`@MockitoBean`) / Testcontainers(MySQL)

**설계 문서:** `docs/superpowers/specs/2026-10-28-order-expiration-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **작업 브랜치는 `main` 에서 딴 `feature/order-expiration`** 이다. 설계 문서와 이 계획서는 Task 1 의 첫 커밋에 함께 넣는다.
- **응답 · 주석 · 커밋 메시지 · 문서는 한국어.** 변수명 · 함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백. 끝에 빈 줄 하나 뒤 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- **`modules/` · `supports/` 는 고치지 않는다.**
- **수치는 설계 3 장 그대로다.** 유효 시간 30 분(`created_at <= now − 30분` 이면 만료) · 주기 `fixedDelay = 60_000` · 한 번에 100 건 · `id` 오름차순.
- **대상 상태는 `PAYMENT_PENDING`, `PAYMENT_FAILED` 둘뿐이다.** `PAYING` · `PAID` · `CANCELED` 는 어떤 경우에도 건드리지 않는다. (설계 1 장 반목표)
- **상태 전이와 재고 · 쿠폰 복구는 한 트랜잭션이다.** 복구는 `cancel` 이 true 일 때만 실행한다. (설계 4.2 장)
- **재고 복구는 `productId` 오름차순이다.** 주문 생성의 차감 순서와 같아야 데드락이 없다.
- **분산 락을 두지 않는다.** (설계 4.2 장)
- **조건부 UPDATE 는 `@Modifying(clearAutomatically = true, flushAutomatically = true)` 다.** 호출 뒤 영속성 컨텍스트가 비워지므로
  같은 트랜잭션에서 그 전에 읽은 엔티티 값(주문 항목 · `usedCouponId`)은 호출 **전에** 꺼내 둔다.
- 재고 · 주문 상태 UPDATE 는 `updated_at` 을 건드리지 않는다. 쿠폰 UPDATE 는 `use` 와 같이 `updatedAt = :now` 를 SET 절에 직접 쓴다(벌크 연산은 `@PreUpdate` 를 타지 않는다).
- **`ktlintFormat` 을 실행하지 않는다.** 검증은 `ktlintCheck`. 최대 130 자(`*Test.kt` 예외), star import 금지, trailing comma 허용.
- **블록 주석 안에 `/**` 를 쓰지 않는다.**
- **주석은 "왜" 를 적는다.** 새로 쓰는 인용은 `(2026-10-28 주문 만료 설계 N 장)` 형식이다.
- 테스트: `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()`, 상품 조회(캐시)를 거치면 `redisCleanUp.truncateAll()` 도. 테스트에 `@Transactional` 을 붙이지 않는다.
  `@DisplayName` 한국어, `@Nested inner class`, `// arrange` · `// act` · `// assert`, 단언이 여럿이면 `assertAll`.
- 시간 경계는 `now` 를 인자로 넘겨 검증한다. 테스트가 30 분을 기다리지 않는다. 기준 시각은 저장 후 다시 읽은 `order.createdAt` 이다.
- 테스트 실행(저장소 루트 `loop-pack-be-l2-vol3-kotlin` 에서, Docker Desktop 필요):
  `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :apps:commerce-api:test --tests '<패턴>' --console=plain`

## 검토 초점

설계가 함의하지만 태스크의 기본 테스트로는 드러나지 않는, 사람이 겪을 가능성이 큰 입력과 실패 모드다. 각각의 테스트는 해당 태스크에 넣었다.

1. **정확히 30 분 경계** — `created_at == now − 30분` 인 주문은 만료된다(`<=`). 1 초 전은 그대로다. → Task 1 · Task 3
2. **소프트 삭제된 주문** — 조건을 만족해도 대상이 아니다(`deleted_at IS NULL`). → Task 1
3. **항목마다 수량이 다른 여러 상품 주문** — 각 상품이 자기 항목 수량만큼 정확히 돌아온다(합쳐지거나 한 번만 돌지 않는다). → Task 3
4. **쿠폰 복구가 다른 발급분을 건드리지 않는다** — 같은 회원의 다른 쿠폰 · 다른 회원의 같은 정책 쿠폰은 그대로다. → Task 2
5. **취소된 주문에 결제 요청** — 409 이고 결제 건이 만들어지지 않는다. → Task 3

---

### Task 1: 주문 — `CANCELED` 상태 · 만료 대상 조회 · 취소 전이

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderStatus.kt` — `CANCELED` 추가, KDoc 에 최종 상태임을 적는다
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderModel.kt` — `@Table.indexes` 에 `idx_orders_status_created_at (status, created_at)` 추가. `status` KDoc 의 운영 DDL 옆에 `CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderRepositoryImpl.kt`, `OrderJpaRepository.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderServiceIntegrationTest.kt` (기존 파일에 nested 추가)
- Add (커밋만): `docs/superpowers/specs/2026-10-28-order-expiration-design.md`, `docs/superpowers/plans/2026-10-28-order-expiration.md`

**Interfaces:**
- Produces:
  - `OrderStatus.CANCELED`
  - `OrderRepository.findExpirationTargets(statuses: Set<OrderStatus>, createdBefore: ZonedDateTime, limit: Int): List<Long>` — 주문 ID, `id` 오름차순. 저장소는 "만료" 를 모르고 상태 집합을 받는다
  - `OrderService.findExpirationTargets(createdBefore: ZonedDateTime, limit: Int): List<Long>` — `@Transactional(readOnly = true)`
  - `OrderService.cancel(orderId: Long): Boolean` — `changeStatus(orderId, setOf(PAYMENT_PENDING, PAYMENT_FAILED), CANCELED)`. false 면 결제가 시작됐거나 이미 취소됐다

- [ ] **Step 0: 브랜치**

`git switch main && git pull && git switch -c feature/order-expiration`. 설계 문서 · 계획서는 추적되지 않은 파일이라 그대로 따라온다.

- [ ] **Step 1: 실패하는 테스트 작성** — `@Nested inner class "주문을 만료 대상으로 고를 때, "` · `"주문을 취소할 때, "`

  - `returnsOnlyPendingAndFailed_createdAtOrBeforeCutoff`: PAYMENT_PENDING · PAYMENT_FAILED · PAYING · PAID · CANCELED 주문 하나씩.
    `findExpirationTargets(createdBefore = 마지막 주문의 createdAt, limit = 100)` → PENDING · FAILED 두 ID 만, `id` 오름차순.
  - `includesOrderCreatedExactlyAtCutoff` / `excludesOrderCreatedOneSecondAfterCutoff`: `createdBefore = order.createdAt` → 포함, `order.createdAt.minusSeconds(1)` → 비어 있음. (검토 초점 1)
  - `excludesSoftDeletedOrder`: 조건을 만족하는 주문을 `delete()` 후 저장 → 비어 있음. (검토 초점 2)
  - `respectsLimit`: 3 건, `limit = 2` → 앞의 2 건.
  - `cancel`: PAYMENT_PENDING → true · 상태 CANCELED. PAYMENT_FAILED → true. PAYING · PAID → false · 상태 그대로. 같은 주문 두 번째 `cancel` → false.
  - `startPayment` 가 CANCELED 주문에 false.

- [ ] **Step 2: 실패 확인** — `--tests '*OrderServiceIntegrationTest*'` → 컴파일 실패(`CANCELED`, `findExpirationTargets`, `cancel` 미해결)

- [ ] **Step 3: 구현**

  - `OrderJpaRepository` 에 JPQL `SELECT o.id FROM OrderModel o WHERE o.status IN :statuses AND o.createdAt <= :createdBefore AND o.deletedAt IS NULL ORDER BY o.id` + `Pageable`(limit 번역은 `RepositoryImpl` 이 한다 — 도메인 시그니처에 `Pageable` 금지).
  - 대상 상태 집합 `setOf(PAYMENT_PENDING, PAYMENT_FAILED)` 는 `OrderService` 가 넘긴다. `cancel` 의 기대 상태 집합과 같은 상수 하나를 쓴다 — 둘이 어긋나면 고르고도 취소하지 못하는 주문이 매 주기 다시 뽑힌다.

- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. `./gradlew :apps:commerce-api:ktlintCheck` → PASS

- [ ] **Step 5: 커밋** — `feat : 주문 취소 상태와 만료 대상 조회 · 취소 전이를 추가한다` (설계 문서 · 계획서 포함)

---

### Task 2: 재고 복구 · 쿠폰 복구

**Files:**
- Modify: `domain/product/ProductRepository.kt`, `domain/product/ProductService.kt`, `infrastructure/product/ProductRepositoryImpl.kt`, `infrastructure/product/ProductJpaRepository.kt`
- Modify: `domain/coupon/UserCouponRepository.kt`, `domain/coupon/CouponService.kt`, `domain/coupon/UserCouponModel.kt`(KDoc — "상태 변화는 usedAt 의 null → 시각 하나뿐" 을 복구 방향까지 고친다), `infrastructure/coupon/UserCouponRepositoryImpl.kt`, `infrastructure/coupon/UserCouponJpaRepository.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt`, `apps/commerce-api/src/test/kotlin/com/loopers/domain/coupon/CouponServiceIntegrationTest.kt` (있으면 nested 추가, 없으면 생성)

(경로는 `apps/commerce-api/src/main/kotlin/com/loopers/` 기준)

**Interfaces:**
- Produces:
  - `ProductRepository.increaseStock(productId: Long, quantity: Int): Int`
  - `ProductService.increaseStock(productId: Long, quantity: Int): Boolean` — `@Transactional`, 1 행이면 true
  - `UserCouponRepository.restore(userCouponId: Long, now: ZonedDateTime): Int`
  - `CouponService.restore(userCouponId: Long): Boolean` — `@Transactional`, `now = ZonedDateTime.now()` 를 안에서 만든다(`use` 와 같은 모양)

- [ ] **Step 1: 실패하는 테스트 작성**

  재고 — `"재고를 되돌릴 때, "`:
  - `increasesStockByQuantity`: 재고 10 → `increaseStock(id, 3)` true · 재고 13.
  - `increasesStockOfSoftDeletedProduct`: 삭제된 상품도 true · 재고 증가(조회는 `IncludingDeleted` 로). (설계 4.3 장)
  - `doesNotTouchUpdatedAt`: 호출 전후 `updatedAt` 같음.
  - `returnsFalse_whenProductDoesNotExist`: 없는 ID → false.

  쿠폰 — `"쿠폰을 되돌릴 때, "`:
  - `clearsUsedAt_whenUsed`: 발급 · 사용 → `restore(userCoupon.id)` true · `usedAt` null · `statusAt(now)` AVAILABLE.
  - `returnsFalse_whenNotUsed`: 쓰지 않은 쿠폰 → false.
  - `restoresRegardlessOfExpiry_butStaysExpired`: 만료 전 정책으로 발급 · 사용 → `restore` true · `usedAt` null · `statusAt(expiresAt.plusSeconds(1))` EXPIRED. (만료된 쿠폰은 `use` 가 막아 "만료 뒤 사용" 을 만들 수 없으므로 시각 인자로 확인한다)
  - `doesNotTouchOtherCoupons`: 같은 회원의 다른 쿠폰, 다른 회원의 같은 정책 쿠폰을 모두 사용 → 하나만 `restore` → 나머지 `usedAt` 그대로. (검토 초점 4)

- [ ] **Step 2: 실패 확인** — `--tests '*ProductServiceIntegrationTest*' --tests '*CouponServiceIntegrationTest*'` → 컴파일 실패

- [ ] **Step 3: 구현** — JPQL 은 설계 4.3 · 4.4 장 그대로다.
  - `increaseStock`: `WHERE p.id = :productId` 뿐. `deletedAt` 조건 없음.
  - `restore`: `SET c.usedAt = NULL, c.updatedAt = :now WHERE c.id = :userCouponId AND c.usedAt IS NOT NULL`. 만료 · 삭제 조건 없음.
  - 두 쿼리 KDoc 에 왜 그 조건이 없는지 적는다.

- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. `ktlintCheck` → PASS

- [ ] **Step 5: 커밋** — `feat : 주문 취소를 위한 재고 복구 · 쿠폰 복구 조건부 UPDATE 를 추가한다`

---

### Task 3: 만료 유스케이스 · 스케줄러 · 문서 반영

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderExpirationFacade.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/scheduler/OrderExpirationScheduler.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/config/SchedulingConfig.kt` — KDoc 에 이 스위치가 만료 스케줄러도 켠다는 것, 속성 이름을 바꾸지 않은 이유(설계 5 장)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt:124` — 409 메시지
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderService.kt:55` (KDoc), `interfaces/api/payment/PaymentV1ApiSpec.kt:14` (설명 문구)
- Modify: `docs/superpowers/specs/2026-10-13-payment-design.md` — 3.2 장 상태 그림 · 199 행 표의 409 설명, 9.1 장, 9.2 장 (설계 8 장)
- Test: Create `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderExpirationFacadeIntegrationTest.kt`
- Test: Modify `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentFacadeIntegrationTest.kt` (취소된 주문 결제)

**Interfaces:**
- Consumes: Task 1 의 `OrderService.findExpirationTargets` · `cancel` · `OrderStatus.CANCELED`, Task 2 의 `ProductService.increaseStock` · `CouponService.restore`, 기존 `OrderService.getOrder(id): OrderModel?`
- Produces:
  - `OrderExpirationFacade(orderService, productService, couponService, transactionTemplate: TransactionTemplate)`
  - `fun expireOverdue(now: ZonedDateTime = ZonedDateTime.now()): Int` — `findExpirationTargets(now.minus(EXPIRE_AFTER), BATCH_SIZE)` 의 각 ID 에 `expire` 를 `runCatching` 으로 부르고 실패는 ERROR 로그. 뽑은 건수를 돌려준다(결제 `syncPending` 과 같은 모양)
  - `fun expire(orderId: Long): Boolean` — `transactionTemplate.execute` 한 번. 취소했으면 true
  - `private val EXPIRE_AFTER: Duration = Duration.ofMinutes(30)`, `private const val BATCH_SIZE = 100`
  - `OrderExpirationScheduler(orderExpirationFacade)` — `@Scheduled(fixedDelay = 60_000) fun expireOverdue()`

- [ ] **Step 1: 실패하는 테스트 작성** — `OrderExpirationFacadeIntegrationTest`

  준비: 회원 · 상품 둘(재고 10 · 10) · 쿠폰 정책 · 발급. 주문은 `OrderFacade.place` 로 만든다(실제 차감 · 쿠폰 사용을 거치게). `@MockitoBean PaymentGateway` (경합 테스트용).

  `"만료 시각이 지난 주문을 처리할 때, "`:
  - `cancelsPendingOrder_andRestoresStockAndCoupon`: 상품 A 2 개 · B 3 개 + 쿠폰 주문(재고 8 · 7) → `expireOverdue(createdAt + 30분)` 1 → 주문 CANCELED · 재고 10 · 10 · 쿠폰 `usedAt` null. (검토 초점 3)
  - `cancelsPaymentFailedOrder`: 결제를 FAILED 로 만든 주문(`PaymentGateway.request` → `NotSent`) → CANCELED · 재고 원복.
  - `cancelsOrderWithoutCoupon`: 쿠폰 없는 주문 → CANCELED · 재고 원복, 예외 없음.
  - `expiresAtExactlyThirtyMinutes` / `keepsOrderOneSecondBeforeThirtyMinutes`: `now = createdAt + 30분` → CANCELED, `createdAt + 30분 − 1초` → PAYMENT_PENDING · 재고 그대로. (검토 초점 1)
  - `keepsPayingAndPaidOrders`: PAYING(`request` → `Unknown`) · PAID(0 원 주문 또는 동기화로 확정) 주문, `now = createdAt + 1시간` → 상태 · 재고 그대로.
  - `restoresOnlyOnce_whenRunTwice`: `expireOverdue` 두 번 → 재고 10 (13 아님), 두 번째 반환 0.

  `"결제 시작과 만료가 겹칠 때, "` (`CountDownLatch` + `Executors`, 결제 동기화 테스트의 동시성 테스트와 같은 방식):
  - `onlyOneOfPaymentAndExpirationWins`: `PaymentGateway.request` → `Accepted(KEY)`. `PaymentFacade.request` 와 `expire(orderId)` 를 동시에 →
    `(주문 PAYING · 재고 그대로 · expire false)` 또는 `(주문 CANCELED · 재고 원복 · request 가 CONFLICT)` 중 정확히 하나. 50 회 반복은 하지 않는다 — 한 번 실행으로 어느 쪽이든 불변식만 단언한다.
  - `restoresOnlyOnce_whenTwoExpirationsRace`: 같은 주문에 `expire` 두 개 동시 → true 는 하나, 재고 원복 1 회.

  `PaymentFacadeIntegrationTest` — `"결제를 요청할 때, "` 에 추가:
  - `throwsConflict_whenOrderIsCanceled`: 만료된 주문에 `request` → `CoreException` CONFLICT, 메시지에 `결제할 수 없는 상태의 주문입니다` 포함, `paymentService.findAllByOrderId` 비어 있음, `PaymentGateway.request` 미호출. (검토 초점 5)

- [ ] **Step 2: 실패 확인** — `--tests '*OrderExpirationFacadeIntegrationTest*' --tests '*PaymentFacadeIntegrationTest*'` → 컴파일 실패

- [ ] **Step 3: 구현**
  - `expire`: 트랜잭션 안에서 `getOrder` → 없으면 false. 항목을 `productId` 오름차순 `(productId, quantity)` 목록과 `usedCouponId` 로 **먼저 꺼낸 뒤** `cancel`. false 면 false. 항목마다 `increaseStock` — false 면 WARN 로그만 남기고 계속(설계 4.3 장). `usedCouponId` 가 있으면 `restore` — false 도 WARN 로그만. 취소 시 INFO 로그 `[orderId = {}] 결제 기한이 지나 주문을 취소하고 재고 · 쿠폰을 되돌렸습니다.`
  - 409 메시지: `"[orderId = ${order.id}] 결제할 수 없는 상태의 주문입니다."` — 결제 중 · 완료 · 취소를 가리지 않는다.
  - 스케줄러 KDoc: `interfaces` 에 두는 이유 · 분산 락이 없는 이유는 `PaymentSyncScheduler` 와 같다고 짧게 참조.
  - 문서: 설계 8 장의 세 곳. 9.2 장은 "해소됨 — 2026-10-28 주문 만료 설계" 로 링크하고 PAYING 주문은 남는 한계로 둔다.

- [ ] **Step 4: 통과 확인** — `--tests '*OrderExpirationFacadeIntegrationTest*' --tests '*PaymentFacadeIntegrationTest*'` → PASS.
  이어서 회귀: `--tests '*Order*' --tests '*Payment*' --tests '*Coupon*' --tests '*Product*'` → 실패 0. `ktlintCheck` → PASS

- [ ] **Step 5: 커밋** — `feat : 결제 기한이 지난 주문을 취소하고 재고 · 쿠폰을 되돌리는 만료 스케줄러를 추가한다`
