# 재고 차감 락 전략 비교 구현 계획 — 낙관적 락 · 비관적 락 · 조건부 UPDATE

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 재고 차감에 낙관적 락 · 비관적 락 · 조건부 `UPDATE` 세 전략을 **모두 구현**하고, 기동 시
환경변수 하나로 갈아 끼우는 스위치를 두어, 같은 하네스로 처리량을 실측 비교할 수 있는 상태를 만든다.
채택은 이 계획의 범위가 아니다 — 셋을 구현하고 측정하는 것까지가 이 계획이다.

**아키텍처:** `ProductRepository.decreaseStock(productId, quantity): Int` 계약은 그대로 둔다.
새 인터페이스 `StockDecreaseStrategy` 를 `domain/product` 에 두고, `infrastructure/product` 에 그
구현 셋(`ConditionalUpdateStockDecreaseStrategy` · `OptimisticLockStockDecreaseStrategy` ·
`PessimisticLockStockDecreaseStrategy`)을 둔다. `@ConditionalOnProperty` 로 `loopers.stock.lock-strategy`
값에 따라 기동 시 하나만 빈으로 올라가고, `ProductRepositoryImpl.decreaseStock` 이 그 빈에 위임한다.
`ProductRepository` 전체를 세 번 복제하지 않는 이유는 `saveAll` · `findAll` 등 무관한 메서드 아홉 개를
세 번 유지보수하게 되기 때문이다 — 이 지점은 설계 문서 6.1 장의 예시 코드보다 구체적인 이 계획의
결정이며, 계약(시그니처·반환값의 뜻)은 그대로 지킨다.

낙관적 락의 재시도는 `OrderFacade.place` 전체를 트랜잭션 경계 밖에서 다시 부르는 형태로 둔다.
`LikeFacade` 가 이미 같은 문제(경합 예외를 트랜잭션 경계 밖에서 흡수)를 `TransactionTemplate` 로 풀어
둔 전례가 있어, 그 관용구를 그대로 재사용한다 — `@Transactional` + 클래스 분리가 아니라
`TransactionTemplate.execute { ... }` 를 감싸는 얇은 재시도 루프다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Spring Data JPA / QueryDSL / MySQL 8.0 /
JUnit 5 · AssertJ · Mockito(mockito-kotlin) / Testcontainers / k6

**설계 문서:** `docs/superpowers/specs/2026-09-09-lock-strategy-throughput-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **응답·주석·커밋 메시지·문서는 한국어.** 변수명·함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백이 있다. (`feat : ...`, `test : ...`, `docs : ...`)
- **`modules/` 와 저장소 루트의 `supports/` 를 수정하지 않는다.** 특히 `modules/jpa` 의 `BaseEntity` 는
  세 앱이 공유하며 스스로 "이 외의 컬럼이나 동작은 추가하지 않는다" 고 선언했다. 낙관적 락의
  `@Version` 컬럼은 `ProductModel` 에 직접 둔다 (설계 문서 2 장 · 6.2 장). 이 계획은 그 외에
  `BaseEntity` 를 건드릴 일이 없다.
- **새 `ErrorType` 상수를 만들지 않는다.** `INTERNAL_ERROR` · `BAD_REQUEST` · `UNAUTHORIZED` ·
  `NOT_FOUND` · `CONFLICT` 다섯뿐이다.
- **`ktlintFormat` 을 실행하지 않는다.** 무관한 파일까지 건드린다. 검증은 `ktlintCheck` 로 한다.
- **ktlint 최대 줄 길이 130 자** (유니코드 문자 수 기준). `*Test.kt` 는 예외다.
- **블록 주석 안에 `/**` 를 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되어 `Unclosed comment` 로 컴파일이 깨진다.
- **Java 애노테이션의 배열 속성에는 배열 리터럴을 준다.** `@ConditionalOnProperty(name = ["..."])` —
  `name` 의 Java 시그니처가 `String[]` 이고, Kotlin 은 Java 호출과 달리 단일 문자열을 배열로
  암묵 변환하지 않는다. 저장소 선례는 `apps/commerce-batch` 의 `DemoJobConfig.kt` 다.
  (태스크 1 에서 실제로 컴파일이 깨진 자리이며, 태스크 2 · 3 의 코드에도 같은 형태가 있었다)
- **주석은 "무엇" 이 아니라 "왜" 를 적는다.** 설계 문서를 인용할 때는 **항상 날짜를 밝힌다.**
  이 계획이 새로 쓰는 인용은 전부 `(2026-09-09 설계 문서 N 장)` 형식이다. 다른 문서를 가리킬 때는
  `(2026-08-24 설계 문서 N 장)` 처럼 그 문서의 날짜를 쓴다. **날짜 없는 `(설계 문서 N 장)` 을 새로 쓰지 않는다.**
- **기존 주석의 날짜 없는 인용은 건드리지 않는다.** 예외는 이 계획이 KDoc 블록을 통째로 다시 쓰는
  자리뿐이다 — `ProductModel.change()` 의 KDoc(태스크 2)이 그 사례다.
- **`@Transactional` 을 동시성 테스트에 붙이지 않는다.** 붙이면 스레드가 각자의 트랜잭션을 갖지 못해
  경합이 일어나지 않고, 테스트가 초록인 채 아무것도 검증하지 않게 된다.
- **모든 Gradle 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** 이 디렉터리가 Gradle 루트다.
  상위 `study-project/` 에도 별도의 `gradlew` 가 있어 거기서 돌리면 프로젝트를 찾지 못한다.
  단 **Git 루트는 상위 `study-project/` 다** — `git status` 는 이 디렉터리 밖의 변경까지 보여주므로
  작업 트리를 판정할 때는 경로를 좁혀서 본다.
  - 전체: `./gradlew :apps:commerce-api:test`
  - 단일 클래스: `./gradlew :apps:commerce-api:test --tests 'com.loopers.<FQCN>'`
  - 린트: `./gradlew :apps:commerce-api:ktlintCheck`
- 통합·E2E·동시성 테스트는 Testcontainers 로 MySQL 8.0 을 띄운다. Docker 가 실행 중이어야 한다.
- **커밋 시 `git add -A` 를 쓰지 않는다.** 상위 저장소에 이 계획과 무관한 변경과 Firebase 서비스
  계정 **비공개 키**가 미추적으로 있다 (`fcm-project/src/main/resources/`). 경로를 명시해 스테이징한다.

---

## 기준선

작업 시작 전 상태다. 회귀 판정의 기준이 된다.

- 브랜치 `feature/order`, HEAD `6b99574`
- `./gradlew :apps:commerce-api:test` → **Task 1 Step 1 에서 한 번 실측해 확정한다.**
  이 문서의 모든 "기대 테스트 수 변화" 는 **그 실측 전까지 상대 증감으로만** 표기한다 — 절대값은
  넣지 않는다. 태스크 개요 표의 수치가 실측 기준선과 어긋나면 그 자리에서 전부 보정한다.
- **작업 트리는 깨끗하지 않다.** `loop-pack-be-l2-vol3-kotlin/` 안은 이 계획이 만든 파일 외에는
  비어 있지만, Git 루트인 상위 `study-project/` 에 이 계획과 무관한 변경이 남아 있다.
  - `gradlew` 파일 모드 변경(`100644` → `100755`) — 상위 저장소의 것이다. **되돌리지 않고 그대로 둔다.**
  - 여러 미추적 디렉터리 — `.serena/`, `chat-client/data/`, `chat-study/docker/data/`,
    `fastcampus-coupon-core/src/main/generated/` 등 전부 생성물이다.
  - `docs/superpowers/plans/2026-09-01-coupon-admin.md` · `specs/2026-09-06-order-throughput-design.md` —
    다른 작업이 수정 중인 문서다. 이 계획은 건드리지 않는다.
  - `docs/superpowers/plans/2026-09-11-batch-stock-decrease.md` · 그 설계 문서 —
    **이 비교가 끝나 전략이 정해지기 전까지 보류 상태**다 (설계 문서 갱신 이력 참고). 이 계획은
    그쪽을 건드리지 않는다.
  - ⚠️ `fcm-project/src/main/resources/` 에 Firebase 서비스 계정 **비공개 키**가 미추적으로 있다.
    `.gitignore` 가 막지 않으므로 **`git add -A` 를 쓰면 그대로 올라간다.**

  이것들은 자기 변경이 아니므로 **커밋할 때 경로를 명시해 스테이징한다.**

각 태스크 종료 시 테스트 수는 늘어나되(또는 유지되되) **실패는 0** 이어야 한다.

---

## 파일 구조

### 신규

| 경로 | 책임 |
|---|---|
| `domain/product/StockDecreaseStrategy.kt` | 재고 차감 전략의 추상 계약. `ProductRepository.decreaseStock` 과 같은 시그니처 |
| `infrastructure/product/ConditionalUpdateStockDecreaseStrategy.kt` | 현재 프로덕션 구현을 전략으로 옮긴 것 |
| `infrastructure/product/OptimisticLockStockDecreaseStrategy.kt` | `@Version` 기반. `SELECT` → 앱 검사 → 엔티티 차감 → flush |
| `infrastructure/product/PessimisticLockStockDecreaseStrategy.kt` | `SELECT ... FOR UPDATE` → 앱 검사 → `UPDATE` |
| `infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt` | 기본값이 조건부 UPDATE 인지 확인 |
| `infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt` | 속성이 낙관적 락을 올리는지 확인 |
| `infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt` | 속성이 비관적 락을 올리는지 확인 |
| `domain/product/AbstractStockDecreaseContractTest.kt` | 세 전략 공통 계약(설계 문서 6.4 장) |
| `domain/product/ConditionalUpdateStockDecreaseContractTest.kt` | 위 추상 클래스의 조건부 UPDATE 실행 |
| `domain/product/OptimisticLockStockDecreaseContractTest.kt` | 위 추상 클래스의 낙관적 락 실행 |
| `domain/product/PessimisticLockStockDecreaseContractTest.kt` | 위 추상 클래스의 비관적 락 실행 |
| `application/order/AbstractOrderFacadeConcurrencyTest.kt` | 기존 `OrderFacadeConcurrencyTest.kt` 를 이름 변경 + `abstract` 화한 것 |
| `application/order/ConditionalUpdateOrderFacadeConcurrencyTest.kt` | 위 추상 클래스의 조건부 UPDATE 실행 |
| `application/order/OptimisticLockOrderFacadeConcurrencyTest.kt` | 위 추상 클래스의 낙관적 락 실행 |
| `application/order/PessimisticLockOrderFacadeConcurrencyTest.kt` | 위 추상 클래스의 비관적 락 실행 |

### 수정

| 경로 | 무엇을 |
|---|---|
| `domain/product/ProductRepository.kt` | `decreaseStock` KDoc 에 전략 위임 설명 추가 (시그니처 불변) |
| `domain/product/ProductModel.kt` | `@Version` 필드, `decreaseStockForOptimisticLock()`, `change()` KDoc 정정 |
| `infrastructure/product/ProductRepositoryImpl.kt` | `decreaseStock` 이 `StockDecreaseStrategy` 에 위임 |
| `infrastructure/product/ProductJpaRepository.kt` | `findByIdForUpdate` 추가 |
| `application/order/OrderFacade.kt` | `place()` 를 `TransactionTemplate` + 재시도 래퍼로, 본문은 `placeInTransaction()` 으로 이동 |
| `application/order/OrderFacadeTest.kt` | `transactionTemplate` 목 배선, 재시도 단위 테스트 2 건 |
| `apps/commerce-api/src/main/resources/application.yml` | `loopers.stock.lock-strategy` 속성 추가 (local·test 프로필) |
| `loadtest/ab.js` | `ITEMS_PER_ORDER` 다중 항목 시나리오 |
| `loadtest/README.md` | 새 환경변수, `LABEL` 컨벤션, 9 칸 측정 절차 |
| `docs/superpowers/specs/2026-09-09-lock-strategy-throughput-design.md` | 3 장 가설표 → 실측표 (태스크 6) |

`OrderFacadeConcurrencyTest.kt` 는 "삭제 후 재생성" 이 아니라 `git mv` 로 이름을 바꾼다 — 히스토리를
보존하기 위해서다.

---

## 태스크 개요

| # | 태스크 | 산출물 | 기대 테스트 수 변화 (기준선 실측 전까지 상대값) |
|---|---|---|---|
| 1 | 락 전략 전환 스위치 | `StockDecreaseStrategy` + 조건부 UPDATE 를 전략으로 이관 | 기준선 + 1 |
| 2 | 낙관적 락 | `@Version`, 재시도 3 회, 트랜잭션 경계 밖 | 이전 + 3 |
| 3 | 비관적 락 | `SELECT ... FOR UPDATE`, 항목마다 왕복 2 | 이전 + 1 |
| 4 | 세 전략 공통 계약 테스트 | 계약 테스트 + 기존 동시성 테스트를 세 전략에서 실행 | 이전 + 약 23 |
| 5 | 부하 하네스 다중 항목 시나리오 | `ITEMS_PER_ORDER`, `LABEL` 컨벤션 문서화 | 이전 (변화 없음 — k6 는 Gradle 테스트가 아니다) |
| 6 | 측정 실행과 문서 반영 | 9 칸 실측, 설계 문서 3 장 갱신 | 이전 (변화 없음 — 코드 변경 없음) |

**태스크 2 가 이 계획에서 가장 위험한 지점이다.** 재시도를 트랜잭션 경계 밖에 두려고
`OrderFacade.place()` 의 트랜잭션 관리 방식 자체를 바꾼다(`@Transactional` → `TransactionTemplate`).
이 클래스를 직접 생성하는 유일한 테스트인 `OrderFacadeTest.kt` 가 그 변경의 영향을 받는다 —
`transactionTemplate` 목이 실제로 콜백을 실행하도록 배선하지 않으면 기존 테스트가 전부
`null` 반환으로 깨진다 (태스크 2 Step 6).

**태스크 4 는 새 기능이 없다.** 세 전략이 이미 같은 결과를 낸다는 것을 확인하는 태스크이므로,
실패하면 그 자체가 태스크 2 · 3 의 구현이 어딘가 잘못됐다는 신호다.

---

### Task 1: 락 전략 전환 스위치

**파일:**
- 생성: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecreaseStrategy.kt`
- 생성: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockDecreaseStrategy.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt` (KDoc)
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- 수정: `apps/commerce-api/src/main/resources/application.yml`

**인터페이스:**
- 사용: 없음 (첫 태스크)
- 제공:
  - `com.loopers.domain.product.StockDecreaseStrategy.decreaseStock(productId: Long, quantity: Int): Int`
  - 스프링 속성 `loopers.stock.lock-strategy` (`conditional-update` | `optimistic` | `pessimistic`, 기본 `conditional-update`)
  - 태스크 2 · 3 이 각각 `OptimisticLockStockDecreaseStrategy` · `PessimisticLockStockDecreaseStrategy` 를
    같은 인터페이스로 추가한다. `ProductRepositoryImpl` 은 이 태스크 이후 다시 바뀌지 않는다.

**배경:** `ProductRepository` 전체를 세 번 구현하지 않는다. 인터페이스에는 `saveAll` · `save` ·
`findById` 등 재고 차감과 무관한 메서드가 아홉 개 더 있고, 이것을 세 벌 만들면 그중 여덟은
영원히 서로 동기화해야 하는 사본이 된다. 대신 재고 차감의 **행동만** `StockDecreaseStrategy` 로
뽑아 `ProductRepositoryImpl` 이 위임하게 한다. `ProductRepository.decreaseStock` 의 시그니처와 반환값의
뜻은 그대로다 — 호출부(`ProductService` → `OrderFacade`)가 한 글자도 바뀌지 않는다는 비교의 전제가
이렇게 지켜진다. (2026-09-09 설계 문서 6.1 장 — 구현 위치는 이 계획의 결정이며 계약은 설계 문서를 따른다)

`@ConditionalOnProperty` 를 쓰는 이유는 핫패스에 분기가 없어야 하기 때문이다. 빈 선택은 스프링
기동 시점에 끝나고, 요청마다 `if (strategy == ...)` 를 실행하는 것과 다르다. (설계 문서 6.1 장)

이 태스크에는 새 동작이 없다 — 조건부 UPDATE 구현을 옮기기만 한다. 그래서 기존 스위트가
1 차 회귀 방지선이고, 새로 추가하는 테스트 한 건은 "스위치가 실제로 무언가를 스위치하는가" 를 본다.

- [ ] **Step 1: 기준선을 실측한다**

```bash
cd /Users/choeseongang/IdeaProjects/study-project/loop-pack-be-l2-vol3-kotlin
git log -1 --format=%h   # 기대: 6b99574
./gradlew :apps:commerce-api:test
```

결과를 기록한다. **이 문서의 모든 "이전 + N" 표기는 이 숫자를 기준으로 계산한다.**
숫자가 이 문서 작성 시점과 다르면 태스크 개요 표와 각 태스크 종료 시의 기대 테스트 수를
그 차이만큼 전부 보정한다.

- [ ] **Step 2: `StockDecreaseStrategy` 인터페이스를 만든다**

```kotlin
package com.loopers.domain.product

/**
 * 재고 차감 락 전략의 추상. ProductRepository.decreaseStock 계약을 그대로 옮겨 담는다 —
 * 전략을 갈아 끼워도 OrderFacade → ProductService → ProductRepository 호출부가
 * 한 글자도 바뀌지 않는 것이 이 비교의 조건이다. (2026-09-09 설계 문서 6.1 장)
 *
 * 기동 시 loopers.stock.lock-strategy 값에 따라 구현 셋(조건부 UPDATE · 낙관적 락 · 비관적 락)
 * 중 하나만 빈으로 올라간다. ProductRepositoryImpl 이 이 인터페이스에 위임하는 것이 스위치의 실체다.
 */
