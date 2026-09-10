# 재고 차감 단일 문장화 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 주문의 재고 차감을 상품마다 한 번씩 보내던 것을 **한 문장으로 접어**, 다중 항목 주문에서
락 보유 시간이 항목 수에 비례해 늘어나는 것을 없앤다.

**아키텍처:** 조건부 `UPDATE` 전략은 그대로 유지한다. `WHERE stock >= :quantity` 를 상품마다 다른
값으로 평가하기 위해 `CASE` 를 쓰고, 전체 성공 여부는 **영향 행 수가 항목 수와 같은가**로 판정한다.
항목 수가 요청마다 달라 `@Query` 애노테이션으로 표현할 수 없으므로, JPQL 을 동적으로 조립하는
인프라 클래스를 하나 새로 둔다. 낙관적 락 · 비관적 락은 도입하지 않는다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Spring Data JPA / QueryDSL / MySQL 8.0 /
JUnit 5 · AssertJ · Mockito / Testcontainers / k6

**설계 문서:** `docs/superpowers/specs/2026-09-09-batch-stock-decrease-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **응답·주석·커밋 메시지·문서는 한국어.** 변수명·함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백이 있다. (`feat : ...`, `test : ...`, `refactor : ...`)
- **`modules/` 와 저장소 루트의 `supports/` 를 수정하지 않는다.** 특히 `modules/jpa` 의 `BaseEntity` 는
  세 앱이 공유한다. 이 계획은 `BaseEntity` 를 건드릴 일이 없다 — 건드리고 싶어지면 그것은 설계가
  어긋났다는 신호다 (설계 문서 7.4 장).
- **새 `ErrorType` 상수를 만들지 않는다.** `INTERNAL_ERROR` · `BAD_REQUEST` · `UNAUTHORIZED` ·
  `NOT_FOUND` · `CONFLICT` 다섯뿐이다.
- **`ktlintFormat` 을 실행하지 않는다.** 무관한 파일까지 건드린다. 검증은 `ktlintCheck` 로 한다.
- **ktlint 최대 줄 길이 130 자** (유니코드 문자 수 기준). `*Test.kt` 는 예외다.
- **블록 주석 안에 `/**` 를 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되어 `Unclosed comment` 로 컴파일이 깨진다.
- **주석은 "무엇" 이 아니라 "왜" 를 적는다.** 설계 문서를 인용할 때는 **항상 날짜를 밝힌다.**
  이 계획이 새로 쓰는 인용은 전부 `(2026-09-09 설계 문서 N 장)` 형식이다. 앞선 문서를 가리킬 때는
  `(2026-09-06 설계 문서 N 장)` · `(2026-08-24 설계 문서 N 장)` 로 적는다.
  **날짜 없는 `(설계 문서 N 장)` 을 새로 쓰지 않는다.**
- **기존 주석의 날짜 없는 인용은 건드리지 않는다.** 그 파일을 지배하던 문서를 가리키므로 여전히 옳다.
  예외는 KDoc 블록을 통째로 다시 쓰는 경우뿐이다.
- **`@Transactional` 을 동시성 테스트에 붙이지 않는다.** 붙이면 스레드가 각자의 트랜잭션을 갖지 못해
  경합이 일어나지 않고, 테스트가 초록인 채 아무것도 검증하지 않게 된다.
- **모든 Gradle 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** 이 디렉터리가 Gradle 루트다.
  상위 `study-project/` 에도 별도의 `gradlew` 가 있어 거기서 돌리면 프로젝트를 찾지 못한다.
  단 **Git 루트는 상위 `study-project/` 다** — `git status` 는 이 디렉터리 밖의 변경까지 보여주므로
  작업 트리를 판정할 때는 경로를 좁혀서 본다.
  - 전체: `./gradlew :apps:commerce-api:test`
  - 단일 클래스: `./gradlew :apps:commerce-api:test --tests 'com.loopers.<FQCN>'`
  - 린트: `./gradlew :apps:commerce-api:ktlintCheck`
- 통합·E2E 테스트는 Testcontainers 로 MySQL 8.0 을 띄운다. Docker 가 실행 중이어야 한다.

---

## 기준선

작업 시작 전 상태다. 회귀 판정의 기준이 된다.

- 브랜치 `feature/order`, HEAD `8bb505d`
- `./gradlew :apps:commerce-api:test` → **747 tests / 0 failures** (2026-09-06 리포트 기준)
  - **Task 1 을 시작하기 전에 한 번 실측해 확인한다.** 이후 모든 태스크의 기대 테스트 수
    (752 → 759 → 759 → 754 → 756)가 이 값에 물려 있어, 어긋나면 전부 보정해야 한다.
- **작업 트리는 깨끗하지 않다.** `loop-pack-be-l2-vol3-kotlin/` 안은 비어 있지만, Git 루트인
  상위 `study-project/` 에 이 계획과 무관한 변경이 남아 있다.
  - `gradlew` 파일 모드 변경 — 상위 저장소의 것이다. **되돌리지 않고 그대로 둔다.**
  - `docs/superpowers/plans/2026-09-01-coupon-admin.md` 수정분 — 이 계획과 무관하다.
  - 여러 미추적 디렉터리(`.serena/`, `chat-client/data/` 등)

  이것들은 자기 변경이 아니므로 **커밋할 때 경로를 명시해 스테이징한다.** `git add -A` 를 쓰지 않는다.

---

## 파일 구조

### 신규

| 경로 | 책임 |
|---|---|
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecrease.kt` | 재고 차감 한 건의 요청. 상품 ID 와 수량의 유효성을 생성 시점에 보장한다 |
| `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductStockJpqlRepository.kt` | 항목 수만큼 `CASE` 를 조립해 한 문장으로 차감한다 |
| `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockDecreaseTest.kt` | 값 객체 순수 단위 테스트 |

### 수정

| 경로 | 무엇을 |
|---|---|
| `domain/product/ProductRepository.kt` | `decreaseStock` → `decreaseStocks` 로 계약 교체 |
| `domain/product/ProductService.kt` | 같은 교체 + 중복 상품 방어 |
| `infrastructure/product/ProductRepositoryImpl.kt` | 새 인프라 클래스로 위임 |
| `infrastructure/product/ProductJpaRepository.kt` | `decreaseStock` 제거 |
| `application/order/OrderFacade.kt` | 차감 루프 제거, 한 번 호출로 교체 |
| `test/.../domain/product/ProductServiceIntegrationTest.kt` | `DecreaseStock` 중첩 클래스를 `DecreaseStocks` 로 이관 |
| `test/.../application/order/OrderFacadeTest.kt` | 스텁·검증을 새 시그니처로 |
| `test/.../application/order/OrderFacadeConcurrencyTest.kt` | 다중 항목 동시성 테스트 추가 |
| `loadtest/ab.js` | `ITEMS_PER_ORDER` 시나리오 추가 |
| `loadtest/README.md` | 새 환경변수 문서화 |

---

## 태스크 개요

| # | 태스크 | 기대 테스트 수 |
|---|---|---|
| 1 | `StockDecrease` 값 객체 | 747 → 752 |
| 2 | 배치 차감 쿼리와 계약 | 752 → 759 |
| 3 | `OrderFacade` 전환 | 759 (변화 없음 — 테스트를 더하지 않고 바꾼다) |
| 4 | 옛 단일 차감 API 제거 | 759 → 754 |
| 5 | 다중 항목 동시성 테스트 | 754 → 756 |
| 6 | 부하 하네스 다중 항목 시나리오 | 756 (변화 없음 — k6 는 Gradle 테스트가 아니다) |

**태스크 2 가 이 계획의 중심이다.** 초과 판매 방지가 걸린 `WHERE` 절이 통째로 다시 쓰인다.
`stock >= :quantity` 가 **상품마다 다른 값으로** 평가되지 않으면 조용히 초과 판매가 난다.

태스크 4 를 태스크 3 뒤에 두는 이유는 **되돌릴 수 있는 지점을 남기기 위해서다.** 태스크 3 까지는
옛 API 가 살아 있어 문제가 생기면 `OrderFacade` 한 줄만 되돌리면 된다.

---

### Task 1: `StockDecrease` 값 객체

**파일:**
- 생성: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecrease.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockDecreaseTest.kt`

**인터페이스:**
- 사용: 없음 (첫 태스크)
- 제공: `com.loopers.domain.product.StockDecrease` — `data class StockDecrease(val productId: Long, val quantity: Int)`.
  태스크 2 의 `ProductRepository.decreaseStocks(items: List<StockDecrease>)` 와
  태스크 3 의 `OrderFacade` 가 이 타입을 쓴다.

**배경:** `quantity` 를 `Int` 로 두고 `domain.order.Quantity` 를 재사용하지 않는다.
`Quantity` 는 주문 패키지에 있고, 그것을 쓰면 **`domain.product` 가 `domain.order` 를 알게 된다.**
상품은 주문을 모르는 쪽이 옳다 — 브랜드가 상품을 모르는 것과 같은 방향이다.
대신 같은 규칙(1 이상)을 이 값 객체가 스스로 갖는다.

`Stock.value` 는 `Long` 인데 `quantity` 는 `Int` 다. 기존 `decreaseStock(productId, quantity: Int)` 의
타입을 그대로 승계한 것이며, 실제 차감 쿼리에서 `Long` 으로 바인딩한다 (태스크 2).

- [ ] **Step 1: 기준선 실측**

```bash
./gradlew :apps:commerce-api:test
```

기대: `747 tests / 0 failures`. **다르면 이 계획의 모든 기대 테스트 수를 그 차이만큼 보정한다.**

- [ ] **Step 2: 실패하는 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockDecreaseTest.kt`

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("재고 차감 요청 단위 테스트")
class StockDecreaseTest {
    @DisplayName("재고 차감 요청을 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("상품 ID 와 수량이 유효하면, 그대로 담긴다.")
        @Test
        fun holdsValues_whenValid() {
            // act
            val decrease = StockDecrease(productId = 7L, quantity = 3)

            // assert
            assertAll(
                { assertThat(decrease.productId).isEqualTo(7L) },
                { assertThat(decrease.quantity).isEqualTo(3) },
            )
        }

        /**
         * Quantity 와 같은 규칙이다. 0 개를 차감하는 요청은 아무것도 하지 않으면서
         * 영향 행 수만 1 을 채워, 호출자에게 "성공했다" 고 거짓말한다.
         */
        @DisplayName("수량이 1 미만이면, BAD_REQUEST 가 난다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        fun throwsBadRequest_whenQuantityIsBelowOne(quantity: Int) {
            // act
            val result = assertThrows<CoreException> { StockDecrease(productId = 1L, quantity = quantity) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품 ID 가 0 이하면, BAD_REQUEST 가 난다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenProductIdIsNotPositive(productId: Long) {
            // act
            val result = assertThrows<CoreException> { StockDecrease(productId = productId, quantity = 1) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

- [ ] **Step 3: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.product.StockDecreaseTest'
```

기대: 컴파일 실패 — `Unresolved reference: StockDecrease`

- [ ] **Step 4: 최소 구현**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecrease.kt`

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 재고 차감 한 건의 요청.
 *
 * quantity 를 domain.order.Quantity 로 받지 않는 이유는 그러면 domain.product 가 domain.order 를
 * 알게 되기 때문이다. 상품은 주문을 모르는 쪽이 옳다. 같은 규칙(1 이상)을 여기서 다시 세운다.
 *
 * 이 객체가 만들어졌다는 사실이 검증 통과를 의미하므로, 차감 쿼리는 값을 다시 확인하지 않는다.
 * (2026-09-09 설계 문서 4.5 장)
 */
data class StockDecrease(val productId: Long, val quantity: Int) {
    init {
        if (productId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.")
        }
        if (quantity < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.")
        }
    }
}
```

- [ ] **Step 5: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.product.StockDecreaseTest'
./gradlew :apps:commerce-api:ktlintCheck
```

기대: 5 tests PASS, ktlint 통과

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecrease.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockDecreaseTest.kt
git commit -m "feat : 재고 차감 요청 값 객체를 추가한다"
```

---

### Task 2: 배치 차감 쿼리와 계약

**파일:**
- 생성: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductStockJpqlRepository.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt` — `decreaseStocks` 추가
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt` — 위임 추가
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt` — `decreaseStocks` 추가
- 수정: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` — `DecreaseStocks` 중첩 클래스 추가

**인터페이스:**
- 사용: `StockDecrease` (태스크 1)
- 제공:
  - `ProductRepository.decreaseStocks(items: List<StockDecrease>): Int` — 영향 행 수
  - `ProductService.decreaseStocks(items: List<StockDecrease>): Boolean` — 전부 성공했을 때만 `true`
  - `ProductStockJpqlRepository.decreaseStocks(items: List<StockDecrease>): Int`

**배경:** 옛 `decreaseStock` 을 **지우지 않고 나란히 둔다.** 태스크 4 까지 되돌릴 지점을 남기기 위해서다.

`@Query` 애노테이션을 쓸 수 없다. `CASE` 절의 길이가 항목 수에 따라 달라지기 때문이다.
QueryDSL 도 쓰지 않는다 — `CaseBuilder` 체인은 최종 SQL 의 모양을 가리는데,
**이 쿼리는 초과 판매 방지를 직접 지고 있어 `WHERE` 절이 눈으로 읽혀야 한다.**

`@Modifying(clearAutomatically = true, flushAutomatically = true)` 가 해 주던 일을 직접 해야 한다.
그 애노테이션은 Spring Data 의 기능이지 JPA 의 기능이 아니므로, `EntityManager` 를 직접 쓰면 따라오지 않는다.
**비우지 않으면 이 트랜잭션 안의 `ProductModel` 이 DB 와 어긋난 재고를 들고 남는다.**

`ProductModel` 에 `@Check(constraints = "stock >= 0")` 이 걸려 있다. `CASE` 를 잘못 조립해
`WHERE` 의 판정과 `SET` 의 차감이 어긋나면 **DB 가 거부한다.** 이것은 안전망이지 검증이 아니다 —
테스트가 통과해야 한다.

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`ProductServiceIntegrationTest.kt` 의 **마지막 중첩 클래스 뒤, 바깥 클래스 닫는 중괄호 앞**에 추가한다.

```kotlin
    @DisplayName("여러 상품의 재고를 한 번에 차감할 때, ")
    @Nested
    inner class DecreaseStocks {
        @DisplayName("모두 넉넉하면, 상품마다 요청한 수량만큼 줄고 true 를 반환한다.")
        @Test
        fun decreasesEachByItsQuantity_whenAllSufficient() {
            // arrange
            val first = saveProduct(stock = 10)
            val second = saveProduct(stock = 20)

            // act
            val decreased = productService.decreaseStocks(
                listOf(
                    StockDecrease(productId = first.id, quantity = 3),
                    StockDecrease(productId = second.id, quantity = 7),
                ),
            )

            // assert
            // 상품마다 다른 수량이 적용되는지가 이 단언의 핵심이다.
            // CASE 를 빠뜨리고 한 값으로 차감하면 7 / 13 이 아니라 같은 값이 빠진다.
            assertAll(
                { assertThat(decreased).isTrue() },
                { assertThat(productRepository.findById(first.id)!!.stock.value).isEqualTo(7L) },
                { assertThat(productRepository.findById(second.id)!!.stock.value).isEqualTo(13L) },
            )
        }

        /**
         * 이 단언이 초과 판매 방지의 본체다.
         * WHERE 의 CASE 를 지우고 고정값으로 판정하면 모자란 상품까지 차감되어 재고가 음수가 된다.
         * (DB 의 CHECK 제약이 그때 터지지만, 그것은 안전망이지 이 테스트의 대체물이 아니다)
         */
        @DisplayName("한 상품이라도 모자라면, 그 상품은 차감되지 않고 false 를 반환한다.")
        @Test
        fun returnsFalse_andSkipsInsufficientProduct_whenAnyIsInsufficient() {
            // arrange
            val enough = saveProduct(stock = 10)
            val notEnough = saveProduct(stock = 2)

            // act
            val decreased = productService.decreaseStocks(
                listOf(
                    StockDecrease(productId = enough.id, quantity = 3),
                    StockDecrease(productId = notEnough.id, quantity = 5),
                ),
            )

            // assert
            assertAll(
                { assertThat(decreased).isFalse() },
                { assertThat(productRepository.findById(notEnough.id)!!.stock.value).isEqualTo(2L) },
                {
                    // 이 계층은 부분 차감을 되돌리지 않는다. 넉넉했던 상품은 줄어든 채로 남는다.
                    // 되돌리는 것은 호출자의 몫이며, OrderFacadeIntegrationTest 의
                    // throwsConflict_andRollsBackEverything_whenAnyStockIsInsufficient 가 그것을 증명한다.
                    assertThat(productRepository.findById(enough.id)!!.stock.value)
                        .describedAs("부분 차감은 이 계층에서 되돌리지 않는다 — 롤백은 호출자의 몫이다")
                        .isEqualTo(7L)
                },
            )
        }

        @DisplayName("재고와 요청 수량이 같으면, 0 이 되고 true 를 반환한다.")
        @Test
        fun decreasesToZero_whenStockEqualsQuantity() {
            // arrange
            val product = saveProduct(stock = 5)

            // act
            val decreased = productService.decreaseStocks(listOf(StockDecrease(product.id, 5)))

            // assert
            assertAll(
                { assertThat(decreased).isTrue() },
                { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(0L) },
            )
        }

        @DisplayName("삭제된 상품이 섞이면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenAnyProductIsSoftDeleted() {
            // arrange
            val alive = saveProduct(stock = 10)
            val deleted = saveProduct(stock = 10)
            productService.delete(deleted.id)

            // act
            val decreased = productService.decreaseStocks(
                listOf(StockDecrease(alive.id, 1), StockDecrease(deleted.id, 1)),
            )

            // assert
            assertThat(decreased).isFalse()
        }

        @DisplayName("존재하지 않는 상품이 섞이면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenAnyProductDoesNotExist() {
            // arrange
            val product = saveProduct(stock = 10)

            // act
            val decreased = productService.decreaseStocks(
                listOf(StockDecrease(product.id, 1), StockDecrease(99999L, 1)),
            )

            // assert
            assertThat(decreased).isFalse()
        }

        /**
         * CASE 는 한 행에 한 번만 매칭되므로, 같은 상품이 두 번 들어오면 뒤엣것이 조용히 무시된다.
         * 영향 행 수로는 그것을 구분할 수 없다. 그래서 쿼리에 닿기 전에 막는다.
         * OrderCommand.Place 가 이미 같은 조건을 400 으로 막지만(2026-08-24 설계 문서 6.7 장),
         * 그것은 주문 경로의 규칙이고 이것은 차감 계약의 전제다.
         */
        @DisplayName("같은 상품이 두 번 들어오면, BAD_REQUEST 가 난다.")
        @Test
        fun throwsBadRequest_whenProductIdIsDuplicated() {
            // arrange
            val product = saveProduct(stock = 10)

            // act
            val result = assertThrows<CoreException> {
                productService.decreaseStocks(
                    listOf(StockDecrease(product.id, 1), StockDecrease(product.id, 2)),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("항목이 하나뿐이어도, 단일 차감과 똑같이 동작한다.")
        @Test
        fun behavesLikeSingleDecrease_whenOnlyOneItem() {
            // arrange
            val product = saveProduct(stock = 10)

            // act
            val decreased = productService.decreaseStocks(listOf(StockDecrease(product.id, 4)))

            // assert
            assertAll(
                { assertThat(decreased).isTrue() },
                { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(6L) },
            )
        }
    }
```

**임포트를 확인한다.** 파일 상단에 `com.loopers.domain.product.StockDecrease` 는 같은 패키지라 불필요하지만,
`com.loopers.support.error.CoreException` · `ErrorType` · `org.junit.jupiter.api.assertThrows` 가
없으면 추가한다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.product.ProductServiceIntegrationTest'
```

기대: 컴파일 실패 — `Unresolved reference: decreaseStocks`

- [ ] **Step 3: 도메인 계약을 추가한다**

`ProductRepository.kt` 의 `decreaseStock` 선언 **바로 아래**에 추가한다.

```kotlin
    /**
     * 여러 상품의 재고를 한 문장으로 차감한다. 반환값은 영향 행 수다.
     *
     * 상품마다 stock >= quantity 를 따로 판정하므로, 한 상품이라도 모자라면 그 행만 갱신되지 않는다.
     * 따라서 호출자는 반환값이 items.size 와 같은지로 전체 성공을 판정한다. (2026-09-09 설계 문서 4.2 장)
     *
     * 한 문장인 것이 이 계약의 핵심이다. 상품마다 UPDATE 를 따로 보내면 첫 UPDATE 가 잡은 배타 락이
     * 나머지 왕복을 전부 기다려, 락 보유 시간이 항목 수에 비례해 늘어난다. (같은 문서 3.1 장)
     *
     * 부분 차감을 되돌리지 않는다. 되돌리는 것은 호출자의 트랜잭션이다.
     */
    fun decreaseStocks(items: List<StockDecrease>): Int
```

- [ ] **Step 4: 인프라 구현을 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductStockJpqlRepository.kt`

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecrease
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/**
 * 여러 상품의 재고를 한 문장으로 차감한다.
 *
 * 항목 수가 요청마다 달라 CASE 절의 길이가 고정되지 않으므로, Query 애노테이션으로는 표현할 수 없다.
 * (2026-09-09 설계 문서 4.5 장)
 *
 * QueryDSL 을 쓰지 않는 이유는 이 쿼리가 초과 판매 방지를 직접 지고 있기 때문이다.
 * CaseBuilder 체인은 최종 SQL 의 모양을 가리는데, 여기서는 WHERE 절에 항목별 수량이 실제로
 * 실려 나가는지가 리뷰의 핵심이다. JPQL 문자열이면 그대로 읽힌다.
 *
 * 호출자가 items 가 비어 있지 않음을 보장한다 — ProductRepositoryImpl 이 막는다.
 */
@Component
class ProductStockJpqlRepository(
    private val entityManager: EntityManager,
) {
    fun decreaseStocks(items: List<StockDecrease>): Int {
        // 검색 CASE(WHEN <조건> THEN <값>)를 쓴다. 단순 CASE(CASE p.id WHEN :id0)는 WHEN 피연산자에
        // 파라미터를 두는 형태라 JPA 구현체마다 처리가 갈린다.
        // ELSE 0 은 방어다. IN 절이 대상 행을 이미 좁히므로 도달하지 않지만, 도달했을 때
        // stock - NULL = NULL 로 재고가 통째로 지워지는 것보다 아무것도 빼지 않는 편이 낫다.
        val decrement = items.indices.joinToString(
            separator = " ",
            prefix = "CASE ",
            postfix = " ELSE 0 END",
        ) { index -> "WHEN p.id = :id$index THEN :quantity$index" }

        // updated_at 을 건드리지 않는다. 재고가 빠진 것은 상품을 편집한 것이 아니다.
        // (2026-08-24 설계 문서 6.3 장 — 옛 decreaseStock 과 같은 판단이다)
        val jpql = """
            UPDATE ProductModel p
               SET p.stock.value = p.stock.value - $decrement
             WHERE p.id IN :ids
               AND p.deletedAt IS NULL
               AND p.stock.value >= $decrement
        """.trimIndent()

        val query = entityManager.createQuery(jpql)
        items.forEachIndexed { index, item ->
            query.setParameter("id$index", item.productId)
            // Stock.value 가 Long 이므로 비교와 뺄셈의 타입을 맞춘다.
            query.setParameter("quantity$index", item.quantity.toLong())
        }
        query.setParameter("ids", items.map { it.productId })

        // Modifying(flushAutomatically = true, clearAutomatically = true) 가 해 주던 일을 직접 한다.
        // 그것은 Spring Data 의 기능이지 JPA 의 기능이 아니라, EntityManager 를 직접 쓰면 따라오지 않는다.
        // 벌크 UPDATE 는 1차 캐시를 우회하므로, 비우지 않으면 이 트랜잭션 안의 ProductModel 이
        // DB 와 어긋난 재고를 들고 남는다.
        entityManager.flush()
        val affected = query.executeUpdate()
        entityManager.clear()

        return affected
    }
}
```

- [ ] **Step 5: `ProductRepositoryImpl` 에 위임을 잇는다**

생성자에 의존을 추가한다.

```kotlin
@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryDslRepository: ProductQueryDslRepository,
    private val productStockJpqlRepository: ProductStockJpqlRepository,
) : ProductRepository {
```

`decreaseStock` 구현 **바로 아래**에 추가한다.

```kotlin
    override fun decreaseStocks(items: List<StockDecrease>): Int {
        // IN () 은 문법 오류이고 차감할 대상도 없으므로 쿼리 자체를 보내지 않는다. findAllByIds 와 같은 처리다.
        if (items.isEmpty()) return 0

        return productStockJpqlRepository.decreaseStocks(items)
    }
```

`com.loopers.domain.product.StockDecrease` 임포트를 추가한다.

- [ ] **Step 6: `ProductService` 에 유스케이스를 추가한다**

`decreaseStock` **바로 아래**에 추가한다.

```kotlin
    /**
     * 여러 상품의 재고를 한 번에 차감한다. 전부 성공했을 때만 true 다.
     *
     * false 는 두 가지를 뜻한다 — 어느 상품의 재고가 모자랐거나, 그 사이 삭제됐거나.
     * 어느 상품인지도 구분하지 않는다. 영향 행 수는 몇 개가 실패했는지만 알려주고 어느 것인지는 모른다.
     * 알아내려면 다시 조회해야 하는데, 그 조회는 같은 경합을 겪으면서 롤백될 값을 읽는다.
     * (2026-09-09 설계 문서 4.6 장)
     *
     * decreaseStock 과 달리 부분 차감이 남을 수 있다. 호출자는 반드시 이 값을 보고 롤백해야 한다.
     */
    @Transactional
    fun decreaseStocks(items: List<StockDecrease>): Boolean {
        // CASE 는 한 행에 한 번만 매칭되므로 중복이 조용히 무시된다. 영향 행 수로는 구분할 수 없어
        // 쿼리에 닿기 전에 막는다. (2026-09-09 설계 문서 4.3 장)
        if (items.map { it.productId }.distinct().size != items.size) {
            throw CoreException(ErrorType.BAD_REQUEST, "같은 상품을 여러 번 차감할 수 없습니다.")
        }

        return productRepository.decreaseStocks(items) == items.size
    }
```

`CoreException` · `ErrorType` 임포트가 이미 있는지 확인하고, 없으면 추가한다.

- [ ] **Step 7: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.product.ProductServiceIntegrationTest'
```

기대: `DecreaseStocks` 7 tests PASS

**실패하면 먼저 생성된 SQL 을 본다.** `--spring.jpa.show-sql=true` 로 띄우고
`WHERE` 절에 `stock >= (case when ...)` 이 실려 있는지 확인한다.
`WHERE` 의 `CASE` 가 빠졌다면 초과 판매가 나는 상태다 — **그 상태로 진행하지 않는다.**

- [ ] **Step 8: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: `759 tests / 0 failures`

- [ ] **Step 9: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductStockJpqlRepository.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
git commit -m "feat : 여러 상품의 재고를 한 문장으로 차감하는 경로를 추가한다"
```

---

### Task 3: `OrderFacade` 전환

**파일:**
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt`
- 수정: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeTest.kt`

**인터페이스:**
- 사용: `ProductService.decreaseStocks(items: List<StockDecrease>): Boolean` (태스크 2)
- 제공: 없음 (내부 전환)

**배경:** 차감 루프가 사라지면서 `OrderFacadeTest` 의 스텁과 검증이 전부 바뀐다.
특히 **"첫 상품이 실패하면 두 번째는 차감되지 않는다"** 는 단언은 뜻을 잃는다 — 호출이 하나뿐이기 때문이다.
그 자리를 **"차감이 정확히 한 번만 호출된다"** 가 대신한다. 이것이 이번 개선의 계약이며,
누군가 루프로 되돌리면 여기서 걸린다.

`sortedBy { it.productId }` 는 **남긴다.** 한 문장이 되면서 MySQL 이 인덱스 순서로 락을 잡으므로
데드락 방지의 필수 요건은 아니게 됐지만, 비용이 없고 이 구조가 되돌려질 때 다시 필요해진다.
**성격이 바뀌었다는 사실을 주석에 남기지 않으면 다음 사람이 "쓸모없는 정렬" 로 보고 지운다.**
(2026-09-09 설계 문서 4.4 장 · 7.3 장)

- [ ] **Step 1: `OrderFacade` 를 바꾼다**

기존 차감 블록(주석 포함)을 통째로 아래로 교체한다.

```kotlin
        // 재고 차감을 한 문장으로 보낸다. 상품마다 UPDATE 를 따로 보내면 첫 UPDATE 가 잡은 배타 락이
        // 나머지 왕복을 전부 기다려, 락 보유 시간이 항목 수에 비례해 늘어난다. (2026-09-09 설계 문서 3.1 장)
        //
        // 0 행은 재고 부족과 상품 소멸을 함께 뜻한다. 어느 상품인지도 구분하지 않는다 —
        // 주문할 수 없다는 결론이 같고, 나누려면 다시 조회해야 하는데 그 조회도 같은 경합을 겪는다.
        val decreases = sorted.map { StockDecrease(productId = it.productId, quantity = it.quantity.value) }
        if (!productService.decreaseStocks(decreases)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[productIds = ${decreases.map { it.productId }}] 재고가 부족하거나 주문할 수 없는 상품이 포함되어 있습니다.",
            )
        }
```

`sorted` 를 만드는 줄의 주석도 바꾼다.

```kotlin
        // 정렬은 이제 필수가 아니라 이중 안전장치다. 차감이 한 문장이 되면서 MySQL 이 인덱스 순서로
        // 락을 잡으므로 데드락 방지는 엔진이 보장한다. 그럼에도 남기는 것은 비용이 없고, 이 구조가
        // 되돌려지거나 다른 경로가 상품을 여러 번 갱신하게 될 때 다시 필요해지기 때문이다.
        // (2026-09-09 설계 문서 4.4 장) 저장되는 항목의 순서는 요청 순서 그대로다.
        val sorted = command.items.sortedBy { it.productId }
```

`com.loopers.domain.product.StockDecrease` 임포트를 추가한다.

- [ ] **Step 2: 컴파일 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeTest'
```

기대: 컴파일 실패 — 목이 `decreaseStock` 을 스텁하고 있어 `decreaseStocks` 가 `false` 를 돌려준다.

- [ ] **Step 3: 스텁을 일괄 교체한다**

`OrderFacadeTest.kt` 안의 스텁 11 곳을 바꾼다.

```
whenever(productService.decreaseStock(any(), any())).thenReturn(true)
  → whenever(productService.decreaseStocks(any())).thenReturn(true)          (9 곳)

whenever(productService.decreaseStock(any(), any())).thenReturn(false)
  → whenever(productService.decreaseStocks(any())).thenReturn(false)         (2 곳)

whenever(productService.decreaseStock(productId = eq(1L), quantity = any())).thenReturn(false)
  → whenever(productService.decreaseStocks(any())).thenReturn(false)         (1 곳)
```

`never()` 검증 4 곳도 바꾼다.

```
verify(productService, never()).decreaseStock(any(), any())
  → verify(productService, never()).decreaseStocks(any())
```

`inOrder` 검증 2 곳도 바꾼다.

```
ordered.verify(productService).decreaseStock(productId = any(), quantity = any())
  → ordered.verify(productService).decreaseStocks(any())
```

- [ ] **Step 4: 순서·수량 검증을 인자 캡처로 바꾼다**

차감이 한 번뿐이라 `inOrder` 로 상품 순서를 볼 수 없다. 넘긴 목록의 내용을 본다.

**`decreasesStockInAscendingProductIdOrder`** (`Place` 중첩 클래스 첫 테스트)의 이름을
`passesDecreasesSortedByProductId` 로 바꾸고, `@DisplayName` 과 assert 블록을 교체한다.
arrange · act 는 그대로 두고 스텁 한 줄만 Step 3 대로 바뀐다.

```kotlin
        @DisplayName("요청 순서와 무관하게 차감 목록은 상품 ID 오름차순으로 넘어간다.")
        @Test
        fun passesDecreasesSortedByProductId() {
```

```kotlin
            // assert
            // OrderFacade 의 데드락 방지 계약 — 이제 한 문장이 되어 MySQL 이 인덱스 순서로 락을 잡지만,
            // 정렬은 이중 안전장치로 남아 있다. (2026-09-09 설계 문서 4.4 장)
            // 통합 테스트로는 차감 순서를 관찰할 수 없어 이 단위 테스트가 계약을 고정한다.
            // 아래 "요청 순서 그대로 저장" 케이스와 짝이다 — 정렬한 것은 차감 순서뿐이라는 계약의 반쪽씩이다.
            val captor = argumentCaptor<List<StockDecrease>>()
            verify(productService).decreaseStocks(captor.capture())
            assertThat(captor.firstValue.map { it.productId }).containsExactly(1L, 2L, 3L)
```

**`decreasesStockByRequestedQuantityPerItem`** 의 assert 블록을 교체한다. 이름은 그대로 둔다.

```kotlin
            // assert
            val captor = argumentCaptor<List<StockDecrease>>()
            verify(productService).decreaseStocks(captor.capture())
            assertThat(captor.firstValue)
                .containsExactly(StockDecrease(productId = 1L, quantity = 2), StockDecrease(productId = 2L, quantity = 5))
```

임포트를 추가한다.

```kotlin
import com.loopers.domain.product.StockDecrease
import org.mockito.kotlin.argumentCaptor
```

`inOrder` · `eq` 가 파일의 다른 테스트에서도 안 쓰이게 됐다면 임포트를 지운다.
**`ktlintCheck` 가 미사용 임포트를 잡는다.**

- [ ] **Step 5: `stopsDecreasingStock_whenEarlierItemFails` 를 대체한다**

이 테스트는 뜻을 잃는다 — 차감 호출이 하나뿐이라 "뒤 항목" 이 없다.
**테스트 전체를 아래로 교체한다.**

```kotlin
        /**
         * 이 단언이 이번 개선의 계약이다. 차감을 상품마다 보내면 첫 UPDATE 가 잡은 배타 락이 나머지
         * 왕복을 전부 기다려, 락 보유 시간이 항목 수에 비례해 늘어난다. (2026-09-09 설계 문서 3.1 장)
         * 누군가 루프로 되돌리면 여기서 걸린다.
         *
         * 통합 테스트로는 이 계약을 관찰할 수 없다 — DB 최종 상태는 한 문장으로 보내든 나눠 보내든 같다.
         * 옛 stopsDecreasingStock_whenEarlierItemFails 가 이 자리에 있었고, "앞 항목이 실패하면 뒤는
         * 차감하지 않는다" 를 봤다. 호출이 하나가 되면서 그 질문 자체가 사라졌다.
         */
        @DisplayName("항목이 여럿이어도, 재고 차감은 정확히 한 번만 호출된다.")
        @Test
        fun callsDecreaseStocksOnce_whenPlacingOrder() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L), product(2L), product(3L)))
            whenever(orderService.place(any(), any(), any(), anyOrNull()))
                .thenReturn(order(items = listOf(orderItem(1L), orderItem(2L), orderItem(3L))))
            whenever(productService.decreaseStocks(any())).thenReturn(true)

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(
                    OrderCommand.Item(productId = 1L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 2L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 3L, quantity = Quantity(1)),
                ),
            )

            // act
            orderFacade.place(command)

            // assert
            verify(productService, times(1)).decreaseStocks(any())
        }
```

`org.mockito.kotlin.times` 임포트를 추가한다.

- [ ] **Step 6: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeTest'
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeIntegrationTest'
```

기대: 둘 다 PASS. **통합 테스트는 한 줄도 고치지 않고 통과해야 한다** —
동작이 바뀌지 않았다는 증거다. 고쳐야 통과한다면 무언가 잘못된 것이다.

- [ ] **Step 7: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: `759 tests / 0 failures` — 태스크 3 은 테스트를 더하지 않고 이름과 단언만 바꾼다

- [ ] **Step 8: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeTest.kt
git commit -m "refactor : 주문의 재고 차감을 한 문장으로 보낸다"
```

---

### Task 4: 옛 단일 차감 API 제거

**파일:**
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- 수정: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt`

**인터페이스:**
- 사용: 없음
- 제공: `decreaseStock(productId, quantity)` 가 **사라진다.** 이후 차감 경로는 `decreaseStocks` 하나다.

**배경:** 두 경로를 남기면 누군가 단일 API 를 루프로 감싼다. **차감 경로가 하나뿐이어야
"락 구간 안 왕복 1 회" 가 구조로 보장된다.**

태스크 2 의 `DecreaseStocks` 7 건이 옛 `DecreaseStock` 5 건의 검증 범위를 모두 덮는다 —
넉넉/동일/부족/삭제/미존재. 그래서 옛 중첩 클래스를 **이관이 아니라 제거**한다.

- [ ] **Step 1: `ProductJpaRepository.decreaseStock` 을 지운다**

KDoc 블록과 `@Modifying` · `@Query` · 메서드 선언을 통째로 지운다.
`increaseLikeCount` · `decreaseLikeCount` 는 **그대로 둔다** — 좋아요 경로가 쓰고 있다.

- [ ] **Step 2: `ProductRepositoryImpl.decreaseStock` 을 지운다**

- [ ] **Step 3: `ProductRepository.decreaseStock` 선언을 지운다**

- [ ] **Step 4: `ProductService.decreaseStock` 을 지운다**

- [ ] **Step 5: 옛 통합 테스트를 지운다**

`ProductServiceIntegrationTest` 의 `DecreaseStock` 중첩 클래스(5 건)를 통째로 지운다.
`DecreaseStocks` 는 남긴다.

- [ ] **Step 6: 남은 참조가 없는지 확인한다**

```bash
grep -rn "decreaseStock\b" --include="*.kt" apps/
```

기대: **출력 없음.** (`decreaseStocks` 는 `\b` 경계에 걸리지 않는다.)
하나라도 남으면 컴파일이 깨지므로 반드시 0 이어야 한다.

- [ ] **Step 7: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: `754 tests / 0 failures`

- [ ] **Step 8: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
git commit -m "refactor : 단일 상품 재고 차감 API 를 제거한다"
```

---

### Task 5: 다중 항목 동시성 테스트

**파일:**
- 수정: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeConcurrencyTest.kt`

**인터페이스:**
- 사용: 기존 헬퍼 `signUp(loginId)` · `saveProduct(name, stock)` · `stockOf(productId)` ·
  `place(loginId, vararg items: Pair<Long, Int>)` · `runConcurrently(count, task): List<Throwable>`
- 제공: 없음

**배경:** 기존 동시성 테스트 3 건은 전부 **단일 항목** 주문이다. 배치 차감이 실제로 다투는 것은
다중 항목이므로, 그 형태를 재는 테스트가 없으면 이번 변경의 위험이 검증되지 않는다.

특히 **`CASE` 가 행마다 다른 수량을 쓰는지**를 동시 상황에서 확인해야 한다. 단일 스레드 테스트는
잘못된 `CASE` 도 통과시킬 수 있지만, 여러 스레드가 같은 행을 다투면 합계가 어긋나 드러난다.

- [ ] **Step 1: 실패할 수 있는 테스트를 쓴다**

기존 테스트들 뒤에 추가한다. 파일 상단 임포트에 `assertAll` 이 없으면 추가한다.

```kotlin
    /**
     * 두 상품을 함께 담은 주문이 동시에 몰릴 때, 두 상품의 차감 합계가 각각 정확해야 한다.
     * CASE 를 잘못 조립해 한 값으로 차감하면 여기서 합계가 어긋난다 —
     * 단일 스레드 테스트는 그 오류를 통과시킬 수 있다.
     */
    @DisplayName("두 상품을 함께 담은 주문이 동시에 몰려도, 상품마다 차감 합계가 정확하다.")
    @Test
    fun decreasesEachProductExactly_whenMultiItemOrdersRunConcurrently() {
        // arrange
        val threads = 20
        val first = saveProduct(name = "A", stock = 100)
        val second = saveProduct(name = "B", stock = 100)
        val users = (1..threads).map { signUp("multi%02d".format(it)) }

        // act
        // 항목마다 수량이 다르다. 같은 값으로 차감하는 구현이면 합계가 60 / 60 으로 어긋난다.
        val failures = runConcurrently(threads) { index ->
            place(users[index].loginId, first.id to 1, second.id to 2)
        }

        // assert
        assertAll(
            { assertThat(failures).describedAs("재고가 넉넉하므로 전부 성공해야 한다").isEmpty() },
            { assertThat(stockOf(first.id)).isEqualTo(100L - threads * 1) },
            { assertThat(stockOf(second.id)).isEqualTo(100L - threads * 2) },
        )
    }

    /**
     * 두 상품 중 하나만 재고가 빠듯할 때, 성사된 주문 수가 그 상품의 재고로 정확히 제한되고
     * 실패한 주문이 다른 상품의 재고를 갉아먹지 않아야 한다. 롤백이 실제로 작동하는지를 본다.
     */
    @DisplayName("한 상품만 재고가 모자라면, 그 재고만큼만 성사되고 다른 상품의 재고도 정확히 맞는다.")
    @Test
    fun limitsBySmallestStock_andRollsBackTheOther_whenMultiItemOrdersRunConcurrently() {
        // arrange
        val threads = 10
        val plenty = saveProduct(name = "넉넉", stock = 100)
        val scarce = saveProduct(name = "빠듯", stock = 4)
        val users = (1..threads).map { signUp("scarce%02d".format(it)) }

        // act
        val failures = runConcurrently(threads) { index ->
            place(users[index].loginId, plenty.id to 1, scarce.id to 1)
        }

        // assert
        val succeeded = threads - failures.size
        assertAll(
            { assertThat(succeeded).describedAs("빠듯한 상품의 재고만큼만 성사된다").isEqualTo(4) },
            { assertThat(stockOf(scarce.id)).isEqualTo(0L) },
            {
                // 실패한 6 건이 넉넉한 상품을 차감한 채 남으면 여기서 96 이 아닌 값이 나온다.
                // 부분 차감을 되돌리는 것은 호출자의 트랜잭션이라는 계약(2026-09-09 설계 문서 4.2 장)의 증거다.
                assertThat(stockOf(plenty.id)).describedAs("실패한 주문의 부분 차감이 남지 않아야 한다").isEqualTo(96L)
            },
        )
    }
```

- [ ] **Step 2: 실행한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeConcurrencyTest'
```

기대: 5 tests PASS (기존 3 + 신규 2)

**실패하면 그것이 이 태스크의 성과다.** 특히 두 번째 테스트에서 `plenty` 의 재고가 96 이 아니면
롤백이 부분 차감을 걷어내지 못하는 것이다 — 태스크 2 의 구현으로 돌아간다.

- [ ] **Step 3: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: `756 tests / 0 failures`

- [ ] **Step 4: 커밋**

```bash
git add apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeConcurrencyTest.kt
git commit -m "test : 다중 항목 주문의 동시성 테스트를 추가한다"
```

---

### Task 6: 부하 하네스 다중 항목 시나리오

**파일:**
- 수정: `loadtest/ab.js`
- 수정: `loadtest/README.md`

**인터페이스:**
- 사용: 없음 (Gradle 테스트와 무관하다)
- 제공: 환경변수 `ITEMS_PER_ORDER` (기본 `1`)

**배경:** 이 태스크가 없으면 **개선 효과를 잴 방법이 없다.** 항목 1 개 주문에서는 태스크 3 의 변경이
아무것도 바꾸지 않기 때문이다 (2026-09-09 설계 문서 8.1 장).

기본값이 `1` 이어야 한다. 그래야 2026-09-06 문서의 기존 결과와 같은 조건으로 비교할 수 있다.

**핫 상품이 반드시 포함돼야 한다.** 다중 항목이 되면 상품을 여럿 고르므로 그냥 무작위로 뽑으면
핫스팟과 분산의 경계가 흐려진다. 핫스팟 시나리오는 "핫 상품 1 개 + 무작위 N−1 개" 로 고정한다.

- [ ] **Step 1: 환경변수와 상품 선택 함수를 추가한다**

`ab.js` 의 환경변수 블록에서 `P95_THRESHOLD_MS` 선언 **아래**에 추가한다.

```javascript
// 주문 한 건에 담을 항목 수. 기본 1 은 2026-09-06 측정과 같은 조건이다 — 그래야 비교할 수 있다.
// 2 이상이면 재고 차감이 여러 행을 건드리므로, 배치 차감의 효과가 이 축에서 드러난다.
// (2026-09-09 설계 문서 8.1 장)
const ITEMS_PER_ORDER = Number(__ENV.ITEMS_PER_ORDER || 1);

if (ITEMS_PER_ORDER < 1 || ITEMS_PER_ORDER > PRODUCT_COUNT) {
    throw new Error('ITEMS_PER_ORDER 는 1 이상 ' + PRODUCT_COUNT + ' 이하여야 한다. 받은 값: ' + ITEMS_PER_ORDER);
}
```

**`PRODUCT_COUNT` 선언보다 뒤에 두어야 한다.** 위 검사가 그 값을 읽는다.

- [ ] **Step 2: 상품 선택을 함수로 뽑는다**

`placeOrder` 함수 **위**에 추가한다.

```javascript
// 핫스팟은 productId=1 을 반드시 포함한다. 다중 항목에서 무작위로만 뽑으면 핫 행이 빠진 주문이
// 섞여 "단일 행 직렬화" 를 재는 실험이 아니게 된다. (2026-09-09 설계 문서 8.1 장)
// 중복 productId 는 서버가 400 으로 막으므로(2026-08-24 설계 문서 6.7 장) 반드시 제거한다.
function pickProductIds() {
    const ids = SCENARIO_NAME === 'spread' ? [] : [1];

    while (ids.length < ITEMS_PER_ORDER) {
        const candidate = Math.floor(Math.random() * PRODUCT_COUNT) + 1;
        if (ids.indexOf(candidate) === -1) {
            ids.push(candidate);
        }
    }

    return ids;
}
```

- [ ] **Step 3: 페이로드를 바꾼다**

기존 `productId` 계산 줄과 `payload` 를 교체한다.

```javascript
    // couponId 는 보내지 않는다. user_coupons 는 1인 1매·1회용이라 두 번째 요청부터 409 가 나
    // 지속 부하를 걸 수 없다(2026-09-06 설계 문서 12.2 장). 쿠폰 경로는 이 스크립트의 측정 대상이 아니다.
    const payload = JSON.stringify({
        items: pickProductIds().map(function (id) {
            return { productId: id, quantity: 1 };
        }),
    });
```

- [ ] **Step 4: 요약에 항목 수를 찍는다**

`buildConsoleSummary` 의 제목 줄에 더한다. **어떤 조건의 측정인지 결과 파일만 보고 알 수 있어야 한다.**

```javascript
    lines.push(' 주문 처리량 A/B 부하 테스트 — ' + SCENARIO_NAME + ' / label=' + LABEL +
        ' / target=' + TARGET_TPS + ' TPS / items=' + ITEMS_PER_ORDER);
```

- [ ] **Step 5: 하위 호환을 확인한다**

앱을 띄우지 않고 스크립트만 검사한다.

```bash
k6 inspect loadtest/ab.js
```

기대: 오류 없이 시나리오 정의가 출력된다.

`ITEMS_PER_ORDER` 를 주지 않았을 때 핫스팟이 `[1]` 하나만 담는지 확인한다 —
그래야 2026-09-06 결과와 비교할 수 있다.

- [ ] **Step 6: README 를 갱신한다**

환경변수 표에 한 줄 추가한다.

```markdown
| `ITEMS_PER_ORDER` | `1` | 주문 한 건의 항목 수. 기본 1 은 2026-09-06 측정과 같은 조건이다. 2 이상이면 배치 차감의 효과를 재는 실험이 된다 (2026-09-09 설계 문서 8.1 장) |
```

"알려진 제약" 절에 한 줄 추가한다.

```markdown
- 다중 항목(`ITEMS_PER_ORDER` 2 이상) 결과를 항목 1 개 결과와 같은 표에 놓고 비교하지 않는다.
  서로 다른 실험이다 (2026-09-09 설계 문서 8.3 장).
```

- [ ] **Step 7: 커밋**

```bash
git add loadtest/ab.js loadtest/README.md
git commit -m "test : 부하 하네스에 다중 항목 주문 시나리오를 추가한다"
```

---

## 계획 밖

- **측정 실행과 그 결과의 문서 반영.** 하네스는 태스크 6 이 준비하지만, 실제 A/B 측정은 사람이
  Docker 를 띄우고 판단할 일이다. 2026-09-06 문서 3.5 장의 가드를 그대로 따른다.
- **설계 문서의 3.3 장 추정 표 갱신.** 실측이 나오면 추정을 실측으로 바꾼다.
  **그 전까지 추정을 실측처럼 쓰지 않는다.**
- **`binlog_group_commit_sync_delay` 검토.** 인프라 결정이며 2026-09-06 문서 12.6 장의 주제다.
- **주문 항목 수 상한.** `OrderCommand.Place` 는 항목 수를 제한하지 않아 `CASE` 절의 크기가
  요청에 비례한다 (2026-09-09 설계 문서 7.1 장). 상한을 둘지는 별도 판단이 필요하다.
  부하 하네스에는 태스크 6 이 검사를 넣지만, 그것은 스크립트 방어이지 API 계약이 아니다.
- **재고 행 분할.** 핫스팟 상한 자체를 올리는 유일한 방법이지만 과제 범위 밖이다.
