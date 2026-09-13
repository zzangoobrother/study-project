# 측정 전용 락 전략 구현 정리 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 락 전략 비교를 위해 만든 **측정 전용 코드를 전부 걷어내고**, 재고 차감을 비교 이전의
단일 경로(조건부 `UPDATE`)로 되돌린다.

**아키텍처:** 되돌리기다. 새 동작을 만들지 않는다. 낙관적 락·비관적 락 구현, 전략 인터페이스와
`@ConditionalOnProperty` 스위치, `ProductModel.version` 컬럼, `OrderFacade` 의 재시도 래퍼를
차례로 지운다. 끝나면 `ProductRepositoryImpl.decreaseStock` 이 `ProductJpaRepository.decreaseStock`
을 직접 부르던 원래 모양으로 돌아간다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Spring Data JPA / MySQL 8.0 / JUnit 5 · AssertJ ·
Mockito / Testcontainers

**설계 문서:** `docs/superpowers/specs/2026-09-09-lock-strategy-throughput-design.md` 6.5 장
(이 계획은 그 장의 실행이다. 실행자는 그 문서의 3.7 · 3.8 장도 읽는다 — 무엇을 재서 무엇이
남았는지가 거기 있다.)

> ## 왜 지금 지우는가
>
> 6.5 장이 정한 순서가 **문서 먼저, 코드 나중**이다. 측정 결과는 이미 설계 문서 3.7 · 3.8 장에
> 반영됐다(커밋 `da54f7c`). **코드가 사라져도 재 봤다는 사실과 그 숫자는 남는다.**
>
> 그리고 이 정리가 [2026-09-11 단일 문장화 계획](2026-09-11-batch-stock-decrease.md)의 선행
> 작업이다. 그 계획의 Task 4 는 `decreaseStock` 을 지우는데, 지금은 그 위에 전략 계층이 얹혀
> 있어 그대로 실행하면 구현 셋이 고아가 된다.

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **응답·주석·커밋 메시지·문서는 한국어.** 변수명·함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백이 있다.
- **`modules/` 와 저장소 루트의 `supports/` 를 수정하지 않는다.**
- **`ktlintFormat` 을 실행하지 않는다.** 검증은 `ktlintCheck` 로 한다. 미사용 임포트를 잡으면 지운다.
- **ktlint 최대 줄 길이 130 자.** `*Test.kt` 는 예외다.
- **모든 Gradle 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** Git 루트는 상위 `study-project/` 다.
  - 전체: `./gradlew :apps:commerce-api:test`
  - 단일 클래스: `./gradlew :apps:commerce-api:test --tests 'com.loopers.<FQCN>'`
  - 린트: `./gradlew :apps:commerce-api:ktlintCheck`
- **전체 회귀는 `cleanTest` 를 붙여 돌린다.** 단일 클래스 실행이 리포트를 덮어쓴 상태로 집계하면 숫자가 틀린다.
- ⚠️ **`git add -A` 를 쓰지 않는다.** Git 루트인 상위 `study-project/` 에 Firebase 서비스 계정
  **비공개 키**가 미추적으로 있다 (`fcm-project/src/main/resources/`). `.gitignore` 가 막지 않는다.
  경로를 명시해 스테이징한다.
- **`git push` 하지 않는다.**
- 통합·동시성 테스트는 Testcontainers 로 MySQL 8.0 을 띄운다. Docker 가 실행 중이어야 한다.

---

## 기준선

- 브랜치 `feature/order`, HEAD `6f45f6d`
- `./gradlew :apps:commerce-api:test` → **781 tests / 0 failures** (2026-09-13 실측)
- 작업 트리는 `loop-pack-be-l2-vol3-kotlin/.serena/`(미추적 생성물) 외에 깨끗하다.

---

## 파일 구조

### 삭제