interface StockDecreaseStrategy {
    fun decreaseStock(productId: Long, quantity: Int): Int
}
```

- [ ] **Step 3: 기존 구현을 `ConditionalUpdateStockDecreaseStrategy` 로 옮긴다**

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * 조건부 UPDATE 재고 차감 전략 — 현재 프로덕션 구현을 그대로 옮긴 것이다. (2026-09-09 설계 문서 6.1 장)
 *
 * matchIfMissing = true 인 이유는 두 가지다. 첫째, 이 속성은 local·test 프로필에만 준다(기준선 참고) —
 * dev·qa·prd 는 이 값을 아예 모르지만 그래도 기동은 돼야 한다. 둘째, 이 스위치 자체가 측정이 끝나면
 * 걷어질 것이므로(설계 문서 6.5 장) 그때도 조건부 UPDATE 가 기본으로 남는 쪽이 안전하다 — 셋 중
 * 이것이 지금의 실제 프로덕션 구현이기 때문이다.
 */
@Repository
@ConditionalOnProperty(
    name = ["loopers.stock.lock-strategy"],
    havingValue = "conditional-update",
    matchIfMissing = true,
)
class ConditionalUpdateStockDecreaseStrategy(
    private val productJpaRepository: ProductJpaRepository,
) : StockDecreaseStrategy {
    private val log = LoggerFactory.getLogger(ConditionalUpdateStockDecreaseStrategy::class.java)

    init {
        // 환경변수를 안 바꾸고 두 번 재는 사고가 이 비교에서 가장 흔한 실수다 (설계 문서 6.1 장).
        // 이 로그가 그 사고를 잡는 유일한 장치이므로 기동마다 반드시 찍는다.
        log.info("재고 차감 전략 선택 : conditional-update")
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        return productJpaRepository.decreaseStock(productId = productId, quantity = quantity)
    }
}
```

`@Repository` 를 쓰는 이유(단순 `@Component` 가 아니라)는 태스크 2 에서 낙관적 락 구현에 필요한
스프링 예외 번역이 이 계층 전체에 일관되게 걸리도록 하기 위해서다 — 세 구현을 같은 스테레오타입으로
둔다.

- [ ] **Step 4: `ProductRepositoryImpl` 이 전략에 위임하도록 바꾼다**

```kotlin
@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryDslRepository: ProductQueryDslRepository,
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) : ProductRepository {
```

`decreaseStock` 구현을 바꾼다.

```kotlin
    override fun decreaseStock(productId: Long, quantity: Int): Int {
        // 실제 전략(조건부 UPDATE·낙관적 락·비관적 락)은 기동 시 빈으로 결정된다. (2026-09-09 설계 문서 6.1 장)
        return stockDecreaseStrategy.decreaseStock(productId = productId, quantity = quantity)
    }
```

`import com.loopers.domain.product.StockDecreaseStrategy` 를 추가한다.

- [ ] **Step 5: `ProductRepository.decreaseStock` 의 KDoc 을 갱신한다**

시그니처는 그대로 두고 마지막 문단만 더한다.

```kotlin
    /**
     * 재고를 요청 수량만큼 줄인다. 재고가 모자라면 아무것도 바꾸지 않는다. 반환값은 영향 행 수다.
     *
     * 확인과 차감이 한 문장 안에서 원자적으로 일어나는 것이 이 계약의 핵심이다. (설계 문서 6.3 장)
     * 읽어서 뺀 값을 쓰면 동시 주문 두 건이 같은 재고를 읽고 같은 값을 써서 초과 판매가 된다.
     *
     * 실제 구현은 StockDecreaseStrategy 로 위임된다. 기동 시 loopers.stock.lock-strategy 에 따라
     * 조건부 UPDATE · 낙관적 락 · 비관적 락 중 하나가 선택되지만, 이 계약과 반환값의 뜻은 셋 모두 같다.
     * (2026-09-09 설계 문서 6.1 · 6.4 장)
     */
    fun decreaseStock(productId: Long, quantity: Int): Int
```

- [ ] **Step 6: `application.yml` 에 속성을 추가한다**

`local, test` 프로필 블록(`stub-credentials` 가 있는 자리)에 더한다.

```yaml
---
spring:
  config:
    activate:
      on-profile: local, test

loopers:
  admin:
    stub-credentials:
      - id: admin
        password: admin1234
  stock:
    lock-strategy: conditional-update
```

dev·qa·prd 블록은 건드리지 않는다 — `matchIfMissing = true` 가 그 프로필들의 기동을 보장한다.

- [ ] **Step 7: 스위치를 확인하는 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt`

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * loopers.stock.lock-strategy 가 실제로 대응하는 빈 하나만 올리는지 확인한다.
 * 기동 시 스위치가 문서(2026-09-09 설계 문서 6.1 장)의 약속대로 동작하는지의 유일한 자동 검증이다.
 * 태스크 2 · 3 이 낙관적 락 · 비관적 락 버전을 나란히 추가한다.
 */
@SpringBootTest
class ConditionalUpdateStockLockStrategySwitchTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) {
    @DisplayName("설정을 지정하지 않으면(기본값), 조건부 UPDATE 전략이 올라온다.")
    @Test
    fun loadsConditionalUpdateStrategy_byDefault() {
        assertThat(stockDecreaseStrategy).isInstanceOf(ConditionalUpdateStockDecreaseStrategy::class.java)
    }
}
```

- [ ] **Step 8: 통과와 회귀를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.product.ConditionalUpdateStockLockStrategySwitchTest'
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: 새 테스트 PASS. 전체 스위트는 **기준선 + 1**, 0 failures.
동작이 바뀐 것이 없으므로 기존 테스트의 통과/실패 여부가 하나라도 바뀌면 위임 과정에서 뭔가
달라진 것이다 — Step 4 의 delegate 호출 인자 순서를 확인한다.

- [ ] **Step 9: 기동 로그를 눈으로 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.product.ConditionalUpdateStockLockStrategySwitchTest' --info \
  | grep "재고 차감 전략 선택"
```

기대: `재고 차감 전략 선택 : conditional-update` 한 줄. 이 로그가 이후 9 칸 측정에서 "환경변수를
안 바꾸고 두 번 쟀다" 는 사고를 잡는 유일한 장치이므로, 이 시점에 반드시 눈으로 본다.

- [ ] **Step 10: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDecreaseStrategy.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockDecreaseStrategy.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt \
        apps/commerce-api/src/main/resources/application.yml \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/ConditionalUpdateStockLockStrategySwitchTest.kt
git commit -m "refactor : 재고 차감을 전략 인터페이스 뒤로 옮기고 전환 스위치를 추가한다"
```

---

### Task 2: 낙관적 락

**파일:**
- 생성: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/OptimisticLockStockDecreaseStrategy.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt`
- 수정: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeTest.kt`

**인터페이스:**
- 사용: `StockDecreaseStrategy` (태스크 1), `ProductJpaRepository.findByIdAndDeletedAtIsNull` (기존)
- 제공:
  - `ProductModel.version: Long` (`@Version`)
  - `ProductModel.decreaseStockForOptimisticLock(quantity: Int)`
  - `OrderFacade.place(command)` — 시그니처 불변, 내부에서 최대 3 회 재시도

**배경 — 왜 `@Version` 이 `BaseEntity` 가 아니라 `ProductModel` 에 있는가:**
`modules/jpa` 는 세 앱이 공유하고 `BaseEntity` 는 스스로 컬럼을 더 추가하지 않는다고 선언했다.
이 컬럼은 측정용이고 채택되지 않으면 사라진다 — 공유 기반을 건드릴 이유가 없다. (설계 문서 6.2 장)

**배경 — 왜 재시도가 `OrderFacade.place` 전체를 다시 부르는 형태인가:**
재시도를 `@Transactional` **안**에 두면 버전 충돌로 실패한 그 트랜잭션이 롤백되지 않은 채로 다시
시도하게 된다. 재시도는 트랜잭션 경계 **밖**이어야 한다. (설계 문서 6.2 장) 이 코드베이스에는 이미
같은 문제를 푼 전례가 있다 — `LikeFacade` 가 "경합 예외를 트랜잭션 경계 밖에서 흡수" 하려고
`TransactionTemplate` 을 쓴다(`LikeFacade` KDoc). `place()` 를 얇은 재시도 루프로 두고, 기존
`place()` 본문 전체를 `placeInTransaction()` 이라는 private 메서드로 옮겨 `transactionTemplate.execute { }`
로 감싼다. `@Transactional` + 클래스 분리를 쓰지 않는 이유도 `LikeFacade` KDoc 이 이미 적어 뒀다 —
"클래스를 '얇은 래퍼 + `@Transactional` 컴포넌트' 로 쪼개면 왜 나뉘어 있는지가 어디에도 남지 않아,
나중에 누군가 합치는 순간 이 예외 흡수가 조용히 동작을 멈춘다."

**배경 — 왜 엔티티를 거쳐 차감하는가 (그리고 `updated_at` 이 예외적으로 갱신되는 이유):**
조건부 UPDATE·비관적 락은 raw `UPDATE` 문이라 엔티티 생명주기 콜백을 타지 않는다. 낙관적 락은
정반대다 — `@Version` 검사가 **엔티티 flush** 시점에 걸리므로, 검사를 받으려면 엔티티를 읽고
필드를 바꾸고 flush 해야 한다. 그러면 `BaseEntity.preUpdate()` 가 함께 돌아 `updated_at` 이 갱신된다.
이것은 구현 실수가 아니라 이 기법 고유의 성질이다 — **세 전략이 `updated_at` 에서만큼은 완전히
같은 계약을 갖지 못한다.** 이 계획은 그 사실을 태스크 4 의 계약 테스트에서 명시적으로 갈라 다룬다.

**수동 JPQL 로 `version` 을 직접 비교·증가시키면 이것도 피할 수 있다** — 설계 문서 3.1 장의 절차
(`앱에서 재고 검사` → `UPDATE ... WHERE id=? AND version=?`)가 그 형태다. 그럼에도 엔티티 경로를
쓰는 이유는 **재는 대상이 "JPA 가 제공하는 낙관적 락" 이어야 하고**, `updated_at` 한 컬럼은 같은
`UPDATE` 문장에 붙어 왕복도 인덱스 갱신도 늘리지 않아 **처리량에 영향이 없기** 때문이다.
(2026-09-09 설계 문서 6.4 장 — 그 장이 이 예외를 명시한다)

`Stock.decrease()` 를 두지 않는다는 CLAUDE.md 규칙과 겉보기로 모순되는 지점도 여기다.
`ProductModel.decreaseStockForOptimisticLock()` 은 "읽고 → 빼고 → 쓰기" 형태이지만, 실제 방어는 이
메서드가 아니라 flush 시점의 `@Version` 검사다 — 그 검사가 실패하면 이 메서드가 만든 변경은
롤백된다. 이것이 낙관적 락이라는 기법 자체의 정의이며, 이 계획이 CLAUDE.md 규칙을 어긴 것이
아니라 **그 규칙의 전제(조건부 UPDATE 가 유일한 전략)가 이 실험에서는 성립하지 않는다.**

**배경 — 정확성 검증의 범위:** 재고 부족·소프트 삭제·존재하지 않는 상품에 대한 동작은 태스크 4 의
공통 계약 테스트가 세 전략을 한 번에 검증한다. 이 태스크는 재시도 로직 자체만 본다 — 같은 단언을
두 태스크에서 반복하지 않기 위해서다.

- [ ] **Step 1: 실패하는 테스트를 쓴다 — 재시도 상한 초과**

`OrderFacadeTest.kt` 에 추가하기 전에, **이 파일 전체가 `TransactionTemplate` 의존성 없이는
컴파일되지 않게 된다.** 배선을 먼저 하지 않으면 이 단계의 "실패" 가 의도한 이유(재시도 로직 없음)가
아니라 컴파일 오류가 되어 TDD 사이클이 흐려진다. **Step 1 과 Step 2(배선)를 아래 순서로 함께 본다.**

`OrderFacadeTest.kt` 의 필드 선언부를 바꾼다.

```kotlin
import org.mockito.kotlin.doAnswer
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
```

```kotlin
    private val userService = mock<UserService>()
    private val productService = mock<ProductService>()
    private val orderService = mock<OrderService>()
    private val couponService = mock<CouponService>()
    private val transactionTemplate = mock<TransactionTemplate>()
    private val orderFacade = OrderFacade(userService, productService, orderService, couponService, transactionTemplate)

    init {
        // OrderFacade.place() 가 transactionTemplate.execute { ... } 로 감싸는 것을 목에서도
        // 실제로 콜백을 실행하도록 흉내 낸다. 그렇지 않으면 Mockito 기본값(null) 이 반환되어
        // 이 파일의 기존 테스트가 전부 깨진다.
        //
        // execute<Any> 로 타입 파라미터를 고정해도 스텁은 모든 execute(...) 호출에 걸린다 — JVM 은
        // 제네릭을 지워 이 메서드를 하나의 시그니처로만 본다.
        whenever(transactionTemplate.execute<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<*>>(0)
            callback.doInTransaction(mock())
        }
    }
```