| 경로 | 무엇이었나 |
|---|---|
| `domain/product/StockDecreaseStrategy.kt` | 전략 인터페이스 |
| `infrastructure/product/ConditionalUpdateStockDecreaseStrategy.kt` | 조건부 UPDATE 전략 |
| `infrastructure/product/OptimisticLockStockDecreaseStrategy.kt` | 낙관적 락 전략 |
| `infrastructure/product/PessimisticLockStockDecreaseStrategy.kt` | 비관적 락 전략 |
| `test/.../infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt` | 스위치 확인 |
| `test/.../infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt` | 〃 |
| `test/.../infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt` | 〃 |
| `test/.../infrastructure/product/OptimisticLockExceptionTranslationTest.kt` | 예외 번역 게이트 |
| `test/.../domain/product/AbstractStockDecreaseContractTest.kt` | 공통 계약 (추상) |
| `test/.../domain/product/ConditionalUpdateStockDecreaseContractTest.kt` | 〃 실행 |
| `test/.../domain/product/OptimisticLockStockDecreaseContractTest.kt` | 〃 |
| `test/.../domain/product/PessimisticLockStockDecreaseContractTest.kt` | 〃 |
| `test/.../application/order/AbstractOrderFacadeConcurrencySupport.kt` | 동시성 헬퍼 |
| `test/.../application/order/ConditionalUpdateOrderFacadeConcurrencyTest.kt` | 〃 실행 |
| `test/.../application/order/OptimisticLockOrderFacadeConcurrencyTest.kt` | 〃 |
| `test/.../application/order/PessimisticLockOrderFacadeConcurrencyTest.kt` | 〃 |

### 수정

| 경로 | 무엇을 |
|---|---|
| `application/order/OrderFacade.kt` | 재시도 래퍼 제거 → `@Transactional` 복귀 |
| `domain/product/ProductModel.kt` | `@Version version` · `decreaseStockForOptimisticLock()` 제거 |
| `domain/product/ProductRepository.kt` | `decreaseStock` KDoc 에서 전략 위임 문단 제거 |
| `infrastructure/product/ProductRepositoryImpl.kt` | 전략 위임 제거 → `ProductJpaRepository` 직접 호출 |
| `infrastructure/product/ProductJpaRepository.kt` | `findByIdForUpdate` 제거 |
| `application/order/AbstractOrderFacadeConcurrencyTest.kt` | → `OrderFacadeConcurrencyTest.kt` 로 `git mv`, 단일 클래스 복귀 |
| `test/.../application/order/OrderFacadeTest.kt` | `transactionTemplate` 목·`OptimisticLockRetry` 제거 |
| `apps/commerce-api/src/main/resources/application.yml` | `loopers.stock.lock-strategy` 제거 |
| `docker/loadtest-compose.yml` | `LOOPERS_STOCK_LOCKSTRATEGY` 제거 |

---

## 태스크 개요

| # | 태스크 | 기대 테스트 수 |
|---|---|---|
| 1 | `OrderFacade` 를 `@Transactional` 로 되돌린다 (+ 낙관적 락 동시성 테스트) | 781 → 776 |
| 2 | 동시성 테스트를 단일 클래스로 되돌린다 | 776 → 771 |
| 3 | 계약·스위치·번역 테스트를 지운다 | 771 → 747 |
| 4 | 낙관적 락 구현과 `@Version` 을 지운다 | 747 (변화 없음) |
| 5 | 비관적 락 구현과 `findByIdForUpdate` 를 지운다 | 747 (변화 없음) |
| 6 | 전략 계층과 스위치를 지운다 | 747 (변화 없음) |

**순서가 중요하다.** 의존하는 쪽(테스트·`OrderFacade`)을 먼저 지우고 의존받는 쪽(구현·인터페이스)을
나중에 지운다. 거꾸로 하면 중간 태스크마다 컴파일이 깨져 회귀 확인을 할 수 없다.

**끝나면 747 로 돌아간다** — 락 전략 비교 시작 전 수와 같다.

---

### Task 1: `OrderFacade` 를 `@Transactional` 로 되돌린다