`OrderFacade(userService, productService, orderService, couponService)` 를 쓰는 곳은 이 한 줄뿐이다
(`grep -n "OrderFacade(" apps/commerce-api/src` 로 확인한다).

이제 재시도 상한 테스트를 클래스 끝(또는 `Place` 중첩 클래스 안)에 더한다. 기존 픽스처 헬퍼
`user()` · `product()` · `order()` · `orderItem()` 을 그대로 쓴다.

```kotlin
    @DisplayName("낙관적 락 충돌이 재시도 상한을 넘기면, ")
    @Nested
    inner class OptimisticLockRetry {
        @DisplayName("CONFLICT 이고 재고 차감은 정확히 3 번만 시도된다.")
        @Test
        fun throwsConflict_afterExhaustingRetries() {
            // arrange
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(orderService.place(any(), any(), any(), anyOrNull()))
                .thenReturn(order(items = listOf(orderItem(1L))))
            whenever(productService.decreaseStock(any(), any()))
                .thenThrow(ObjectOptimisticLockingFailureException(ProductModel::class.java, 1L))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act
            val result = assertThrows<CoreException> { orderFacade.place(command) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { verify(productService, times(3)).decreaseStock(any(), any()) },
            )
        }

        @DisplayName("상한 이내에 해소되면, 예외 없이 정상 처리되고 그만큼만 시도된다.")
        @Test
        fun succeeds_whenConflictResolvesWithinRetryLimit() {
            // arrange
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(orderService.place(any(), any(), any(), anyOrNull()))
                .thenReturn(order(items = listOf(orderItem(1L))))
            // 처음 두 번은 버전 충돌, 세 번째에 성공한다.
            whenever(productService.decreaseStock(any(), any()))
                .thenThrow(ObjectOptimisticLockingFailureException(ProductModel::class.java, 1L))
                .thenThrow(ObjectOptimisticLockingFailureException(ProductModel::class.java, 1L))
                .thenReturn(true)

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act & assert — 예외 없이 끝나야 한다
            orderFacade.place(command)
            verify(productService, times(3)).decreaseStock(any(), any())
        }
    }
```

`org.mockito.kotlin.times` 임포트를 추가한다(없다면).

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeTest'
```

기대: 컴파일 실패 — `OrderFacade` 생성자가 인자 5 개를 받지 않는다. 이 실패가 이 태스크의
출발점이다. (아직 `OrderFacade` 를 고치지 않았으므로 정상이다.)

- [ ] **Step 3: `ProductModel` 에 `@Version` 과 측정용 차감 메서드를 더한다**

```kotlin
import jakarta.persistence.Version
```

`stock` 필드 바로 아래에 더한다.

```kotlin
    /**
     * 낙관적 락 측정 전용 컬럼. 세 전략 비교가 끝나면 컬럼째 사라진다. (2026-09-09 설계 문서 6.5 장)
     *
     * BaseEntity 가 아니라 여기 두는 이유는 modules/jpa 가 세 앱이 공유하고, 그 클래스가 스스로
     * "이 외의 컬럼이나 동작은 추가하지 않는다" 고 선언했기 때문이다. (2026-09-09 설계 문서 2 장)
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set
```

`change()` 의 KDoc 마지막 문단을 정정한다 — 낙관적 락이 추가되며 사실과 어긋나게 됐다.

```kotlin
     * stock 이 매개변수에 들어온 것은 재고가 상품의 속성이기 때문이다. (설계 문서 5.6 장)
     * PUT 은 전체 교체이므로 재고도 교체 대상이다.
     * 주문에 의한 차감은 이 경로를 타지 않는다 — 조건부 UPDATE·비관적 락은 엔티티를 거치지 않고,
     * 낙관적 락(측정용)은 decreaseStockForOptimisticLock 이라는 별도 경로를 쓴다. 이 change() 는
     * PUT 수정 전용이며 세 전략 중 어느 것도 거치지 않는다. (2026-09-09 설계 문서 6.2 장)
```

`companion object` 위, `change()` 아래에 측정용 메서드를 더한다.

```kotlin
    /**
     * 낙관적 락 측정 전용 차감. Stock 에 decrease() 를 두지 않는 이유(설계 문서 5.4 장)와 겉보기로
     * 모순되지만, 실제 방어는 이 메서드가 아니라 @Version 이 flush 시점에 거는 검사다. 여기서 stock
     * 을 줄인 뒤 버전이 어긋나면 이 UPDATE 는 0 행이 되고 OptimisticLockException 이 던져져
     * 롤백되므로, "읽고 → 빼고 → 쓰기" 가 초과 판매로 이어지지 않는다.
     *
     * 호출자(OptimisticLockStockDecreaseStrategy)가 stock.value >= quantity 를 미리 확인했다는
     * 전제 위에 있다 — 이 메서드 자신은 그 확인을 반복하지 않는다.
     *
     * 측정용이며 채택되지 않으면 @Version 컬럼과 함께 사라진다. (2026-09-09 설계 문서 6.5 장)
     */
    fun decreaseStockForOptimisticLock(quantity: Int) {
        this.stock = Stock(this.stock.value - quantity)
    }
```

- [ ] **Step 4: `OptimisticLockStockDecreaseStrategy` 를 만든다**

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * 낙관적 락(@Version) 재고 차감 전략. (2026-09-09 설계 문서 6.2 장)
 *
 * SELECT 는 락을 잡지 않는다. 재고 검사도 락 없이 앱에서 한다. 실제 방어는 stock 을 줄인 뒤
 * 강제 flush 에서 @Version 이 발동시키는 `UPDATE ... WHERE id=? AND version=?` 뿐이다 — 그 문장이
 * 0 행이면 Hibernate 가 OptimisticLockException 을 던진다.
 *
 * @Repository 를 붙이는 이유는 스프링의 PersistenceExceptionTranslationPostProcessor 가 이
 * 스테레오타입이 붙은 빈만 감싸기 때문이다. 그 감쌈이 있어야 이 메서드가 던지는
 * jakarta.persistence.OptimisticLockException 이 OrderFacade 가 잡는 스프링 표준 예외인
 * ObjectOptimisticLockingFailureException 으로 번역된다. 이 번역이 없으면 재시도 루프가 이 예외를
 * 알아보지 못하고 그대로 흘려보낸다 — 태스크 2 Step 6 에서 실제로 번역되는지 통합 테스트로 확인한다.
 *
 * 재시도는 여기 없다. "트랜잭션 경계 밖에서 OrderFacade.place 전체를 다시 부르는 형태" 로 두었으므로
 * (설계 문서 6.2 장) 이 클래스는 실패를 예외로 알리기만 하고 재시도는 모른다.
 */
@Repository
@ConditionalOnProperty(name = ["loopers.stock.lock-strategy"], havingValue = "optimistic")
class OptimisticLockStockDecreaseStrategy(
    private val productJpaRepository: ProductJpaRepository,
    private val entityManager: EntityManager,
) : StockDecreaseStrategy {
    private val log = LoggerFactory.getLogger(OptimisticLockStockDecreaseStrategy::class.java)

    init {
        log.info("재고 차감 전략 선택 : optimistic (재시도 상한 3, 백오프 없음)")
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        val product = productJpaRepository.findByIdAndDeletedAtIsNull(productId) ?: return 0
        if (product.stock.value < quantity) return 0

        product.decreaseStockForOptimisticLock(quantity)

        // 항목마다 즉시 flush 해 버전 충돌을 그 자리에서 드러낸다. 커밋까지 미루면 다중 항목 중
        // 몇 번째에서 충돌했는지 알 수 없고, 이 메서드의 반환값이 실제 결과와 어긋난다.
        // (여기서 예외가 나면 아래 return 문에 도달하지 않는다 — 그것이 실패를 알리는 방식이다)
        entityManager.flush()

        return 1
    }
}
```

- [ ] **Step 5: `OrderFacade` 를 재시도 래퍼로 바꾼다**

생성자에 의존을 추가한다.

```kotlin
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate

@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService,
    private val couponService: CouponService,
    private val transactionTemplate: TransactionTemplate,
) {
```

기존 `@Transactional fun place(command: OrderCommand.Place): OrderInfo { ... }` 를 **본문은 그대로
두고 이름과 애노테이션만** 바꿔 `placeInTransaction` 으로 옮긴다. `@Transactional` 은 제거한다 —
경계는 이제 `transactionTemplate` 이 관리한다.

```kotlin
    fun place(command: OrderCommand.Place): OrderInfo {
        repeat(MAX_OPTIMISTIC_LOCK_ATTEMPTS) { attempt ->
            try {
                return transactionTemplate.execute { placeInTransaction(command) }!!
            } catch (e: ObjectOptimisticLockingFailureException) {
                // 백오프 없음 — 넣으면 지연이 경합의 산물인지 정책의 산물인지 구분할 수 없다.
                // (2026-09-09 설계 문서 6.2 장) 다른 두 전략에서는 이 예외가 나지 않으므로
                // 이 catch 는 그 전략들에서 한 번도 실행되지 않는다 — 핫패스에 분기를 더하지 않는다.
                if (attempt == MAX_OPTIMISTIC_LOCK_ATTEMPTS - 1) {
                    throw CoreException(
                        errorType = ErrorType.CONFLICT,
                        customMessage = "[productIds = ${command.items.map { it.productId }}] " +
                            "동시 갱신 충돌로 재고 차감에 실패했습니다 " +
                            "(재시도 $MAX_OPTIMISTIC_LOCK_ATTEMPTS 회 초과).",
                    )
                }
            }
        }
        error("도달할 수 없다 — 위 루프가 성공 시 반환하거나 재시도 초과 시 예외를 던진다")
    }

    /**
     * 실제 주문 처리. place() 와 분리된 이유는 재시도가 트랜잭션 경계 밖에 있어야 하기 때문이다
     * (2026-09-09 설계 문서 6.2 장) — 재시도를 @Transactional 안에 두면 실패한 트랜잭션이 롤백되지
     * 않은 채로 다시 시도하게 된다. LikeFacade 가 같은 문제를 TransactionTemplate 으로 푼 전례를
     * 그대로 따른다 — "얇은 래퍼 + @Transactional 컴포넌트" 로 클래스를 쪼개지 않는 이유는
     * LikeFacade KDoc 참고.
     */
    private fun placeInTransaction(command: OrderCommand.Place): OrderInfo {
        val user = getUserOrThrow(command.loginId)
        // ... 기존 place() 본문을 한 글자도 바꾸지 않고 그대로 옮긴다 ...
    }

    private companion object {
        /** 낙관적 락 외 전략에서는 영향을 주지 않는다 — 그 전략들은 첫 시도에서 항상 끝난다. */
        const val MAX_OPTIMISTIC_LOCK_ATTEMPTS = 3
    }
```

**주의:** `placeInTransaction` 옮기는 과정에서 `OrderInfo.of(order)` 위의 ⚠️ 순서 경고 주석과
`sorted` 정렬 주석은 **내용 변경 없이** 그대로 따라간다 — 이 계획은 그 순서(쿠폰 → 저장 →
`OrderInfo.of` → 차감)를 건드리지 않는다.

- [ ] **Step 6: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeTest'
```

기대: 새 `OptimisticLockRetry` 2 건을 포함해 전부 PASS. 기존 테스트가 하나라도 실패하면 Step 1 의
`transactionTemplate` 스텁 배선을 먼저 의심한다 — 콜백이 실행되지 않으면 `place()` 가 `NullPointerException`
을 던진다(`execute { ... }!!`).

이어서 `OrderFacadeIntegrationTest` 를 **한 줄도 고치지 않고** 돌려 회귀가 없는지 확인한다.

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeIntegrationTest'
```

기대: PASS. 이 테스트는 단일 스레드라 버전 충돌이 나지 않으므로 재시도 경로를 타지 않는다 —
동작이 바뀌지 않았다는 증거다.

- [ ] **Step 7: 스위치를 확인하는 테스트를 더한다**

`apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt`

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockStockLockStrategySwitchTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) {
    @DisplayName("optimistic 으로 설정하면, 낙관적 락 전략이 올라온다.")
    @Test
    fun loadsOptimisticStrategy() {
        assertThat(stockDecreaseStrategy).isInstanceOf(OptimisticLockStockDecreaseStrategy::class.java)
    }
}
```

- [ ] **Step 8: 예외 번역을 실제로 확인한다**

Step 4 의 KDoc 이 세운 가정 — raw `OptimisticLockException` 이 `ObjectOptimisticLockingFailureException`
으로 번역된다 — 을 목이 아니라 진짜 DB 로 확인한다. 두 스레드가 같은 상품을 동시에 주문하게 하고
한쪽이 CONFLICT(재시도 3 회 소진)로 끝나는지 본다. 이 테스트는 태스크 4 가 정식 동시성 테스트로
승격시키므로 여기서는 임시로 돌려서 확인만 한다.

```bash
SPRING_PROFILES_ACTIVE=test -Dloopers.stock.lock-strategy=optimistic \
  ./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeConcurrencyTest' \
  -Dloopers.stock.lock-strategy=optimistic
```

**이 명령은 참고용이다.** Gradle 의 `test` 태스크는 `-D` 를 포크된 테스트 JVM에 자동으로 넘기지
않을 수 있다 — 넘어가지 않으면 기본값(conditional-update)으로 돈다. 확실한 확인은 태스크 4 에서
`@SpringBootTest(properties = [...])` 로 컨텍스트를 직접 지정해서 한다. 여기서는 최소한
`ConditionalUpdateStockLockStrategySwitchTest` 류가 통과했다는 사실로 배선 자체는 검증됐다고 본다.

- [ ] **Step 9: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 **이전 + 3**(재시도 단위 테스트 2, 스위치 확인 1).

- [ ] **Step 10: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/OptimisticLockStockDecreaseStrategy.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/OptimisticLockStockLockStrategySwitchTest.kt
git commit -m "feat : 낙관적 락 재고 차감 전략과 재시도 래퍼를 추가한다"
```

---

### Task 3: 비관적 락

**파일:**
- 생성: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/PessimisticLockStockDecreaseStrategy.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt`
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`

**인터페이스:**
- 사용: `StockDecreaseStrategy` (태스크 1), 기존 `ProductJpaRepository.decreaseStock`
- 제공: `ProductJpaRepository.findByIdForUpdate(productId: Long): ProductModel?`

**배경 — 왕복 2 회가 핵심:** 항목마다 `SELECT ... FOR UPDATE` 로 잠근 뒤 **앱에서** 재고를 검사하고,
통과하면 `UPDATE` 한다. `IN` 절로 여러 상품을 한 번에 잠그지 않는다 — 그러면 조건부 UPDATE(항목당
왕복 1)와 비교할 때 비관적 락의 왕복이 2 가 아니라 사실상 1 에 가까워져 부당하게 유리해진다.
재는 것이 전략이 아니라 왕복을 접은 효과가 되어 버린다. (설계 문서 6.3 장)

**배경 — 왜 `UPDATE` 를 새로 쓰지 않고 기존 `decreaseStock` 을 재사용하는가:** 이미 `FOR UPDATE`
로 행을 잠그고 앱에서 재고를 확인한 뒤라 `WHERE stock >= :quantity` 는 사실상 항상 참이지만,
남겨 두는 편이 방어적이다. 더 중요한 이유는 따로 있다 — `updated_at` 을 건드리지 않는 그 raw
`UPDATE` 문을 그대로 쓰면, 새 `UPDATE` 문을 하나 더 만들며 실수로 `updated_at` 을 건드리게 될
여지 자체가 없어진다.

**배경 — 정렬이 이제 필수인 이유:** `OrderFacade.place` 의 `command.items.sortedBy { it.productId }`
는 조건부 UPDATE 에서는 이중 안전장치였지만, 비관적 락에서는 **필수 조건**이다. 여러 `SELECT ...
FOR UPDATE` 문이 순차로 락을 잡으므로, 두 주문이 같은 상품들을 반대 순서로 담으면 정렬이 없을 때
데드락이 난다. 코드는 이미 그 정렬을 하고 있으므로 이 태스크에서 `OrderFacade` 를 고칠 필요는
없다 — 이미 존재하는 기존 동시성 테스트 `doesNotDeadlock_whenOrdersLockProductsInOppositeOrder`
가 태스크 4 에서 비관적 락으로도 돌며 이것을 증명한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt`

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=pessimistic"])
class PessimisticLockStockLockStrategySwitchTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) {
    @DisplayName("pessimistic 으로 설정하면, 비관적 락 전략이 올라온다.")
    @Test
    fun loadsPessimisticStrategy() {
        assertThat(stockDecreaseStrategy).isInstanceOf(PessimisticLockStockDecreaseStrategy::class.java)
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.product.PessimisticLockStockLockStrategySwitchTest'
```

기대: 컴파일 실패 — `Unresolved reference: PessimisticLockStockDecreaseStrategy`

- [ ] **Step 3: 잠금 조회를 `ProductJpaRepository` 에 더한다**

```kotlin
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
```

`decreaseStock` 선언 아래에 더한다.

```kotlin
    /**
     * 비관적 락 전략 전용. 행을 잠근 채 반환해 재고 검사를 호출자(앱)에게 맡긴다.
     * (2026-09-09 설계 문서 6.3 장)
     *
     * 삭제된 상품은 잠글 이유가 없으므로 조회 대상에서 제외한다 — 다른 조회 메서드들과 같은 규칙이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id = :productId AND p.deletedAt IS NULL")
    fun findByIdForUpdate(@Param("productId") productId: Long): ProductModel?
```

- [ ] **Step 4: `PessimisticLockStockDecreaseStrategy` 를 만든다**

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * 비관적 락(SELECT ... FOR UPDATE) 재고 차감 전략. (2026-09-09 설계 문서 6.3 장)
 *
 * 항목마다 왕복 2 회다 — 잠금 SELECT 하나, 검사 후 UPDATE 하나. IN 절로 여러 상품을 한 번에
 * 잠그지 않는다 — 그러면 조건부 UPDATE(항목당 왕복 1)와 비교할 때 이 전략이 부당하게 유리해진다.
 *
 * UPDATE 는 기존 ProductJpaRepository.decreaseStock 을 그대로 재사용한다. 이미 FOR UPDATE 로
 * 행을 잠그고 재고를 확인한 뒤라 그 WHERE stock >= :quantity 는 사실상 항상 참이지만, 그 문장이
 * updated_at 을 건드리지 않는다는 사실까지 함께 재사용하기 위해 새 UPDATE 문을 만들지 않는다.
 *
 * OrderFacade.place 의 productId 오름차순 정렬이 여기서는 이중 안전장치가 아니라 필수 조건이다.
 * 여러 문장이 순차로 락을 잡으므로 순서가 통일되지 않으면 데드락이 난다.
 */
@Repository
@ConditionalOnProperty(name = ["loopers.stock.lock-strategy"], havingValue = "pessimistic")
class PessimisticLockStockDecreaseStrategy(
    private val productJpaRepository: ProductJpaRepository,
) : StockDecreaseStrategy {
    private val log = LoggerFactory.getLogger(PessimisticLockStockDecreaseStrategy::class.java)

    init {
        log.info("재고 차감 전략 선택 : pessimistic")
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        val locked = productJpaRepository.findByIdForUpdate(productId) ?: return 0
        if (locked.stock.value < quantity) return 0

        return productJpaRepository.decreaseStock(productId = productId, quantity = quantity)
    }
}
```

- [ ] **Step 5: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.product.PessimisticLockStockLockStrategySwitchTest'
```

기대: PASS