**파일:**
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt`
- 수정: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeTest.kt`
- 삭제: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OptimisticLockOrderFacadeConcurrencyTest.kt`

**인터페이스:**
- 사용: 없음 (첫 태스크)
- 제공: `OrderFacade` 생성자가 인자 4 개로 돌아간다 —
  `OrderFacade(userService, productService, orderService, couponService)`.
  태스크 2 가 이 시그니처를 쓴다.

**배경:** 재시도 래퍼는 낙관적 락 하나 때문에 만들었다. 낙관적 락은 버전 충돌을 재시도해야 하고
재시도는 트랜잭션 경계 밖이어야 해서 `@Transactional` 을 `TransactionTemplate` 으로 바꿨다
(2026-09-09 설계 문서 6.2 장). 그 전략이 사라지므로 래퍼도 사라진다.

**이 태스크를 먼저 하는 이유:** `OrderFacade` 가 `ObjectOptimisticLockingFailureException` 을
임포트하고 있어, 낙관적 락 구현보다 먼저 이 의존을 끊어야 한다.

- [ ] **Step 1: 기준선을 실측한다**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
```

기대: `781 tests / 0 failures`. **다르면 이 계획의 모든 기대 테스트 수를 그 차이만큼 보정한다.**

- [ ] **Step 2: `OrderFacade` 생성자와 `place()` 를 되돌린다**

생성자에서 `transactionTemplate` 을 뺀다.

```kotlin
@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService,
    private val couponService: CouponService,
) {
```

`place()` 의 재시도 루프를 지우고, `placeInTransaction` 의 본문을 `place()` 로 되돌린다.
**본문은 한 글자도 바꾸지 않는다** — 이름과 애노테이션만 바뀐다.

```kotlin
    @Transactional
    fun place(command: OrderCommand.Place): OrderInfo {
        val user = getUserOrThrow(command.loginId)
        // ... placeInTransaction 의 본문을 그대로 옮긴다 ...
    }
```

`private companion object` 의 `MAX_OPTIMISTIC_LOCK_ATTEMPTS` 를 지운다. 그 결과 `companion object`
가 비면 블록째 지운다.

아래 임포트를 지운다.

```kotlin
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate
```

- [ ] **Step 3: 상단 KDoc 을 되돌린다**

커밋 `88e8c39` 이 이 KDoc 을 "지금은 TransactionTemplate 을 쓴다" 로 고쳤다. 래퍼가 사라지므로
원래 문단으로 되돌린다.

```kotlin
 * LikeFacade 와 달리 평범한 @Transactional 을 쓴다. (설계 문서 6.8 장)
 * 좋아요가 TransactionTemplate 을 쓴 이유는 경합 예외를 트랜잭션 경계 밖에서 흡수해야 했기 때문인데,
 * 주문은 흡수하지 않는다 — 재고 부족은 409 로 그대로 나가고 그때 롤백되는 것이 정답이다.
 * 흡수할 것이 없으므로 경계를 밖으로 뺄 이유가 없다.
 *
 * 낙관적 락 측정 기간(2026-09-09 ~ 09-13)에만 TransactionTemplate + 재시도 래퍼였다.
 * 비교 결과 조건부 UPDATE 가 유지되어 되돌렸다. (2026-09-09 설계 문서 3.8 · 6.5 장)
```

- [ ] **Step 4: `OrderFacadeTest` 에서 재시도 테스트와 목 배선을 지운다**

`OptimisticLockRetry` 중첩 클래스를 **통째로** 지운다(테스트 2 건).

`init { }` 블록의 `transactionTemplate` 스텁 배선을 통째로 지운다. 필드 선언도 지운다.

```kotlin
    private val userService = mock<UserService>()
    private val productService = mock<ProductService>()
    private val orderService = mock<OrderService>()
    private val couponService = mock<CouponService>()
    private val orderFacade = OrderFacade(userService, productService, orderService, couponService)
```

더 안 쓰이는 임포트를 지운다 — `doAnswer` · `ObjectOptimisticLockingFailureException` ·
`TransactionCallback` · `TransactionTemplate`. **`ktlintCheck` 가 미사용 임포트를 잡는다.**

- [ ] **Step 4b: 낙관적 락 동시성 테스트를 지운다** (2026-09-13 판정으로 추가)

```bash
git rm apps/commerce-api/src/test/kotlin/com/loopers/application/order/OptimisticLockOrderFacadeConcurrencyTest.kt
```