- [ ] **Step 6: 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 **이전 + 1**.

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/PessimisticLockStockDecreaseStrategy.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/PessimisticLockStockLockStrategySwitchTest.kt
git commit -m "feat : 비관적 락 재고 차감 전략을 추가한다"
```

---

### Task 4: 세 전략 공통 계약 테스트

**파일:**
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/AbstractStockDecreaseContractTest.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ConditionalUpdateStockDecreaseContractTest.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/OptimisticLockStockDecreaseContractTest.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/PessimisticLockStockDecreaseContractTest.kt`
- 생성(이름 변경): `apps/commerce-api/src/test/kotlin/com/loopers/application/order/AbstractOrderFacadeConcurrencyTest.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/ConditionalUpdateOrderFacadeConcurrencyTest.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OptimisticLockOrderFacadeConcurrencyTest.kt`
- 생성: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/PessimisticLockOrderFacadeConcurrencyTest.kt`

**인터페이스:**
- 사용: 태스크 1·2·3 이 만든 세 `StockDecreaseStrategy` 구현, 기존 `OrderFacade`
- 제공: 없음 (검증만)

**배경 — 왜 추상 클래스 + `@SpringBootTest(properties=...)` 서브클래스인가:** 컴파일타임 상수인
`-D` 시스템 프로퍼티를 Gradle `test` 태스크가 포크된 JVM 에 항상 넘겨준다는 보장이 없다(태스크 2
Step 8 참고). `@SpringBootTest(properties = [...])` 는 클래스마다 **다른 스프링 컨텍스트** 를
띄우는 표준 메커니즘이라 신뢰할 수 있다 — `properties` 값이 다르면 스프링이 별도의 컨텍스트를
캐싱하므로, 컨텍스트마다 `@ConditionalOnProperty` 로 선택되는 `StockDecreaseStrategy` 빈이 정확히
하나로 갈린다.

같은 테스트 본문을 세 번 타이핑하지 않기 위해 실제 `@Test` 메서드는 전부 **추상 클래스**에 두고,
구체 클래스는 `@SpringBootTest(properties=...)` 하나만 얹은 빈 껍데기로 둔다. 이 패턴이 성립하려면
추상 클래스가 **필드 주입**(`@Autowired lateinit var`)을 써야 한다 — 생성자 주입이면 모든 서브클래스가
그 생성자 시그니처를 다시 선언해 `super(...)` 로 넘겨야 해서 보일러플레이트가 커진다.

**배경 — `updated_at` 을 공통 계약에서 뺀 이유:** 태스크 2 배경에서 적었듯, 낙관적 락은 엔티티
dirty checking 을 거쳐야 `@Version` 검사가 걸리므로 `BaseEntity.preUpdate()` 가 함께 돌아
`updated_at` 이 갱신된다. 조건부 UPDATE 와 비관적 락은 raw `UPDATE` 라 갱신되지 않는다. 그래서
`updated_at` 불변 검증은 공통 계약(추상 클래스)이 아니라 **조건부 UPDATE·비관적 락 서브클래스에만**
따로 둔다 — 세 전략에 똑같이 기대하면 낙관적 락 쪽에서 항상 실패하는 단언이 된다.

**배경 — 기존 동시성 테스트를 추상화하는 이유:** `OrderFacadeConcurrencyTest` 의 기존 3 건(초과
판매 방지·차감 합계·데드락 방지)은 이미 조건부 UPDATE 를 전제로 통과가 확인된 회귀 방지선이다.
이 태스크는 그 본문을 한 글자도 바꾸지 않고 낙관적 락·비관적 락에서도 돌려, "다른 두 전략도 같은
동시성 계약을 지킨다" 를 확인한다. 실패하면 태스크 2·3 의 구현이 잘못된 것이다.

- [ ] **Step 1: 계약 테스트의 실패 버전을 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/AbstractStockDecreaseContractTest.kt`

```kotlin
package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired

/**
 * 세 락 전략이 공통으로 지켜야 하는 계약. (2026-09-09 설계 문서 6.4 장)
 *
 * 구체 클래스마다 @SpringBootTest(properties=[...]) 로 스프링 컨텍스트를 따로 띄운다 — 컨텍스트마다
 * 다른 StockDecreaseStrategy 빈 하나만 올라오므로, 같은 테스트 본문이 세 구현을 각각 검증하게 된다.
 *
 * updated_at 불변은 여기서 공통으로 확인하지 않는다. 낙관적 락은 엔티티 dirty checking 을 거쳐야
 * @Version 검사가 걸리므로 BaseEntity.preUpdate 가 함께 돈다 — 세 전략 중 유일하게 updated_at 이
 * 갱신된다. 이것은 구현의 실수가 아니라 그 기법 자체의 성질이다(태스크 2 배경 참고). 그래서
 * 조건부 UPDATE·비관적 락 서브클래스에만 그 전용 테스트를 따로 둔다.
 */
abstract class AbstractStockDecreaseContractTest {
    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    protected fun saveProduct(stock: Long): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(1_000),
                stock = Stock(stock),
            ),
        )
    }

    @DisplayName("재고가 넉넉하면, 정확히 요청 수량만큼 줄고 영향 행 수는 1 이다.")
    @Test
    fun decreasesByQuantity_whenStockIsSufficient() {
        // arrange
        val product = saveProduct(stock = 10)

        // act
        val affected = productRepository.decreaseStock(product.id, 3)

        // assert
        assertAll(
            { assertThat(affected).isEqualTo(1) },
            { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(7L) },
        )
    }

    @DisplayName("재고와 요청 수량이 같으면, 0 이 되고 영향 행 수는 1 이다.")
    @Test
    fun decreasesToZero_whenStockEqualsQuantity() {
        // arrange
        val product = saveProduct(stock = 5)

        // act
        val affected = productRepository.decreaseStock(product.id, 5)

        // assert
        assertAll(
            { assertThat(affected).isEqualTo(1) },
            { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(0L) },
        )
    }

    /** 초과 판매 방지의 본체다. 이 단언이 실패하면 그 전략의 WHERE 절(또는 낙관적 락의 사전 검사)이 깨진 것이다. */
    @DisplayName("재고가 모자라면, 아무것도 바꾸지 않고 영향 행 수는 0 이다.")
    @Test
    fun doesNothing_whenStockIsInsufficient() {
        // arrange
        val product = saveProduct(stock = 2)

        // act
        val affected = productRepository.decreaseStock(product.id, 3)

        // assert
        assertAll(
            { assertThat(affected).isEqualTo(0) },
            { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(2L) },
        )
    }

    @DisplayName("삭제된 상품이면, 영향 행 수는 0 이다.")
    @Test
    fun returnsZero_whenProductIsSoftDeleted() {
        // arrange
        val product = saveProduct(stock = 10)
        productService.delete(product.id)

        // act
        val affected = productRepository.decreaseStock(product.id, 1)

        // assert
        assertThat(affected).isEqualTo(0)
    }

    @DisplayName("존재하지 않는 상품이면, 영향 행 수는 0 이다.")
    @Test
    fun returnsZero_whenProductDoesNotExist() {
        // act
        val affected = productRepository.decreaseStock(999_999L, 1)

        // assert
        assertThat(affected).isEqualTo(0)
    }
}
```

세 구체 클래스를 만든다.

```kotlin
package com.loopers.domain.product

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=conditional-update"])
class ConditionalUpdateStockDecreaseContractTest : AbstractStockDecreaseContractTest()
```

```kotlin
package com.loopers.domain.product

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockStockDecreaseContractTest : AbstractStockDecreaseContractTest()
```

```kotlin
package com.loopers.domain.product

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=pessimistic"])
class PessimisticLockStockDecreaseContractTest : AbstractStockDecreaseContractTest()
```

- [ ] **Step 2: 실패(또는 컴파일 오류)를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.product.*StockDecreaseContractTest'
```

이 시점에는 태스크 1~3 이 이미 끝나 있으므로 **실제로는 전부 PASS 할 가능성이 높다** — 이 태스크는
새 동작이 아니라 기존 세 구현의 정확성을 처음으로 한자리에서 확인하는 것이기 때문이다. 만약 하나라도
실패한다면 그것이 이 태스크의 진짜 성과다 — 태스크 2 또는 3 의 구현으로 돌아가 원인을 고친다.

- [ ] **Step 3: `updated_at` 불변 테스트를 조건부 UPDATE·비관적 락에만 더한다**

`ConditionalUpdateStockDecreaseContractTest.kt` 와 `PessimisticLockStockDecreaseContractTest.kt`
양쪽에 각각 더한다 (본문은 동일하다 — 공통 클래스로 뽑을 만큼 크지 않고, 낙관적 락에는 없는
단언이라는 사실이 파일을 보는 것만으로 드러나야 한다).

```kotlin
    @DisplayName("차감은 updated_at 을 건드리지 않는다.")
    @Test
    fun doesNotTouchUpdatedAt() {
        // arrange
        val product = saveProduct(stock = 10)
        val before = productRepository.findById(product.id)!!.updatedAt

        // act
        productRepository.decreaseStock(product.id, 1)

        // assert — raw UPDATE 문은 BaseEntity.preUpdate 콜백을 타지 않는다 (2026-08-24 설계 문서 6.3 장)
        assertThat(productRepository.findById(product.id)!!.updatedAt).isEqualTo(before)
    }
```

`import org.junit.jupiter.api.DisplayName` · `Test` · `assertThat` 이 이미 있는지 확인한다(상속만
있고 자기 테스트가 없던 파일이라면 추가해야 한다).

- [ ] **Step 4: 기존 동시성 테스트를 세 전략으로 나눈다**

먼저 히스토리를 보존하며 이름을 바꾼다.

```bash
cd /Users/choeseongang/IdeaProjects/study-project/loop-pack-be-l2-vol3-kotlin
git mv apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeConcurrencyTest.kt \
       apps/commerce-api/src/test/kotlin/com/loopers/application/order/AbstractOrderFacadeConcurrencyTest.kt
```

파일을 열어 클래스 선언과 생성자 주입을 필드 주입으로 바꾼다. **테스트 메서드·헬퍼 메서드 본문은
한 글자도 바꾸지 않는다** — 무엇을 확인하는지는 그대로이고 어떻게 인스턴스를 만드는지만 바뀐다.

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

위를 아래로 바꾼다.

```kotlin
abstract class AbstractOrderFacadeConcurrencyTest {
    @Autowired
    private lateinit var orderFacade: OrderFacade

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var databaseCleanUp: DatabaseCleanUp
```

`@SpringBootTest` 애노테이션은 지운다 — 구체 클래스가 각자 다른 `properties` 로 붙인다.
`import org.springframework.boot.test.context.SpringBootTest` 도 더 안 쓰이면 지운다
(`ktlintCheck` 가 미사용 임포트를 잡는다).

클래스 KDoc 맨 위에 한 문단을 더한다.

```kotlin
/**
 * 주문의 동시성 계약을 지키는 회귀 테스트.
 *
 * 세 락 전략 각각의 스프링 컨텍스트에서 이 본문을 그대로 돌린다 — 구체 클래스(ConditionalUpdate·
 * OptimisticLock·PessimisticLock 접두어)가 @SpringBootTest(properties=[...]) 로 전략을 고정하고,
 * 실제 테스트는 전부 여기 있다. (2026-09-09 설계 문서 6.4 장)
 *
 * Testcontainers 가 띄우는 진짜 MySQL 8.0 위에서 돌기 때문에 InnoDB 의 행 락과 데드락 감지가 실제로 동작한다.
 * ...
 */
```

세 구체 클래스를 만든다.

```kotlin
package com.loopers.application.order

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=conditional-update"])
class ConditionalUpdateOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencyTest()
```

```kotlin
package com.loopers.application.order

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencyTest()
```

```kotlin
package com.loopers.application.order

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=pessimistic"])
class PessimisticLockOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencyTest()
```

- [ ] **Step 5: 세 전략 모두 통과하는지 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.*OrderFacadeConcurrencyTest'
```

기대: 9 건(3 전략 × 3 테스트) 전부 PASS.

**낙관적 락에서 실패한다면 먼저 의심할 것 — `sellsExactlyStock_whenMoreUsersOrderConcurrently` 는
재시도 상한 3 회 안에서 경합이 해소되지 못하면 실패로 보일 수 있다.** 재고 9, 동시 요청 10 이라
정확히 1 건만 실패해야 하는데, 낙관적 락은 버전 충돌로 실패한 요청이 재시도 중 **다시** 충돌할
수 있어 상한을 넘겨 CONFLICT 가 여러 건 나올 가능성이 이론적으로 있다. 이 계획의 동시성 강도
(10 스레드)에서는 3 회 재시도로 충분할 것으로 예상하지만, 만약 이 테스트가 불안정하게(flaky)
실패한다면 그 자체가 3.6 장의 "무제한 재시도를 쓰지 않는 이유" 가설을 반증하는 데이터이므로
**억지로 통과시키지 말고 그대로 보고한다.**

- [ ] **Step 6: 계약 테스트까지 포함해 전체 회귀와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 **이전 + 약 23**
(계약 테스트 5 × 3 = 15, `updated_at` 전용 2, 동시성 재편으로 늘어난 순증 6 — 기존 3 건이
`ConditionalUpdate` 서브클래스로 그대로 옮겨가고 `OptimisticLock` · `PessimisticLock` 서브클래스가
각각 3 건씩 새로 돈다).

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/test/kotlin/com/loopers/domain/product/AbstractStockDecreaseContractTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ConditionalUpdateStockDecreaseContractTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/OptimisticLockStockDecreaseContractTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/PessimisticLockStockDecreaseContractTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/AbstractOrderFacadeConcurrencyTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/ConditionalUpdateOrderFacadeConcurrencyTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/OptimisticLockOrderFacadeConcurrencyTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/PessimisticLockOrderFacadeConcurrencyTest.kt
git commit -m "test : 세 락 전략의 공통 계약과 기존 동시성 테스트를 세 전략에서 검증한다"
```

---

### Task 5: 부하 하네스 다중 항목 시나리오

**파일:**
- 수정: `loadtest/ab.js`
- 수정: `loadtest/README.md`

**인터페이스:**
- 사용: 없음 (Gradle 테스트와 무관하다)
- 제공: 환경변수 `ITEMS_PER_ORDER` (기본 `1`)

**배경 — 왜 필요한가:** 이 태스크가 없으면 다중 항목에서 세 전략이 갈라지는 축(설계 문서 3.5 장)을
잴 방법이 없다. 항목 1 개 주문에서는 세 전략의 차이가 가장 작게 나온다 — 그 지점만 재는 것은
비교의 조건을 스스로 좁히는 것이다.

기본값이 `1` 이어야 한다. 그래야 2026-09-06 문서의 기존 결과와 같은 조건으로 비교할 수 있다.

**핫 상품이 반드시 포함돼야 한다.** 다중 항목이 되면 상품을 여럿 고르므로 그냥 무작위로 뽑으면
핫스팟과 분산의 경계가 흐려진다. 핫스팟 시나리오는 "핫 상품 1 개 + 무작위 N−1 개" 로 고정한다.
(설계 문서 5.3 장)

**배경 — 전략 라벨은 스크립트가 만들지 않는다:** 기존 `LABEL` 환경변수가 이미 결과 파일명과 콘솔
요약 제목에 찍힌다. 별도의 `STRATEGY` 환경변수를 새로 만들지 않는 이유는, 그러면 "`LABEL` 이 실제로
가리키는 것" 과 "`STRATEGY` 가 가리키는 것" 이 어긋날 수 있는 자리가 하나 늘기 때문이다. 대신
태스크 6 의 측정 절차가 **`LABEL` 값 자체를 전략 이름으로 준다** — 예: `LABEL=optimistic`.

- [ ] **Step 1: 환경변수와 상품 선택 함수를 추가한다**

`ab.js` 의 환경변수 블록에서 `P95_THRESHOLD_MS` 선언 아래에 추가한다.

```javascript
// 주문 한 건에 담을 항목 수. 기본 1 은 2026-09-06 측정과 같은 조건이다 — 그래야 비교할 수 있다.
// 2 이상이면 재고 차감이 여러 행을 건드리므로, 세 락 전략이 갈라지는 축이 이 스크립트에서 드러난다.
// (2026-09-09 설계 문서 3.5 장 · 5.3 장)
const ITEMS_PER_ORDER = Number(__ENV.ITEMS_PER_ORDER || 1);

if (ITEMS_PER_ORDER < 1 || ITEMS_PER_ORDER > PRODUCT_COUNT) {
    throw new Error('ITEMS_PER_ORDER 는 1 이상 ' + PRODUCT_COUNT + ' 이하여야 한다. 받은 값: ' + ITEMS_PER_ORDER);
}
```

`PRODUCT_COUNT` 선언보다 뒤에 두어야 한다. 위 검사가 그 값을 읽는다.

- [ ] **Step 2: 상품 선택을 함수로 뽑는다**

`placeOrder` 함수 위에 추가한다.

```javascript
// 핫스팟은 productId=1 을 반드시 포함한다. 다중 항목에서 무작위로만 뽑으면 핫 행이 빠진 주문이
// 섞여 "단일 행 직렬화" 를 재는 실험이 아니게 된다. (2026-09-09 설계 문서 5.3 장)
// 중복 productId 는 서버가 400 으로 막으므로 반드시 제거한다.
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

`buildConsoleSummary` 의 제목 줄에 더한다.

```javascript
    lines.push(' 주문 처리량 락 전략 비교 부하 테스트 — ' + SCENARIO_NAME + ' / label=' + LABEL +
        ' / target=' + TARGET_TPS + ' TPS / items=' + ITEMS_PER_ORDER);
```

- [ ] **Step 5: 하위 호환을 확인한다**

앱을 띄우지 않고 스크립트만 검사한다.

```bash
k6 inspect loadtest/ab.js
```

기대: 오류 없이 시나리오 정의가 출력된다. `ITEMS_PER_ORDER` 를 주지 않았을 때 핫스팟이 `[1]`
하나만 담는지(기존 동작과 같은지) 코드를 읽어 재확인한다.

- [ ] **Step 6: README 를 갱신한다**

환경변수 표에 한 줄 추가한다.

```markdown
| `ITEMS_PER_ORDER` | `1` | 주문 한 건의 항목 수. 기본 1 은 2026-09-06 측정과 같은 조건이다. 2 이상이면 세 락 전략이 갈라지는 축을 재는 실험이 된다 (2026-09-09 설계 문서 3.5 · 5.3 장) |
```

**새 섹션 "락 전략 비교 절차"를 "측정 절차" 다음, "A/B 조건 통일 체크리스트" 앞에 추가한다.**

```markdown
## 락 전략 비교 절차 (2026-09-09 설계 문서 5 장)

3 전략(`conditional-update` / `optimistic` / `pessimistic`) × 3 시나리오(hotspot / spread /
items=3) = 9 칸을 채운다. `docker/loadtest-compose.yml` 은 jar 하나만 올리므로, 전략을 바꾸려면
`apps/commerce-api/src/main/resources/application.yml` 의 `loopers.stock.lock-strategy` 를 고쳐
**다시 빌드**해야 한다 — 세 전략의 jar 를 각각 만들어 둔다(예: `commerce-api-conditional-update.jar`,
`commerce-api-optimistic.jar`, `commerce-api-pessimistic.jar`).

**`LABEL` 을 전략 이름으로 준다.** 별도의 전략 환경변수는 없다 — 기존 `LABEL` 이 결과 파일명과
콘솔 요약 제목에 이미 찍히므로, `LABEL=optimistic` 처럼 전략 이름 그 자체를 준다.

```bash
SCENARIO=hotspot TARGET_TPS=400 LABEL=optimistic k6 run loadtest/ab.js
SCENARIO=hotspot TARGET_TPS=400 LABEL=optimistic ITEMS_PER_ORDER=3 k6 run loadtest/ab.js
```

**한 시나리오의 세 전략을 같은 세션 안에서 연달아 잰다.** 날을 나눠 재면 배경 부하·디스크 상태·
컨테이너 스케줄링이 달라져 전략 차이보다 환경 차이가 커질 수 있다(2026-09-06 문서 12.7 장).
세 전략을 가로질러 비교하는 표는 항상 같은 세션에서 나온 값이어야 한다.

**한 칸이라도 채우지 못하면 그 시나리오의 표를 만들지 않는다.** 두 전략만 재고 세 번째를
추론으로 채우면 이 비교가 없애려던 것 — 재 보지 않고 순서를 말하는 일 — 로 돌아간다.
```

"알려진 제약" 절에 한 줄 추가한다.

```markdown
- 다중 항목(`ITEMS_PER_ORDER` 2 이상) 결과를 항목 1 개 결과와 같은 표에 놓고 비교하지 않는다.
  서로 다른 실험이다 (2026-09-09 설계 문서 5.5 장).
```

- [ ] **Step 7: 커밋**

```bash
git add loadtest/ab.js loadtest/README.md
git commit -m "test : 부하 하네스에 다중 항목 시나리오와 락 전략 비교 절차를 추가한다"
```

---

### Task 6: 측정 실행과 문서 반영

**파일:**
- 수정: `docs/superpowers/specs/2026-09-09-lock-strategy-throughput-design.md`

**인터페이스:**
- 사용: 태스크 1~5 산출물 전부
- 제공: 없음 (문서만)

**배경:** 이 태스크는 코드를 만들지 않는다. 3 전략 × 3 시나리오 9 칸을 실제로 채우고, 설계 문서
3 장의 가설표를 실측표로 교체하는 **운영 태스크**다. 5.1 장의 공정성 조건을 어기면 잰 것이 전략이
아니라 구현이 되므로, 아래 체크리스트를 전부 통과한 측정만 표에 넣는다.

측정은 Docker 가 필요하고 사람의 판단(포화 여부, TPS 스윕)이 들어가므로, 이 태스크는 **실행자가
직접 자원을 준비하고 판단해야** 한다. 아래는 순서와 판정 기준이다.

- [ ] **Step 1: 빌드를 준비한다**

```bash
cd /Users/choeseongang/IdeaProjects/study-project/loop-pack-be-l2-vol3-kotlin
./gradlew :apps:commerce-api:ktlintCheck :apps:commerce-api:test
```

전체 스위트가 0 failures 인 것을 최종 확인한 뒤에만 다음으로 간다. 태스크 1~5 중 하나라도
실패 상태로 남아 있으면 이 측정은 신뢰할 수 없다.

세 전략의 jar 를 만든다. `application.yml` 의 `loopers.stock.lock-strategy` 를 바꿔 가며 빌드한다.

```bash
# conditional-update (기본값 — 수정 없이 빌드)
./gradlew :apps:commerce-api:bootJar
cp apps/commerce-api/build/libs/commerce-api-*.jar \
   apps/commerce-api/build/libs/commerce-api-conditional-update.jar

# application.yml 의 lock-strategy: optimistic 로 바꾼 뒤
./gradlew :apps:commerce-api:bootJar
cp apps/commerce-api/build/libs/commerce-api-*.jar \
   apps/commerce-api/build/libs/commerce-api-optimistic.jar

# application.yml 의 lock-strategy: pessimistic 로 바꾼 뒤
./gradlew :apps:commerce-api:bootJar
cp apps/commerce-api/build/libs/commerce-api-*.jar \
   apps/commerce-api/build/libs/commerce-api-pessimistic.jar
```

**빌드가 끝나면 `application.yml` 을 `conditional-update` 로 되돌려 둔다** — 이 값이 리포지토리에
커밋된 기본 상태다.

- [ ] **Step 2: 공정성 조건을 체크리스트로 확인한다 (설계 문서 5.1 장)**

각 칸을 재기 직전에 확인한다.

- [ ] 같은 이미지·같은 자원 배분 (`docker/loadtest-compose.yml` 을 수정하지 않았다)
- [ ] 같은 k6 스크립트·같은 시드 데이터 (`loadtest/prepare.sql` 을 매 측정 직전 재주입했다)
- [ ] 세 전략 모두 항목마다 처리하는 형태다 (비관적 락도 `IN` 절로 접지 않았다 — 태스크 3 코드 확인)
- [ ] 차감 순서는 `productId` 오름차순이다 (`OrderFacade.place` 를 건드리지 않았다)
- [ ] 낙관적 락에 백오프가 없다 (태스크 2 `OrderFacade.place` 의 `repeat` 안에 `Thread.sleep` 등이 없다)
- [ ] 재시도 상한 3 이 문서와 코드 양쪽에서 같다

- [ ] **Step 3: 9 칸을 채운다**

`loadtest/README.md` 의 "락 전략 비교 절차" 를 따른다. 시나리오 하나(예: hotspot)를 고르면 세
전략을 **연달아** 잰다.

```bash
docker compose -f docker/loadtest-compose.yml down -v
APP_JAR=commerce-api-conditional-update.jar docker compose -f docker/loadtest-compose.yml up -d
# 헬스체크 대기 → 재고 주입 →
SCENARIO=hotspot TARGET_TPS=<스윕값> LABEL=conditional-update k6 run loadtest/ab.js

docker compose -f docker/loadtest-compose.yml down -v
APP_JAR=commerce-api-optimistic.jar docker compose -f docker/loadtest-compose.yml up -d
# 헬스체크 대기 → 재고 주입 →
SCENARIO=hotspot TARGET_TPS=<스윕값> LABEL=optimistic k6 run loadtest/ab.js

docker compose -f docker/loadtest-compose.yml down -v
APP_JAR=commerce-api-pessimistic.jar docker compose -f docker/loadtest-compose.yml up -d
# 헬스체크 대기 → 재고 주입 →
SCENARIO=hotspot TARGET_TPS=<스윕값> LABEL=pessimistic k6 run loadtest/ab.js
```

TPS 를 올려 가며 각 칸의 상한(무릎점)을 찾는다. 상한 TPS·p50·p95·**에러율**·`dropped_iterations`
을 기록한다 (설계 문서 5.2 장 — 에러율이 세 전략을 가르는 축이라는 점을 잊지 않는다).

`SCENARIO=spread`, 그리고 `ITEMS_PER_ORDER=3`(핫스팟 위에 얹어 다중 항목을 함께 잰다) 로 같은
과정을 반복한다.

**한 칸이라도 상한을 확정하지 못하면 그 시나리오의 표를 만들지 않는다** (설계 문서 4.2 · 5.5 장).

- [ ] **Step 4: 설계 문서 3 장을 실측으로 교체한다**

`docs/superpowers/specs/2026-09-09-lock-strategy-throughput-design.md` 를 수정한다.

- 문서 맨 위의 "⚠️ 이 문서의 처리량 수치는 아직 전부 추정이다" 경고 블록을 지운다.
- 3.6 장의 가설표(예측 순서·판정 지표)를 실측값으로 교체한다. 가설이 맞았으면 "확인됨" 을,
  틀렸으면 **"틀렸다" 라고 명시하고 실측값을 함께 적는다** — 3.6 장 자신이 이미 그렇게 하라고
  써 두었다.
- 채우지 못한 칸이 있다면 그 시나리오 전체를 "미측정" 으로 남기고 이유(무엇이 안정되지 않았는지)를
  적는다. 두 칸으로 순서를 말하지 않는다.
- 4.1 장(재시도 상한 종속성)·4.3 장(불공정 구현 위험)에 실측이 그 우려를 어떻게 뒷받침하거나
  반박했는지 한 문단씩 추가한다.
- 진 전략이 있다면 **왜 졌는지** 를 3 장에 적는다 — 다음 사람이 "낙관적 락을 써 보면 어떨까" 라고
  물었을 때 답이 이미 있도록 (설계 문서 6.5 장).

- [ ] **Step 5: 커밋**

```bash
git add docs/superpowers/specs/2026-09-09-lock-strategy-throughput-design.md
git commit -m "docs : 락 전략 비교 실측 결과를 설계 문서에 반영한다"
```

---

## 계획 밖

- **채택된 전략 하나만 남기고 나머지 둘을 제거하는 일 (설계 문서 6.5 장).** 이 계획은 셋을
  구현하고 측정하는 것까지다. 무엇이 이겼는지는 태스크 6 이 끝나야 알 수 있으므로, 제거는 그
  결과를 근거로 한 별도의 후속 계획이 맡는다 — `StockDecreaseStrategy` 인터페이스, 스위치,
  `@Version` 컬럼, 진 두 전략의 구현·테스트 파일이 그 대상이다.
- **재고 차감 단일 문장화(배치).** `docs/superpowers/plans/2026-09-11-batch-stock-decrease.md` 가
  다루며, 그 설계 문서 자신이 "이 비교가 끝나 전략이 정해진 뒤에" 착수한다고 명시했다.
- **인프라 설정(CPU/메모리 배분, `binlog_group_commit_sync_delay` 등).** 2026-09-06 문서 11·12 장이
  다루는 영역이며 이 비교의 범위가 아니다.
- **재고 행 분할(샤딩).** 핫스팟 상한 자체를 올리는 유일한 방법이지만 과제 범위를 벗어난다
  (설계 문서 2 장).
- **쿠폰 경로(`user_coupons`)의 락 전략 비교.** 같은 조건부 UPDATE 를 쓰지만 지속 부하를 걸 수
  없어 이번 비교에 넣지 못한다 (설계 문서 2 장).
- **주문 항목 수 상한.** `OrderCommand.Place` 는 항목 수를 제한하지 않는다. 부하 하네스가
  `ITEMS_PER_ORDER` 검사를 넣지만 그것은 스크립트 방어이지 API 계약이 아니다.

---

## 설계 문서와의 불일치 (해소됨)

계획서를 쓰며 실제 코드를 조사하는 과정에서 설계 문서 6.4 장의 "세 전략 공통 계약" 표와
어긋나는 지점을 하나 발견했다. **설계 문서 쪽을 고쳐 해소했다** (2026-09-11).

- **`updated_at` 은 세 전략이 같지 않다.** 낙관적 락은 `@Version` 의 자동 검사를 받으려면
  엔티티를 읽고 고쳐 flush 해야 하고, 그 경로가 `BaseEntity.preUpdate()` 를 거쳐 `updated_at` 을
  갱신시킨다. **기법 고유의 성질이지 구현 실수가 아니다.**