**왜 태스크 2 가 아니라 여기인가.** 이 파일의 두 테스트
(`doesNotOversell_whenRetriesAreExhausted` · `failsSomeRequests_whenContentionExceedsRetryLimit`)는
**재시도 소진이 `CoreException(CONFLICT)` 로 나온다**는 전제 위에 있다. 그 변환을 하는 것이 바로
Step 2 에서 지우는 재시도 래퍼다. 래퍼가 사라지면 `ObjectOptimisticLockingFailureException` 이
그대로 새어 나가 단언이 깨진다.

**테스트와 그것이 고정하던 기능은 같은 태스크에서 사라져야 한다.** 태스크 2 로 미루면 태스크 1
종료 시점에 회귀가 빨간 상태가 되고, 이 계획이 세운 "태스크마다 회귀를 확인한다" 는 전제가 깨진다.

- [ ] **Step 5: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeTest'
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeIntegrationTest'
```

기대: 둘 다 PASS. **통합 테스트는 한 줄도 고치지 않고 통과해야 한다** — 동작이 바뀌지 않았다는 증거다.

- [ ] **Step 6: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: **776 tests / 0 failures** (재시도 단위 테스트 2 + 낙관적 락 동시성 3 = 5 건 감소)

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/OptimisticLockOrderFacadeConcurrencyTest.kt
git commit -m "refactor : 주문의 낙관적 락 재시도 래퍼와 그 계약 테스트를 걷어낸다"
```

---

### Task 2: 동시성 테스트를 단일 클래스로 되돌린다

**파일:**
- 이름 변경: `AbstractOrderFacadeConcurrencyTest.kt` → `OrderFacadeConcurrencyTest.kt`
- 삭제: `AbstractOrderFacadeConcurrencySupport.kt`
- 삭제: `ConditionalUpdateOrderFacadeConcurrencyTest.kt`
- 삭제: `PessimisticLockOrderFacadeConcurrencyTest.kt`

**인터페이스:**
- 사용: `OrderFacade(userService, productService, orderService, couponService)` (태스크 1)
- 제공: 없음

**배경:** 동시성 테스트를 세 갈래로 나눈 것은 세 전략에서 같은 본문을 돌리기 위해서였다
(2026-09-09 계획 태스크 4). 전략이 하나가 되므로 원래의 단일 클래스로 돌아간다.

**기존 3 건의 본문은 한 글자도 바꾸지 않는다.** 조건부 `UPDATE` 에서 통과가 확인된 회귀 방지선이다.

- [ ] **Step 1: 파일 이름을 되돌린다**

```bash
git mv apps/commerce-api/src/test/kotlin/com/loopers/application/order/AbstractOrderFacadeConcurrencyTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeConcurrencyTest.kt
```

**"삭제 후 재생성" 이 아니라 `git mv` 다.** 이 파일은 원래 `OrderFacadeConcurrencyTest.kt` 였고,
두 번의 이름 변경 이력이 `--follow` 로 이어져 있다.

- [ ] **Step 2: 헬퍼를 다시 합치고 구체 클래스로 되돌린다**

`AbstractOrderFacadeConcurrencySupport.kt` 의 필드 · `companion object` · `tearDown` · 헬퍼
(`signUp` · `saveProduct` · `stockOf` · `place` · `runConcurrently`)를 **본문 그대로**
`OrderFacadeConcurrencyTest.kt` 로 옮긴다. 접근자는 `protected` → `private` 으로 좁힌다.

클래스 선언을 아래로 바꾼다. 필드 주입이 아니라 생성자 주입으로 되돌린다 — 상속이 없어졌다.

```kotlin
@SpringBootTest
class OrderFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
```

`expectedStrategy` · `runsOnExpectedStrategy` 를 지운다. `StockDecreaseStrategy` · `KClass`
임포트도 함께 지운다.

클래스 KDoc 첫 문단(세 전략에서 돌린다는 설명)을 지우고, 원래 문단만 남긴다.

- [ ] **Step 3: 나머지 네 파일을 지운다**