  이 계획은 `updated_at` 검증을 공통 계약 테스트에서 빼고 조건부 `UPDATE` · 비관적 락 전용
  테스트로만 둔다 (태스크 4). 설계 문서 6.4 장도 같은 내용으로 갱신됐다.

> **처음 이 계획이 적었던 근거는 틀렸다 — 기록으로 남긴다.**
>
> "수동 JPQL 로 `version` 을 CAS 하면 영향 행 0 이 재고 부족인지 버전 충돌인지 구분할 수 없어
> 재시도를 트리거할 방법이 없다" 고 적었으나, **그렇지 않다.** 설계 문서 3.1 장이 그린 절차는
> `UPDATE` **앞에** `(앱에서 재고 검사)` 를 둔다. 재고 부족은 문장을 보내기도 전에 걸러지므로
> `WHERE` 에는 `version` 조건만 남고, **영향 행 0 은 버전 충돌만 뜻한다.**
>
> 즉 수동 JPQL 경로는 가능하며 `updated_at` 도 지킬 수 있다. 그럼에도 JPA 의 `@Version` 을 쓰는
> 이유는 다른 데 있다 — **재는 대상이 "JPA 가 제공하는 낙관적 락" 이어야 하고**, `updated_at`
> 한 컬럼은 같은 `UPDATE` 문장에 붙어 왕복도 인덱스 갱신도 늘리지 않아 **처리량에 영향이 없기**
> 때문이다. (설계 문서 6.4 장)
>
> 두 설계를 섞어 본 데서 나온 착오였다. **틀린 근거로 옳은 결론에 닿은 자리를 그대로 두면,
> 다음 사람이 그 근거를 사실로 배운다.**