```bash
git rm apps/commerce-api/src/test/kotlin/com/loopers/application/order/AbstractOrderFacadeConcurrencySupport.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/application/order/ConditionalUpdateOrderFacadeConcurrencyTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/application/order/PessimisticLockOrderFacadeConcurrencyTest.kt
```

**`OptimisticLockOrderFacadeConcurrencyTest.kt` 는 태스크 1 이 이미 지웠다** — 그 파일은 재시도
래퍼의 계약을 고정하던 것이라 래퍼와 함께 사라졌다 (2026-09-13 판정).

```bash
```

- [ ] **Step 4: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeConcurrencyTest'
```

기대: **3 tests PASS** (초과 판매 방지 · 차감 합계 · 데드락 방지)

- [ ] **Step 5: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: **771 tests / 0 failures** (동시성 8 → 3, 5 건 감소 — 낙관적 락 3 건은 태스크 1 이 이미 뺐다)

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/test/kotlin/com/loopers/application/order/
git commit -m "test : 동시성 테스트를 단일 클래스로 되돌린다"
```

---

### Task 3: 계약·스위치·번역 테스트를 지운다

**파일:**
- 삭제: `test/.../domain/product/AbstractStockDecreaseContractTest.kt`
- 삭제: `test/.../domain/product/ConditionalUpdateStockDecreaseContractTest.kt`
- 삭제: `test/.../domain/product/OptimisticLockStockDecreaseContractTest.kt`
- 삭제: `test/.../domain/product/PessimisticLockStockDecreaseContractTest.kt`
- 삭제: `test/.../infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt`
- 삭제: `test/.../infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt`
- 삭제: `test/.../infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt`
- 삭제: `test/.../infrastructure/product/OptimisticLockExceptionTranslationTest.kt`

**인터페이스:**
- 사용: 없음
- 제공: 없음

**배경 — 커버리지가 사라지지 않는 것을 먼저 확인한다.** 계약 테스트가 검증하던 것(넉넉/동일/부족/
삭제/미존재)은 `ProductServiceIntegrationTest` 의 `DecreaseStock` 중첩 클래스 5 건이 그대로 덮는다.
그 클래스는 이 계획이 건드리지 않는다.

스위치 테스트와 예외 번역 테스트는 **측정 인프라 전용**이다. 스위치가 사라지므로 검증할 대상이 없다.

- [ ] **Step 1: 커버리지가 남는지 먼저 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.product.ProductServiceIntegrationTest'
grep -n "inner class DecreaseStock" apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
```

기대: PASS, 그리고 `DecreaseStock` 중첩 클래스가 존재한다.
**없으면 멈추고 보고한다** — 지우면 재고 차감의 계약 검증이 사라진다.

- [ ] **Step 2: 여덟 파일을 지운다**

```bash
git rm apps/commerce-api/src/test/kotlin/com/loopers/domain/product/AbstractStockDecreaseContractTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ConditionalUpdateStockDecreaseContractTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/domain/product/OptimisticLockStockDecreaseContractTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/domain/product/PessimisticLockStockDecreaseContractTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/OptimisticLockExceptionTranslationTest.kt
```

- [ ] **Step 3: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: **747 tests / 0 failures**
(계약 7 + 6 + 7 = 20, 스위치 3, 번역 1 — 합계 24 건 감소)

- [ ] **Step 4: 커밋**

```bash
git add apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/
git commit -m "test : 락 전략 비교 전용 테스트를 걷어낸다"
```

---

### Task 4: 낙관적 락 구현과 `@Version` 을 지운다

**파일:**
- 삭제: `infrastructure/product/OptimisticLockStockDecreaseStrategy.kt`
- 수정: `domain/product/ProductModel.kt`

**인터페이스:**
- 사용: 없음
- 제공: `ProductModel` 에서 `version` 과 `decreaseStockForOptimisticLock()` 이 사라진다.

**배경:** `@Version` 컬럼을 `BaseEntity` 가 아니라 `ProductModel` 에 직접 둔 것이 여기서 값을 한다
(2026-09-09 설계 문서 6.2 장). 공유 모듈을 건드리지 않았으므로 **이 파일 하나만 고치면 컬럼이 사라진다.**

스키마는 `ddl-auto: create` 라 다음 기동에서 자동으로 반영된다. 마이그레이션 파일이 없다.

- [ ] **Step 1: 낙관적 락 전략 구현을 지운다**

```bash
git rm apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/OptimisticLockStockDecreaseStrategy.kt
```

- [ ] **Step 2: `ProductModel` 에서 `@Version` 필드를 지운다**

`stock` 필드 아래의 아래 블록을 KDoc 째 통째로 지운다.

```kotlin
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set
```

`import jakarta.persistence.Version` 을 지운다.

- [ ] **Step 3: `decreaseStockForOptimisticLock()` 을 지운다**

`change()` 아래, `companion object` 위의 그 메서드를 KDoc 째 통째로 지운다.

- [ ] **Step 4: `change()` 의 KDoc 을 되돌린다**

커밋 `063c49e` 가 이 KDoc 마지막 문단을 낙관적 락을 언급하도록 고쳤다. 되돌린다.

```kotlin
     * stock 이 매개변수에 들어온 것은 재고가 상품의 속성이기 때문이다. (설계 문서 5.6 장)
     * PUT 은 전체 교체이므로 재고도 교체 대상이다.
     * 주문에 의한 차감은 이 경로를 타지 않는다 — 그쪽은 조건부 UPDATE 이며 엔티티를 거치지 않는다.
```

- [ ] **Step 5: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: **747 tests / 0 failures** (테스트 수 변화 없음 — 구현만 사라진다)

**`ProductModelPersistenceTest` 류가 깨지면 `version` 을 단언하고 있는 것이다.** 그 단언만 지운다.

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/
git commit -m "refactor : 낙관적 락 구현과 version 컬럼을 걷어낸다"
```

---

### Task 5: 비관적 락 구현과 `findByIdForUpdate` 를 지운다

**파일:**
- 삭제: `infrastructure/product/PessimisticLockStockDecreaseStrategy.kt`
- 수정: `infrastructure/product/ProductJpaRepository.kt`

**인터페이스:**
- 사용: 없음
- 제공: `ProductJpaRepository.findByIdForUpdate` 가 사라진다.

**배경:** `findByIdForUpdate` 는 비관적 락 전략 전용이다. 다른 호출자가 없다.

- [ ] **Step 1: 다른 호출자가 없는지 확인한다**

```bash
grep -rn "findByIdForUpdate" --include="*.kt" apps/
```

기대: `PessimisticLockStockDecreaseStrategy.kt` 와 `ProductJpaRepository.kt` 두 곳뿐이다.
**다른 곳이 나오면 멈추고 보고한다.**

- [ ] **Step 2: 비관적 락 전략 구현을 지운다**

```bash
git rm apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/PessimisticLockStockDecreaseStrategy.kt
```

- [ ] **Step 3: `findByIdForUpdate` 를 지운다**

`ProductJpaRepository.kt` 에서 KDoc · `@Lock` · `@Query` · 메서드 선언을 통째로 지운다.

더 안 쓰이는 임포트를 지운다.

```kotlin
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
```

`decreaseStock` · `increaseLikeCount` · `decreaseLikeCount` 는 **그대로 둔다.**

- [ ] **Step 4: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: **747 tests / 0 failures**

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/
git commit -m "refactor : 비관적 락 구현과 잠금 조회를 걷어낸다"
```

---

### Task 6: 전략 계층과 스위치를 지운다

**파일:**
- 삭제: `domain/product/StockDecreaseStrategy.kt`
- 삭제: `infrastructure/product/ConditionalUpdateStockDecreaseStrategy.kt`
- 수정: `infrastructure/product/ProductRepositoryImpl.kt`
- 수정: `domain/product/ProductRepository.kt`
- 수정: `apps/commerce-api/src/main/resources/application.yml`
- 수정: `docker/loadtest-compose.yml`

**인터페이스:**
- 사용: 없음
- 제공: `ProductRepositoryImpl.decreaseStock` 이 `ProductJpaRepository.decreaseStock` 을 직접 부른다.
  비교 이전의 모양이다.

**배경:** 구현이 하나뿐인 인터페이스는 간접 층만 남긴다. 6.5 장이 "스위치" 를 걷으라고 한 것의
실행이며, 이로써 [2026-09-11 단일 문장화 계획](2026-09-11-batch-stock-decrease.md)의 Task 4 가
원래 적힌 대로 실행 가능해진다.

- [ ] **Step 1: `ProductRepositoryImpl` 을 되돌린다**

생성자에서 `stockDecreaseStrategy` 를 뺀다.

```kotlin
@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryDslRepository: ProductQueryDslRepository,
) : ProductRepository {
```

`decreaseStock` 구현을 되돌린다.

```kotlin
    override fun decreaseStock(productId: Long, quantity: Int): Int {
        return productJpaRepository.decreaseStock(productId = productId, quantity = quantity)
    }
```

`import com.loopers.domain.product.StockDecreaseStrategy` 를 지운다.

- [ ] **Step 2: `ProductRepository` 의 KDoc 을 되돌린다**

커밋 `4670d4d` 이 `decreaseStock` KDoc 에 더한 마지막 문단(전략 위임 설명)을 지운다.
앞 두 문단은 그대로 둔다.

- [ ] **Step 3: 전략 인터페이스와 조건부 UPDATE 구현을 지운다**

```bash
git rm apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecreaseStrategy.kt \
       apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockDecreaseStrategy.kt
```

- [ ] **Step 4: 스위치 설정을 지운다**

`apps/commerce-api/src/main/resources/application.yml` 의 `local, test` 프로필 블록에서 아래를 지운다.

```yaml
  stock:
    lock-strategy: conditional-update
```

`loopers.admin.stub-credentials` 는 그대로 둔다.

`docker/loadtest-compose.yml` 의 `commerce-api` 서비스 `environment:` 에서 아래를 지운다
(KDoc 성격의 주석 4 줄 포함).

```yaml
      LOOPERS_STOCK_LOCKSTRATEGY: ${LOCK_STRATEGY:-conditional-update}
```

`TZ: UTC` 는 그대로 둔다.

- [ ] **Step 5: 남은 참조가 없는지 확인한다**

```bash
grep -rn "StockDecreaseStrategy\|lock-strategy\|LOCK_STRATEGY\|LOOPERS_STOCK" --include="*.kt" --include="*.yml" apps/ docker/
```

기대: **출력 없음.**

`loadtest/README.md` 의 "락 전략 비교 절차" 섹션은 **지우지 않는다** — 어떻게 쟀는지의 기록이며,
설계 문서 3.7 장이 그 절차로 잰 값을 담고 있다.

- [ ] **Step 6: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: **747 tests / 0 failures** — 락 전략 비교 시작 전과 같은 수다.

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ \
        apps/commerce-api/src/main/resources/application.yml \
        docker/loadtest-compose.yml
git commit -m "refactor : 재고 차감 전략 계층과 전환 스위치를 걷어낸다"
```

---

## 계획 밖

- **`loadtest/ab.js` 의 `ITEMS_PER_ORDER`.** 다중 항목 시나리오는 전략과 무관하게 쓸모가 있고,
  2026-09-11 단일 문장화 계획이 그 하네스로 A/B 를 잰다. **남긴다.**
- **`loadtest/README.md` 의 "락 전략 비교 절차".** 측정 방법의 기록이다. **남긴다.**
- **`loadtest/results/` 의 측정 산출물.** 원자료다. **남긴다.**
- **2026-09-11 단일 문장화 계획의 Task 4 재작성.** 이 정리가 끝나면 그 계획이 적힌 대로
  실행 가능해지므로, 그 계획에 붙은 ⚠️ 경고 블록을 지우는 것은 그 계획의 몫이다.
- **`ProductServiceIntegrationTest` 의 `DecreaseStock` 중첩 클래스.** 2026-09-11 계획 Task 4 가
  지울 대상이다. 이 계획은 건드리지 않는다.
