# 상품 좋아요 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원이 상품에 좋아요를 걸고·취소하고·자기가 좋아요한 상품 목록을 조회하는 API 3개를 구현하고, 비정규화된 `products.like_count` 를 동시 요청에서도 정확하게 유지한다.

**Architecture:** 기존 `interfaces → application → domain → infrastructure` 4계층을 그대로 쓴다. `ProductLikeModel` 을 새 애그리거트로 추가하되 다른 애그리거트는 식별자(`Long`)로만 참조한다. **모든 상태 전이는 조건부 `UPDATE` 의 영향 행 수로 판정하고**, 엔티티 메서드로 상태를 바꾸지 않는다. 좋아요 행과 `like_count` 는 같은 트랜잭션에서 함께 움직인다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, MySQL 8.0, JUnit 5, AssertJ, Testcontainers

**설계 문서:** [`docs/superpowers/specs/2026-08-20-product-like-design.md`](../specs/2026-08-20-product-like-design.md) — 각 결정의 근거는 여기에 있다. 계획과 문서가 어긋나면 **문서가 기준이다.**

**선행 문서:**
- [`2026-08-13-brand-product-design.md`](../specs/2026-08-13-brand-product-design.md) — 값 객체·페이징·`like_count` 컬럼의 출처
- [`2026-08-15-brand-product-admin-design.md`](../specs/2026-08-15-brand-product-admin-design.md) — 소프트 삭제·연쇄 삭제·어드민 규약

---

## Global Constraints

- 모든 새 파일은 `apps/commerce-api` 모듈 아래에 만든다. 다른 모듈(`modules/*`, `supports/*`)은 **수정하지 않는다.** 특히 `modules/jpa` 의 `BaseEntity` 는 건드리지 않는다 — `commerce-batch` / `commerce-streamer` 가 함께 쓴다.
- 패키지 루트는 `com.loopers` 다.
- 검증 실패는 전부 `CoreException(ErrorType.XXX, "메시지")` 로 던진다. 표준 예외를 쓰지 않는다.
- **`ErrorType` 에 새 상수를 추가하지 않는다.** 이번 작업이 쓰는 것은 `BAD_REQUEST` 와 `NOT_FOUND` 둘뿐이다. `401` 과 `409` 는 쓰지 않는다 (설계 문서 8.2 장).
- **`ApiControllerAdvice` 를 수정하지 않는다.** 헤더 누락(`MissingRequestHeaderException`)과 경로 변수 타입 불일치(`MethodArgumentTypeMismatchException`)는 이미 400 으로 처리된다.
- 도메인 계층(`domain/**`)의 인터페이스 시그니처에 `deletedAt` 이나 `org.springframework.data.domain.*` 타입이 등장해서는 안 된다. 소프트 삭제 필터와 `Pageable` 번역은 `infrastructure/**` 의 `RepositoryImpl` 이 한다.
- 도메인 서비스의 **조회**는 대상이 없으면 `null` 을 반환한다. 404 로 볼지는 `Facade` 가 정한다.
- **엔티티의 `delete()` / `restore()` 로 좋아요 상태를 바꾸지 않는다.** 갱신 손실이 발생한다 (설계 문서 6.2 장). 전이는 전부 조건부 `UPDATE` 다.
- **`LikeCount` 에 `increase()` / `decrease()` 를 추가하지 않는다** (설계 문서 6.5 장).
- `product_likes` 를 갱신하는 `UPDATE` 는 `updated_at` 을 `SET` 절에 직접 쓴다. JPQL 은 `@PreUpdate` 를 타지 않는다. 반대로 **`products` 를 갱신하는 `UPDATE` 는 `updated_at` 을 건드리지 않는다** (설계 문서 6.4 장).
- 주석은 한국어로 쓴다. "무엇을" 이 아니라 "왜" 를 쓴다.
- **블록 주석 안에 `/**` 가 들어가는 문자열을 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되므로 KDoc 본문에 경로 패턴을 그대로 적으면 `Unclosed comment` 로 컴파일이 깨진다.
- 커밋 메시지는 한국어로 쓰고 `feat : ` / `test : ` / `docs : ` 형식(콜론 앞에 공백)을 따른다.
- 코드 스타일은 ktlint 가 강제한다. 최대 줄 길이 130자(`*Test.kt` 는 제한 없음).
- **통합·E2E 테스트는 Docker 가 실행 중이어야 한다.** Testcontainers 가 `mysql:8.0` 컨테이너를 띄운다.

## 공통 명령어

```bash
# 특정 테스트 클래스
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.ProductLikeModelTest"

# 특정 패키지 전체
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.*"

# 모듈 전체 테스트
./gradlew :apps:commerce-api:test

# 스타일 검사 / 자동 수정
./gradlew :apps:commerce-api:ktlintCheck
./gradlew :apps:commerce-api:ktlintFormat
```

## File Structure

### 신규

| 파일 | 책임 |
|---|---|
| `domain/like/ProductLikeModel.kt` | 좋아요 애그리거트. 자기 소유 값이 없는 연결 엔티티. 변경 메서드 없음 |
| `domain/like/ProductLikeRepository.kt` | 포트. 조건부 UPDATE 3종과 선조회, 목록 조회 |
| `domain/like/LikeService.kt` | 좋아요 애그리거트만 안다. **전이 여부를 `Boolean` 으로 반환** |
| `infrastructure/like/ProductLikeJpaRepository.kt` | JPQL 조건부 UPDATE 와 목록 쿼리 |
| `infrastructure/like/ProductLikeRepositoryImpl.kt` | 포트 구현. `PageQuery` → `PageRequest` 번역 |
| `application/like/LikeFacade.kt` | 회원·좋아요·상품 3개 애그리거트 조합. **트랜잭션 경계와 경합 예외 흡수** |
| `interfaces/api/ApiHeaders.kt` | 요청 헤더 이름 상수. 컨트롤러 3개가 공유 |
| `interfaces/api/like/ProductLikeV1ApiSpec.kt` | 등록·취소 API 문서 |
| `interfaces/api/like/ProductLikeV1Controller.kt` | `POST` / `DELETE` 진입점 |
| `interfaces/api/like/UserLikeV1ApiSpec.kt` | 목록 API 문서 |
| `interfaces/api/like/UserLikeV1Controller.kt` | `GET` 진입점 |
| `http/commerce-api/like-v1.http` | 수동 확인용 요청 파일 |

`LikeV1Dto.kt` 와 `ProductLikeCommand.kt` 는 **만들지 않는다** (설계 문서 5.3, 7.1 장).

### 수정

| 파일 | 변경 |
|---|---|
| `domain/product/LikeCount.kt` | 주석 교체. `increase()` / `decrease()` 를 만들지 않는 **새 근거**를 적는다 |
| `domain/product/ProductRepository.kt` | `increaseLikeCount` / `decreaseLikeCount` / `findAllByIds` 추가 |
| `domain/product/ProductService.kt` | 위 3개를 감싸는 유스케이스 + `deleteAllByBrandId` 반환을 `List<Long>` 으로 |
| `infrastructure/product/ProductJpaRepository.kt` | 조건부 UPDATE 2종 + ID 집합 조회 |
| `infrastructure/product/ProductRepositoryImpl.kt` | 새 계약 3개 위임 |
| `application/admin/product/ProductAdminFacade.kt` | `delete` 에 `@Transactional` + 좋아요 연쇄 삭제 |
| `application/admin/brand/BrandAdminFacade.kt` | 브랜드 → 상품 → 좋아요 2단계 연쇄 |
| `interfaces/api/user/UserV1Controller.kt` | `HEADER_LOGIN_ID` 를 `ApiHeaders.LOGIN_ID` 로 옮긴다 |
| `support/seed/LocalDataSeeder.kt` | 회원 3명 시드 추가 |

### 테스트

| 파일 | 대상 |
|---|---|
| `test/domain/like/ProductLikeModelTest.kt` | 생성자 검증 |
| `test/domain/like/ProductLikeModelPersistenceTest.kt` | 컬럼 매핑과 유니크 제약 |
| `test/domain/like/LikeServiceIntegrationTest.kt` | 전이 판정 |
| `test/application/like/LikeFacadeIntegrationTest.kt` | 등록·취소·목록 유스케이스 |
| `test/application/like/LikeFacadeConcurrencyTest.kt` | **동시성 회귀 방어선** |
| `test/interfaces/api/ProductLikeV1ApiE2ETest.kt` | 등록·취소 API |
| `test/interfaces/api/UserLikeV1ApiE2ETest.kt` | 목록 API |
| `test/domain/product/ProductServiceIntegrationTest.kt` (보강) | `like_count` 증감 |
| `test/application/admin/product/ProductAdminFacadeIntegrationTest.kt` (보강) | 상품 삭제 연쇄 |
| `test/application/admin/brand/BrandAdminFacadeIntegrationTest.kt` (보강) | 2단계 연쇄 |

## Task 순서와 의존

```
Task 1  ProductLikeModel                      (독립)
Task 2  좋아요 저장소와 전이 판정               ← 1
Task 3  like_count 원자적 증감                 (독립, 2 와 병행 가능)
Task 4  LikeFacade 등록·취소                   ← 2, 3
Task 5  동시성 회귀 테스트                      ← 4
Task 6  좋아요 목록 조회                        ← 4
Task 7  상품 삭제 연쇄                          ← 2, 6
Task 8  등록·취소 API                          ← 4
Task 9  목록 API                               ← 6
Task 10 시드 회원과 .http                       ← 8, 9
```

Task 5 는 Task 4 의 회귀 테스트이므로 **건너뛰지 않는다.** 이 테스트가 없으면 Task 2·3·4 의 설계가 조용히 되돌려질 수 있다 (설계 문서 10.3 장).

---

## Task 1: `ProductLikeModel` 과 매핑

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeModel.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/like/ProductLikeModelTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/like/ProductLikeModelPersistenceTest.kt`

**Interfaces:**
- Consumes: `com.loopers.domain.BaseEntity`, `CoreException`, `ErrorType` (모두 기존)
- Produces: `ProductLikeModel.create(userId: Long, productId: Long): ProductLikeModel`, 읽기 프로퍼티 `userId: Long` / `productId: Long`, 그리고 `BaseEntity` 가 주는 `id` / `createdAt` / `updatedAt` / `deletedAt`

---

- [ ] **Step 1: 실패하는 단위 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/like/ProductLikeModelTest.kt`

```kotlin
package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductLikeModelTest {
    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("회원 ID 와 상품 ID 가 모두 양수면, 생성된다.")
        @Test
        fun creates_whenBothIdsArePositive() {
            // act
            val like = ProductLikeModel.create(userId = 1L, productId = 2L)

            // assert
            assertAll(
                { assertThat(like.userId).isEqualTo(1L) },
                { assertThat(like.productId).isEqualTo(2L) },
                { assertThat(like.deletedAt).isNull() },
            )
        }

        @DisplayName("회원 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenUserIdIsNotPositive(userId: Long) {
            // act
            val result = assertThrows<CoreException> { ProductLikeModel.create(userId = userId, productId = 1L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenProductIdIsNotPositive(productId: Long) {
            // act
            val result = assertThrows<CoreException> { ProductLikeModel.create(userId = 1L, productId = productId) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.ProductLikeModelTest"`
Expected: 컴파일 실패 — `Unresolved reference: like` / `ProductLikeModel`

- [ ] **Step 3: 엔티티를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeModel.kt`

```kotlin
package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 회원이 상품에 건 좋아요.
 *
 * 회원과 상품을 객체가 아닌 식별자로 참조한다. (설계 문서 5.2 장)
 * 그 결과 이 엔티티는 자기 소유의 값을 하나도 갖지 않는 순수 연결 엔티티다.
 *
 * 변경 메서드가 없는 것은 의도적이다. 이 애그리거트의 유일한 상태 변화는 deletedAt 의 on/off 인데,
 * 그것을 엔티티 메서드로 하면 "읽고 → 판단하고 → 쓰기" 사이의 틈에서 갱신 손실이 발생한다. (설계 문서 6.2 장)
 * 따라서 이 클래스가 상태를 바꾸는 경로는 INSERT 하나뿐이고,
 * 취소와 부활은 저장소의 조건부 UPDATE 가 담당한다.
 */
@Entity
@Table(
    name = "product_likes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_product_likes_user_product", columnNames = ["user_id", "product_id"]),
    ],
)
class ProductLikeModel private constructor(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    init {
        // 두 값 모두 다른 애그리거트의 식별자라 값 객체가 아니므로, 이 검증만 애그리거트가 직접 한다.
        // ProductModel 이 brandId 를 다루는 방식과 같다.
        if (userId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "회원 ID 는 양수여야 합니다.")
        }
        if (productId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.")
        }
    }

    companion object {
        fun create(userId: Long, productId: Long): ProductLikeModel =
            ProductLikeModel(userId = userId, productId = productId)
    }
}
```

- [ ] **Step 4: 단위 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.ProductLikeModelTest"`
Expected: PASS (5건)

- [ ] **Step 5: 매핑 테스트를 쓴다**

유니크 제약이 **소프트 삭제된 행에도 걸린다**는 것이 설계의 전제다 (설계 문서 5.4 장).
이것이 깨지면 6장의 "부활" 설계가 통째로 무너지므로 테스트로 못 박는다.

`apps/commerce-api/src/test/kotlin/com/loopers/domain/like/ProductLikeModelPersistenceTest.kt`

```kotlin
package com.loopers.domain.like

import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@SpringBootTest
class ProductLikeModelPersistenceTest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요를 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("컬럼으로 저장되고 다시 읽을 때 복원된다.")
        @Transactional
        @Test
        fun persistsColumns_andRestoresThem() {
            // arrange
            val like = ProductLikeModel.create(userId = 7L, productId = 9L)

            // act
            entityManager.persist(like)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(ProductLikeModel::class.java, like.id)

            // assert
            assertAll(
                { assertThat(found.id).isPositive() },
                { assertThat(found.userId).isEqualTo(7L) },
                { assertThat(found.productId).isEqualTo(9L) },
                { assertThat(found.createdAt).isNotNull() },
                { assertThat(found.updatedAt).isNotNull() },
                { assertThat(found.deletedAt).isNull() },
            )
        }

        @DisplayName("같은 회원과 상품 조합이 이미 있으면, 유니크 제약에 걸린다.")
        @Test
        fun violatesUniqueConstraint_whenSamePairIsInsertedTwice() {
            // arrange
            saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L))

            // act & assert
            assertThatThrownBy { saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L)) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        /**
         * 소프트 삭제된 행도 유니크 제약에 포함된다는 것이 부활 설계의 전제다. (설계 문서 5.4 장)
         * 이 단언이 깨지면 취소 후 재좋아요를 INSERT 로 처리해도 되어버려 6장 전체가 흔들린다.
         */
        @DisplayName("소프트 삭제된 행이 있어도, 같은 조합의 새 행은 유니크 제약에 걸린다.")
        @Test
        fun violatesUniqueConstraint_evenWhenExistingRowIsSoftDeleted() {
            // arrange
            val like = saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L))
            softDeleteInNewTransaction(like.id)

            // act & assert
            assertThatThrownBy { saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L)) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }
    }

    /**
     * 제약 위반은 flush 시점에 터지고 그 트랜잭션을 오염시키므로, 각 저장을 독립 트랜잭션으로 분리한다.
     * 테스트 메서드에 @Transactional 을 붙이면 첫 위반 이후의 단언이 전부 무의미해진다.
     */
    private fun saveInNewTransaction(like: ProductLikeModel): ProductLikeModel =
        transactionTemplate.execute {
            entityManager.persist(like)
            entityManager.flush()
            like
        }!!

    private fun softDeleteInNewTransaction(id: Long) {
        transactionTemplate.execute {
            entityManager.createQuery("UPDATE ProductLikeModel l SET l.deletedAt = :now WHERE l.id = :id")
                .setParameter("now", ZonedDateTime.now())
                .setParameter("id", id)
                .executeUpdate()
        }
    }
}
```

> **`TransactionTemplate` 은 스프링 부트가 자동 설정하는 빈이다.**
> `PlatformTransactionManager` 후보가 하나뿐일 때 `TransactionAutoConfiguration` 이 등록하며,
> 이 프로젝트는 `modules/jpa` 에 커스텀 트랜잭션 매니저가 없어 조건을 만족한다.
> 이 테스트가 `NoSuchBeanDefinitionException` 으로 실패하면 Task 4 도 같은 이유로 실패하므로,
> 그때는 `config/` 에 `TransactionTemplate(transactionManager)` 를 반환하는 `@Bean` 을 하나 추가한다.

import 에 다음 두 줄이 필요하다.

```kotlin
import org.springframework.transaction.support.TransactionTemplate
```

- [ ] **Step 6: 매핑 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.ProductLikeModelPersistenceTest"`
Expected: PASS (3건)

`ddl-auto: create` 이므로 `product_likes` 테이블은 기동 시 자동 생성된다. 마이그레이션 파일은 없다.

- [ ] **Step 7: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeModel.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/like/
git commit -m "feat : 좋아요 애그리거트 ProductLikeModel 추가

회원과 상품을 식별자로만 참조하는 연결 엔티티다.
변경 메서드를 두지 않는 이유는 deletedAt 을 엔티티 메서드로 바꾸면
갱신 손실이 발생하기 때문이다. 상태 전이는 저장소의 조건부 UPDATE 가 맡는다.

소프트 삭제된 행도 유니크 제약에 포함된다는 사실을 매핑 테스트로 못 박는다.
이 성질이 취소 후 재좋아요를 INSERT 가 아닌 부활로 처리하는 근거다."
```

---

## Task 2: 좋아요 저장소와 전이 판정

이 Task 의 핵심은 **`LikeService` 가 `Boolean` 을 반환한다**는 것이다. 그 `Boolean` 이 "좋아요 수를 움직여도 되는가" 의 유일한 근거가 된다.

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/LikeService.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/like/LikeServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProductLikeModel.create(userId, productId)` (Task 1)
- Produces:
  - `ProductLikeRepository.save(productLike: ProductLikeModel): ProductLikeModel`
  - `ProductLikeRepository.findIncludingDeleted(userId: Long, productId: Long): ProductLikeModel?`
  - `ProductLikeRepository.restore(userId: Long, productId: Long, now: ZonedDateTime): Int`
  - `ProductLikeRepository.softDelete(userId: Long, productId: Long, now: ZonedDateTime): Int`
  - `LikeService.like(userId: Long, productId: Long): Boolean`
  - `LikeService.unlike(userId: Long, productId: Long): Boolean`

---

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/like/LikeServiceIntegrationTest.kt`

```kotlin
package com.loopers.domain.like

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class LikeServiceIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val productLikeRepository: ProductLikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val USER_ID = 1L
        private const val PRODUCT_ID = 2L
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun findLike(): ProductLikeModel? =
        productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID)

    @DisplayName("좋아요를 걸 때, ")
    @Nested
    inner class Like {
        @DisplayName("행이 없으면, 저장되고 전이했다고 보고한다.")
        @Test
        fun savesAndReportsTransition_whenRowDoesNotExist() {
            // act
            val transitioned = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            val found = findLike()
            assertAll(
                { assertThat(transitioned).isTrue() },
                { assertThat(found).isNotNull() },
                { assertThat(found?.deletedAt).isNull() },
            )
        }

        @DisplayName("취소된 행이 있으면, 새 행을 만들지 않고 되살리며 전이했다고 보고한다.")
        @Test
        fun restoresExistingRow_whenRowIsSoftDeleted() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            val originalId = findLike()!!.id
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // act
            val transitioned = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            val found = findLike()
            assertAll(
                { assertThat(transitioned).isTrue() },
                { assertThat(found?.deletedAt).isNull() },
                // 새 행이 생기지 않았다는 것이 부활 설계의 요체다. INSERT 로 처리하면 유니크 제약에 걸린다.
                { assertThat(found?.id).isEqualTo(originalId) },
            )
        }

        @DisplayName("이미 좋아요 상태면, 아무것도 바꾸지 않고 전이하지 않았다고 보고한다.")
        @Test
        fun reportsNoTransition_whenAlreadyLiked() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            val before = findLike()!!

            // act
            val transitioned = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            val after = findLike()!!
            assertAll(
                { assertThat(transitioned).isFalse() },
                { assertThat(after.id).isEqualTo(before.id) },
                { assertThat(after.updatedAt).isEqualTo(before.updatedAt) },
            )
        }

        /**
         * updatedAt 을 SET 절에서 빠뜨리면 이 단언이 실패한다.
         * JPQL 벌크 UPDATE 는 PreUpdate 콜백을 타지 않으므로 손으로 써야 한다.
         */
        @DisplayName("되살릴 때, updatedAt 이 갱신된다.")
        @Test
        fun refreshesUpdatedAt_whenRestoring() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)
            val beforeRestore = findLike()!!.updatedAt

            // act
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertThat(findLike()!!.updatedAt).isAfter(beforeRestore)
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    inner class Unlike {
        @DisplayName("좋아요 상태면, deletedAt 이 채워지고 전이했다고 보고한다.")
        @Test
        fun softDeletesAndReportsTransition_whenLiked() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // act
            val transitioned = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(transitioned).isTrue() },
                { assertThat(findLike()?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("이미 취소된 상태면, 전이하지 않았다고 보고한다.")
        @Test
        fun reportsNoTransition_whenAlreadyUnliked() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)
            val before = findLike()!!.deletedAt

            // act
            val transitioned = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(transitioned).isFalse() },
                // 취소 시각이 덮어씌워지지 않아야 한다. 덮어씌워지면 조건절이 빠진 것이다.
                { assertThat(findLike()?.deletedAt).isEqualTo(before) },
            )
        }

        @DisplayName("행이 아예 없으면, 예외 없이 전이하지 않았다고 보고한다.")
        @Test
        fun reportsNoTransition_whenRowDoesNotExist() {
            // act
            val transitioned = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(transitioned).isFalse() },
                { assertThat(findLike()).isNull() },
            )
        }
    }

    @DisplayName("좋아요 행을 조회할 때, ")
    @Nested
    inner class FindIncludingDeleted {
        @DisplayName("취소된 행도 반환한다.")
        @Test
        fun returnsSoftDeletedRow() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // act
            val found = productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(found).isNotNull() },
                { assertThat(found?.deletedAt).isNotNull() },
                { assertThat(found?.deletedAt).isBefore(ZonedDateTime.now()) },
            )
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.LikeServiceIntegrationTest"`
Expected: 컴파일 실패 — `Unresolved reference: LikeService`, `ProductLikeRepository`

- [ ] **Step 3: 포트를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeRepository.kt`

```kotlin
package com.loopers.domain.like

import java.time.ZonedDateTime

/**
 * 좋아요 저장소.
 *
 * restore 와 softDelete 가 엔티티를 받지 않고 식별자와 시각을 받는 것이 이 인터페이스의 핵심이다.
 * 엔티티를 읽어 상태를 바꾸면 읽기와 쓰기 사이의 틈에서 갱신 손실이 생기므로 (설계 문서 6.2 장),
 * 두 연산은 조건을 WHERE 절에 담은 단일 UPDATE 여야 한다.
 * 그래서 반환이 Unit 이 아니라 영향 행 수이며, 그 숫자가 전이 여부의 유일한 근거다.
 */
interface ProductLikeRepository {
    fun save(productLike: ProductLikeModel): ProductLikeModel

    /**
     * 삭제 여부와 무관하게 조회한다. 등록 경로의 선조회 전용이다.
     * "행이 없다" 와 "취소된 행이 있다" 를 구분해야 하므로 삭제된 행도 보아야 한다.
     */
    fun findIncludingDeleted(userId: Long, productId: Long): ProductLikeModel?

    /** 취소된 좋아요를 되살린다. 이미 살아 있으면 아무것도 바꾸지 않는다. 반환값은 영향 행 수다. */
    fun restore(userId: Long, productId: Long, now: ZonedDateTime): Int

    /** 살아 있는 좋아요를 취소한다. 이미 취소됐거나 행이 없으면 아무것도 바꾸지 않는다. 반환값은 영향 행 수다. */
    fun softDelete(userId: Long, productId: Long, now: ZonedDateTime): Int
}
```

- [ ] **Step 4: JPA 어댑터를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeJpaRepository.kt`

```kotlin
package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface ProductLikeJpaRepository : JpaRepository<ProductLikeModel, Long> {
    /** 삭제 필터가 없는 조회다. 이름에 DeletedAt 조건이 없다는 것이 그 의미다. */
    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLikeModel?

    /**
     * clearAutomatically 를 켜는 이유는 직전 선조회로 1차 캐시에 올라온 엔티티가
     * 이 UPDATE 를 반영하지 못한 채 남기 때문이다. 같은 트랜잭션에서 다시 읽으면 낡은 deletedAt 을 본다.
     * flushAutomatically 는 반대 방향의 보호다 — 아직 flush 되지 않은 변경이 이 UPDATE 뒤로 밀리지 않게 한다.
     *
     * updatedAt 을 SET 절에 직접 쓰는 이유는 JPQL 벌크 연산이 PreUpdate 콜백을 타지 않기 때문이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductLikeModel l
           SET l.deletedAt = null, l.updatedAt = :now
         WHERE l.userId = :userId AND l.productId = :productId AND l.deletedAt IS NOT NULL
        """,
    )
    fun restore(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
        @Param("now") now: ZonedDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductLikeModel l
           SET l.deletedAt = :now, l.updatedAt = :now
         WHERE l.userId = :userId AND l.productId = :productId AND l.deletedAt IS NULL
        """,
    )
    fun softDelete(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
        @Param("now") now: ZonedDateTime,
    ): Int
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeRepositoryImpl.kt`

```kotlin
package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeModel
import com.loopers.domain.like.ProductLikeRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class ProductLikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun save(productLike: ProductLikeModel): ProductLikeModel {
        return productLikeJpaRepository.save(productLike)
    }

    override fun findIncludingDeleted(userId: Long, productId: Long): ProductLikeModel? {
        return productLikeJpaRepository.findByUserIdAndProductId(userId = userId, productId = productId)
    }

    override fun restore(userId: Long, productId: Long, now: ZonedDateTime): Int {
        return productLikeJpaRepository.restore(userId = userId, productId = productId, now = now)
    }

    override fun softDelete(userId: Long, productId: Long, now: ZonedDateTime): Int {
        return productLikeJpaRepository.softDelete(userId = userId, productId = productId, now = now)
    }
}
```

- [ ] **Step 5: 도메인 서비스를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/like/LikeService.kt`

```kotlin
package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 좋아요 애그리거트의 유스케이스.
 *
 * 이 서비스는 상품도 회원도 모른다. 좋아요 수를 움직이는 것은 이 애그리거트의 일이 아니며,
 * 두 애그리거트를 잇는 책임은 LikeFacade 에만 있다. (설계 문서 3.2 장)
 */
@Component
class LikeService(
    private val productLikeRepository: ProductLikeRepository,
) {
    /**
     * 좋아요를 건다. 반환값은 "이 호출이 상태를 바꿨는가" 다.
     *
     * false 는 실패가 아니라 "이미 좋아요 상태였다" 는 뜻이다.
     * 호출자는 이 값이 true 일 때만 좋아요 수를 올려야 한다. 아니면 중복 요청마다 수가 늘어난다.
     *
     * 선조회를 두는 이유는 성능이 아니다. 없으면 흔한 더블클릭이 매번 유니크 제약 위반 예외를 일으켜
     * 정상 동작이 예외 경로를 타게 된다. 동시 최초 좋아요에서 두 요청이 모두 "행 없음" 을 보는 경우는
     * 여전히 남으며, 그때 진 쪽의 예외는 LikeFacade 가 흡수한다. (설계 문서 6.6, 6.8 장)
     */
    @Transactional
    fun like(userId: Long, productId: Long): Boolean {
        val existing = productLikeRepository.findIncludingDeleted(userId = userId, productId = productId)

        return when {
            existing == null -> {
                productLikeRepository.save(ProductLikeModel.create(userId = userId, productId = productId))
                true
            }

            existing.deletedAt != null ->
                productLikeRepository.restore(userId = userId, productId = productId, now = ZonedDateTime.now()) == 1

            else -> false
        }
    }

    /**
     * 좋아요를 취소한다. 반환값은 "이 호출이 상태를 바꿨는가" 다.
     *
     * 등록과 달리 선조회가 없다. INSERT 가 없어 유니크 제약 위반이 발생할 수 없고,
     * 조건부 UPDATE 한 문장이 판정과 전이를 동시에 끝낸다. (설계 문서 6.7 장)
     */
    @Transactional
    fun unlike(userId: Long, productId: Long): Boolean {
        return productLikeRepository.softDelete(userId = userId, productId = productId, now = ZonedDateTime.now()) == 1
    }
}
```

- [ ] **Step 6: 통합 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.LikeServiceIntegrationTest"`
Expected: PASS (8건). Docker 가 떠 있어야 한다.

- [ ] **Step 7: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/like/LikeServiceIntegrationTest.kt
git commit -m "feat : 좋아요 저장소와 전이 판정 추가

취소와 부활을 조건부 UPDATE 로 처리하고 영향 행 수로 전이를 판정한다.
엔티티를 읽어 delete()/restore() 를 부르면 동시 요청 두 건이 모두
'내가 상태를 바꿨다' 고 판단해 좋아요 수가 2 만큼 움직인다.

LikeService 가 Boolean 을 반환하는 것이 이 설계의 계약이다.
그 값이 true 일 때만 호출자가 좋아요 수를 건드릴 수 있다."
```

---

## Task 3: `like_count` 원자적 증감

Task 2 와 의존이 없으므로 병행할 수 있다.

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/LikeCount.kt` (주석만)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` (보강)

**Interfaces:**
- Consumes: 기존 `ProductRepository` / `ProductService` / `ProductModel`
- Produces:
  - `ProductRepository.increaseLikeCount(productId: Long): Int`
  - `ProductRepository.decreaseLikeCount(productId: Long): Int`
  - `ProductService.increaseLikeCount(productId: Long)` — 반환 없음
  - `ProductService.decreaseLikeCount(productId: Long)` — 반환 없음

---

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`ProductServiceIntegrationTest.kt` 의 마지막 `inner class` 뒤에 다음 `@Nested` 블록을 추가한다.
기존 클래스에 이미 있는 헬퍼(`saveProduct` 등)와 `@AfterEach` 는 그대로 쓴다.
헬퍼 이름이 다르면 그 파일의 기존 헬퍼에 맞춘다.

```kotlin
    @DisplayName("좋아요 수를 증감할 때, ")
    @Nested
    inner class ChangeLikeCount {
        @DisplayName("증가시키면, 1 늘어난다.")
        @Test
        fun increasesByOne() {
            // arrange
            val product = saveProduct(likeCount = 3)

            // act
            productService.increaseLikeCount(product.id)

            // assert
            assertThat(productService.getProduct(product.id)?.likeCount).isEqualTo(LikeCount(4))
        }

        @DisplayName("감소시키면, 1 줄어든다.")
        @Test
        fun decreasesByOne() {
            // arrange
            val product = saveProduct(likeCount = 3)

            // act
            productService.decreaseLikeCount(product.id)

            // assert
            assertThat(productService.getProduct(product.id)?.likeCount).isEqualTo(LikeCount(2))
        }

        /**
         * WHERE 절의 like_count > 0 가드가 빠지면 이 단언이 실패한다.
         * 음수가 저장되면 다음 조회에서 LikeCount 생성자가 터진다.
         */
        @DisplayName("0 인 상품을 감소시켜도, 음수가 되지 않는다.")
        @Test
        fun doesNotGoNegative_whenCountIsZero() {
            // arrange
            val product = saveProduct(likeCount = 0)

            // act
            productService.decreaseLikeCount(product.id)

            // assert
            assertThat(productService.getProduct(product.id)?.likeCount).isEqualTo(LikeCount(0))
        }

        @DisplayName("삭제된 상품이면, 증가하지 않는다.")
        @Test
        fun doesNotIncrease_whenProductIsSoftDeleted() {
            // arrange
            val product = saveProduct(likeCount = 3)
            productService.delete(product.id)

            // act
            productService.increaseLikeCount(product.id)

            // assert
            assertThat(productService.getProductIncludingDeleted(product.id)?.likeCount).isEqualTo(LikeCount(3))
        }

        @DisplayName("존재하지 않는 상품이면, 예외 없이 아무 일도 일어나지 않는다.")
        @Test
        fun doesNothing_whenProductDoesNotExist() {
            // act & assert — 예외가 나지 않는 것이 단언이다
            productService.increaseLikeCount(99999L)
            productService.decreaseLikeCount(99999L)
        }
    }
```

기존 파일에 `saveProduct(likeCount = ...)` 헬퍼가 없다면 클래스 상단에 추가한다.

```kotlin
    private fun saveProduct(brandId: Long = 1L, price: Long = 10_000, likeCount: Long = 0): ProductModel =
        productRepository.save(
            ProductModel.create(
                brandId = brandId,
                name = ProductName("상품"),
                price = Price(price),
                likeCount = LikeCount(likeCount),
            ),
        )
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"`
Expected: 컴파일 실패 — `Unresolved reference: increaseLikeCount`

- [ ] **Step 3: 포트에 계약을 추가한다**

`ProductRepository.kt` 의 인터페이스 본문 끝에 추가한다.

```kotlin
    /**
     * 좋아요 수를 1 늘린다. 반환값은 영향 행 수다.
     *
     * 읽어서 더한 값을 쓰는 대신 DB 안에서 증분하므로 동시 갱신에서 손실이 발생하지 않는다. (설계 문서 6.4 장)
     * 삭제된 상품은 대상이 아니다.
     */
    fun increaseLikeCount(productId: Long): Int

    /**
     * 좋아요 수를 1 줄인다. 이미 0 이면 아무것도 바꾸지 않는다. 반환값은 영향 행 수다.
     *
     * 0 이 반환되면 좋아요 행과 카운트가 어긋났다는 뜻이다. 그것을 어떻게 볼지는 호출자가 정한다.
     */
    fun decreaseLikeCount(productId: Long): Int
```

- [ ] **Step 4: JPQL 조건부 UPDATE 를 추가한다**

`ProductJpaRepository.kt` 에 추가한다. import 세 개(`Modifying`, `Query`, `Param`)가 새로 필요하다.

```kotlin
    /**
     * products 의 updated_at 은 건드리지 않는다. (설계 문서 6.4 장)
     * 좋아요는 상품을 편집한 것이 아니라 비정규화된 카운터를 움직인 것이므로,
     * 여기서 타임스탬프를 밀면 어드민 목록에서 아무도 수정하지 않은 상품이 계속 "방금 수정됨" 으로 보인다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductModel p
           SET p.likeCount.value = p.likeCount.value + 1
         WHERE p.id = :productId AND p.deletedAt IS NULL
        """,
    )
    fun increaseLikeCount(@Param("productId") productId: Long): Int

    /** like_count 가 0 보다 클 때만 줄인다. 이 조건이 음수 방지의 실질적 책임자다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductModel p
           SET p.likeCount.value = p.likeCount.value - 1
         WHERE p.id = :productId AND p.deletedAt IS NULL AND p.likeCount.value > 0
        """,
    )
    fun decreaseLikeCount(@Param("productId") productId: Long): Int
```

> `p.likeCount.value` 는 `@Embedded` 값 객체 안으로 들어가는 JPQL 경로다.
> Hibernate 가 이 경로를 거부하면(`Could not resolve attribute`) 같은 두 메서드를
> `nativeQuery = true` 와 `UPDATE products SET like_count = like_count + 1 WHERE id = :productId AND deleted_at IS NULL`
> 로 바꾼다. 동작과 계약은 동일하다.

- [ ] **Step 5: 어댑터에 위임을 추가한다**

`ProductRepositoryImpl.kt` 에 추가한다.

```kotlin
    override fun increaseLikeCount(productId: Long): Int {
        return productJpaRepository.increaseLikeCount(productId)
    }

    override fun decreaseLikeCount(productId: Long): Int {
        return productJpaRepository.decreaseLikeCount(productId)
    }
```

- [ ] **Step 6: 도메인 서비스에 유스케이스를 추가한다**

`ProductService.kt` 에 로거 필드와 두 메서드를 추가한다.

```kotlin
    private val log = LoggerFactory.getLogger(ProductService::class.java)

    /**
     * 좋아요 수를 1 늘린다.
     *
     * 반환값을 두지 않는 이유는 호출자가 할 수 있는 일이 없기 때문이다.
     * 호출 직전에 상품 존재를 확인했으므로 0 행은 "확인과 갱신 사이에 상품이 삭제됐다" 는 드문 경우이고,
     * 그것 때문에 사용자의 좋아요 요청을 실패시킬 이유가 없다. 기록만 남긴다.
     */
    @Transactional
    fun increaseLikeCount(productId: Long) {
        if (productRepository.increaseLikeCount(productId) == 0) {
            log.warn("좋아요 수 증가 실패 : productId={} — 상품이 없거나 삭제되었습니다.", productId)
        }
    }

    /**
     * 좋아요 수를 1 줄인다.
     *
     * 0 행은 정합성 붕괴 신호다 — 좋아요 행은 살아 있었는데 카운트가 이미 0 이라는 뜻이다.
     * 그래도 예외를 던지지 않는다. 사용자의 취소는 이미 정상 완료됐고,
     * 어긋난 카운트를 이유로 그 요청을 실패시킬 근거가 없다. (설계 문서 6.4 장)
     */
    @Transactional
    fun decreaseLikeCount(productId: Long) {
        if (productRepository.decreaseLikeCount(productId) == 0) {
            log.warn(
                "좋아요 수 감소 실패 : productId={} — 카운트가 이미 0 이거나 상품이 삭제되었습니다. 정합성 확인이 필요합니다.",
                productId,
            )
        }
    }
```

`import org.slf4j.LoggerFactory` 를 추가한다.

- [ ] **Step 7: 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"`
Expected: PASS — 기존 케이스 전부 + 새 5건

- [ ] **Step 8: `LikeCount` 의 주석을 교체한다**

`LikeCount.kt` 의 KDoc 을 통째로 바꾼다. 코드는 한 글자도 건드리지 않는다.

```kotlin
/**
 * 상품의 좋아요 수. 정렬을 위해 상품에 비정규화해 둔 값이다.
 *
 * increase() / decrease() 를 두지 않는 것은 여전히 의도적이며, 이유가 바뀌었다.
 * 예전 이유는 "값을 바꾸는 유스케이스가 아직 없다" 였지만 좋아요 기능이 생겨 그것은 더 이상 참이 아니다.
 *
 * 지금의 이유는 이렇다. 증감은 원자적이어야 하고, 원자성은 메모리 안의 객체가 표현할 수 없는 성질이다.
 * increase() 를 만들면 그것을 쓰는 코드가 반드시 "읽고 → 더하고 → 쓰기" 가 되어,
 * 동시 요청 두 건이 같은 값을 읽고 같은 값을 쓰는 갱신 손실로 돌아간다.
 * 실제 증감은 ProductRepository 의 원자적 UPDATE 가 하며, 음수 방지는 그 쿼리의 WHERE 절이 맡는다.
 *
 * 따라서 이 값 객체의 역할은 런타임 방어에서 읽기 측 계약으로 바뀌었다 —
 * 조회된 값이 0 이상임을 보장하고, 어떤 경로가 그것을 깨면 조회 시점에 터져서 침묵하지 않게 한다.
 * (설계 문서 2026-08-20-product-like-design.md 6.5 장)
 */
```

- [ ] **Step 9: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
git commit -m "feat : 좋아요 수 원자적 증감 경로 추가

UPDATE products SET like_count = like_count ± 1 로 DB 안에서 증분한다.
읽어서 더한 값을 쓰지 않으므로 갱신 손실이 원리적으로 발생하지 않는다.
감소는 WHERE like_count > 0 으로 음수를 막고, 영향 행 수 0 은 정합성 붕괴 신호로 로그에 남긴다.

LikeCount 의 주석을 교체한다. increase()/decrease() 를 만들지 않는 이유가
'유스케이스가 없다' 에서 '원자성은 객체가 표현할 수 없다' 로 바뀌었다."
```

---

## Task 4: `LikeFacade` — 등록과 취소

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeIntegrationTest.kt`

**Interfaces:**
- Consumes: `LikeService.like/unlike(userId, productId): Boolean` (Task 2), `ProductService.increaseLikeCount/decreaseLikeCount(productId)` (Task 3), 기존 `UserService.getUser(loginId: LoginId): UserModel?`, `ProductService.getProduct(id: Long): ProductModel?`
- Produces:
  - `LikeFacade.like(loginId: LoginId, productId: Long)` — 반환 없음
  - `LikeFacade.unlike(loginId: LoginId, productId: Long)` — 반환 없음

---

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeIntegrationTest.kt`

```kotlin
package com.loopers.application.like

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = "loopers01"): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    private fun saveProduct(likeCount: Long = 0): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )
    }

    private fun likeCountOf(productId: Long): Long =
        productRepository.findById(productId)!!.likeCount.value

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    inner class Like {
        @DisplayName("좋아요 수가 1 늘어난다.")
        @Test
        fun increasesLikeCountByOne() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)

            // act
            likeFacade.like(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(6L)
        }

        /**
         * 이 단언이 이 기능의 핵심이다. 중복 등록이 수를 올리면 사용자가 버튼을 두 번 눌러
         * 좋아요 수를 마음대로 부풀릴 수 있다.
         */
        @DisplayName("이미 좋아요한 상품이면, 예외 없이 수가 그대로다.")
        @Test
        fun keepsLikeCount_whenAlreadyLiked() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)

            // act
            likeFacade.like(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(6L)
        }

        @DisplayName("취소한 뒤 다시 등록하면, 수가 정확히 복구된다.")
        @Test
        fun restoresLikeCount_whenLikedAgainAfterUnlike() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)
            likeFacade.unlike(user.loginId, product.id)

            // act
            likeFacade.like(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(6L)
        }

        @DisplayName("서로 다른 회원이 같은 상품을 좋아요하면, 각각 1 씩 늘어난다.")
        @Test
        fun countsEachUserSeparately() {
            // arrange
            val first = signUp("loopers01")
            val second = signUp("loopers02")
            val product = saveProduct(likeCount = 0)

            // act
            likeFacade.like(first.loginId, product.id)
            likeFacade.like(second.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(2L)
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = signUp()

            // act
            val result = assertThrows<CoreException> { likeFacade.like(user.loginId, 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductIsSoftDeleted() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            product.delete()
            productRepository.save(product)

            // act
            val result = assertThrows<CoreException> { likeFacade.like(user.loginId, product.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProduct()

            // act
            val result = assertThrows<CoreException> { likeFacade.like(LoginId("nobody"), product.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    inner class Unlike {
        @DisplayName("좋아요 수가 1 줄어든다.")
        @Test
        fun decreasesLikeCountByOne() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)

            // act
            likeFacade.unlike(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(5L)
        }

        @DisplayName("좋아요하지 않은 상품이면, 예외 없이 수가 그대로다.")
        @Test
        fun keepsLikeCount_whenNotLiked() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)

            // act
            likeFacade.unlike(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(5L)
        }

        @DisplayName("이미 취소한 상품을 다시 취소해도, 수가 더 줄지 않는다.")
        @Test
        fun keepsLikeCount_whenUnlikedTwice() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)
            likeFacade.unlike(user.loginId, product.id)

            // act
            likeFacade.unlike(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(5L)
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = signUp()

            // act
            val result = assertThrows<CoreException> { likeFacade.unlike(user.loginId, 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
```

> `UserCommand.SignUp` 의 필드 이름과 타입이 위와 다르면 `domain/user/UserCommand.kt` 를 열어 실제 시그니처에 맞춘다.
> `UserModel.loginId` 의 타입은 `LoginId` 다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeIntegrationTest"`
Expected: 컴파일 실패 — `Unresolved reference: LikeFacade`

- [ ] **Step 3: 파사드를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt`

```kotlin
package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 회원 · 좋아요 · 상품 세 애그리거트를 조합하는 유스케이스.
 *
 * 이 클래스에 @Transactional 이 없는 것은 실수가 아니다.
 * 동시 최초 좋아요에서 진 쪽은 유니크 제약 위반을 맞는데, 그 예외를 @Transactional 메서드 안에서 잡으면
 * 트랜잭션이 이미 rollback-only 로 마킹되어 커밋할 수 없다. 예외는 트랜잭션 경계 밖에서 잡아야 한다.
 *
 * TransactionTemplate 을 쓰면 트랜잭션의 시작과 끝이 execute 블록으로 눈에 보이고,
 * catch 가 그 블록 밖에 있다는 사실이 문법으로 드러난다.
 * 클래스를 "얇은 래퍼 + @Transactional 컴포넌트" 로 쪼개면 왜 나뉘어 있는지가 어디에도 남지 않아,
 * 나중에 누군가 합치는 순간 이 예외 흡수가 조용히 동작을 멈춘다. (설계 문서 6.9 장)
 */
@Component
class LikeFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val likeService: LikeService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(LikeFacade::class.java)

    fun like(loginId: LoginId, productId: Long) {
        try {
            transactionTemplate.execute { doLike(loginId, productId) }
        } catch (e: DataIntegrityViolationException) {
            // 동시 최초 좋아요 경합에서 진 쪽이다. 이긴 쪽이 이미 행과 카운트를 확정했으므로
            // 이 트랜잭션이 통째로 롤백된 최종 상태가 정확하다. 클라이언트에게는 성공이다. (설계 문서 6.8 장)
            log.debug("좋아요 경합 패배 : loginId={}, productId={}", loginId.value, productId, e)
        }
    }

    /**
     * 취소에는 try 가 없다. INSERT 가 없어 유니크 제약 위반이 발생할 수 없기 때문이다.
     * 없는 위험을 방어하는 catch 는 나중에 진짜 예외를 삼킨다.
     */
    fun unlike(loginId: LoginId, productId: Long) {
        transactionTemplate.execute { doUnlike(loginId, productId) }
    }

    private fun doLike(loginId: LoginId, productId: Long) {
        val user = getUserOrThrow(loginId)
        requireProductExists(productId)

        // 전이했을 때만 수를 올린다. 이 조건이 중복 등록의 멱등성을 만든다.
        if (likeService.like(userId = user.id, productId = productId)) {
            productService.increaseLikeCount(productId)
        }
    }

    private fun doUnlike(loginId: LoginId, productId: Long) {
        val user = getUserOrThrow(loginId)
        requireProductExists(productId)

        if (likeService.unlike(userId = user.id, productId = productId)) {
            productService.decreaseLikeCount(productId)
        }
    }

    private fun getUserOrThrow(loginId: LoginId): UserModel =
        userService.getUser(loginId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[loginId = ${loginId.value}] 존재하지 않는 회원입니다.",
            )

    /**
     * 삭제된 상품도 404 다. 미등록과 소프트 삭제를 구분하지 않는 것은 ProductFacade.getProduct 와 같은 판단이다.
     * 반환값을 쓰지 않으므로 이름을 require 로 두어 "존재 확인이 목적" 임을 드러낸다.
     */
    private fun requireProductExists(productId: Long) {
        productService.getProduct(productId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $productId] 존재하지 않는 상품입니다.",
            )
    }
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeIntegrationTest"`
Expected: PASS (11건)

`TransactionTemplate` 빈이 없다는 오류가 나면 Task 1 Step 5 의 안내대로 `@Bean` 을 하나 추가한다.

- [ ] **Step 5: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeIntegrationTest.kt
git commit -m "feat : 좋아요 등록·취소 유스케이스 추가

좋아요 행과 like_count 를 한 트랜잭션에서 함께 움직인다.
LikeService 가 전이했다고 보고할 때만 수를 증감하므로 중복 요청이 멱등하다.

트랜잭션을 TransactionTemplate 으로 여는 이유는 예외 흡수 경계가 트랜잭션 경계와
다르기 때문이다. 유니크 제약 위반은 트랜잭션을 rollback-only 로 만들므로
@Transactional 메서드 안에서는 잡아도 커밋할 수 없다."
```

---

## Task 5: 동시성 회귀 테스트

**이 Task 를 건너뛰면 안 된다.** Task 2·3·4 의 설계를 지키는 유일한 테스트다.
누군가 조건부 UPDATE 를 "읽고 → `delete()` 호출" 로 되돌리거나 원자적 UPDATE 를 엔티티 증감으로 바꿔도,
단일 스레드 테스트는 **전부 통과한다** (설계 문서 10.3 장).

**Files:**
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeConcurrencyTest.kt`

**Interfaces:**
- Consumes: `LikeFacade.like/unlike` (Task 4), `ProductLikeJpaRepository` (Task 2)
- Produces: 없음 (테스트 전용)

---

- [ ] **Step 1: 동시성 테스트를 쓴다**

```kotlin
package com.loopers.application.like

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 좋아요의 동시성 계약을 지키는 회귀 테스트.
 *
 * Testcontainers 가 띄우는 진짜 MySQL 8.0 위에서 돌기 때문에 InnoDB 의 행 락과 유니크 제약이 실제로 동작한다.
 * 인메모리 DB 였다면 이 검증이 불가능했을 것이다.
 *
 * 각 테스트가 좋아요 행 수와 like_count 를 함께 단언하는 이유는,
 * 두 진실 원천이 서로 어긋나지 않았는지가 확인 대상이기 때문이다. (설계 문서 6.1 장)
 */
@SpringBootTest
class LikeFacadeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productLikeJpaRepository: ProductLikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val CONCURRENT_USERS = 10
        private const val BASE_LIKE_COUNT = 5L
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    private fun saveProduct(): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(10_000),
                likeCount = LikeCount(BASE_LIKE_COUNT),
            ),
        )
    }

    private fun likeCountOf(productId: Long): Long = productRepository.findById(productId)!!.likeCount.value

    /**
     * 모든 스레드를 같은 순간에 출발시킨다.
     * 순차 실행이면 경합이 재현되지 않아 테스트가 있으나 마나가 되므로 시작 래치가 필요하다.
     */
    private fun runConcurrently(count: Int, task: (Int) -> Unit) {
        val executor = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val failures = CopyOnWriteArrayList<Throwable>()

        repeat(count) { index ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    task(index)
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 예외가 밖으로 새면 설계가 흡수하기로 한 경합을 흡수하지 못한 것이다. (설계 문서 6.8 장)
        assertThat(failures).describedAs("동시 요청에서 예외가 밖으로 새면 안 된다").isEmpty()
    }

    /**
     * 원자적 UPDATE 가 아니라 "엔티티를 읽어 +1" 이면 이 단언이 실패한다.
     * 여러 스레드가 같은 값을 읽고 같은 값을 써서 증가분이 사라진다.
     */
    @DisplayName("서로 다른 회원이 같은 상품에 동시에 좋아요하면, 좋아요 수가 회원 수만큼 늘어난다.")
    @Test
    fun countsEveryLike_whenDifferentUsersLikeConcurrently() {
        // arrange
        val users = (1..CONCURRENT_USERS).map { signUp("user$it") }
        val product = saveProduct()

        // act
        runConcurrently(CONCURRENT_USERS) { index -> likeFacade.like(users[index].loginId, product.id) }

        // assert
        assertAll(
            { assertThat(likeCountOf(product.id)).isEqualTo(BASE_LIKE_COUNT + CONCURRENT_USERS) },
            { assertThat(productLikeJpaRepository.count()).isEqualTo(CONCURRENT_USERS.toLong()) },
        )
    }

    /**
     * 유니크 제약이 없거나 경합 예외를 흡수하지 못하면 이 단언이 실패한다.
     * 행이 둘 생기거나, 진 쪽의 예외가 밖으로 새어 failures 가 비지 않는다.
     */
    @DisplayName("같은 회원이 같은 상품에 동시에 좋아요를 두 번 보내면, 행 하나와 수 1 증가만 남는다.")
    @Test
    fun keepsSingleRow_whenSameUserLikesConcurrently() {
        // arrange
        val user = signUp("loopers01")
        val product = saveProduct()

        // act
        runConcurrently(2) { likeFacade.like(user.loginId, product.id) }

        // assert
        assertAll(
            { assertThat(likeCountOf(product.id)).isEqualTo(BASE_LIKE_COUNT + 1) },
            { assertThat(productLikeJpaRepository.count()).isEqualTo(1L) },
        )
    }

    /**
     * 취소를 "읽고 → deletedAt 확인 → delete()" 로 하면 이 단언이 실패한다.
     * 두 스레드가 모두 살아 있는 행을 보고 각자 수를 1 씩 줄여 2 가 빠진다. (설계 문서 6.2 장)
     */
    @DisplayName("같은 회원이 같은 상품의 좋아요를 동시에 두 번 취소해도, 수는 1 만 줄어든다.")
    @Test
    fun decreasesOnce_whenSameUserUnlikesConcurrently() {
        // arrange
        val user = signUp("loopers01")
        val product = saveProduct()
        likeFacade.like(user.loginId, product.id)

        // act
        runConcurrently(2) { likeFacade.unlike(user.loginId, product.id) }

        // assert
        assertThat(likeCountOf(product.id)).isEqualTo(BASE_LIKE_COUNT)
    }
}
```

- [ ] **Step 2: 테스트가 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeConcurrencyTest"`
Expected: PASS (3건)

**여기서 실패하면 Task 2·3·4 의 구현이 설계와 다르다.** 테스트를 고치지 말고 구현을 고친다.

단, `keepsSingleRow_whenSameUserLikesConcurrently` 가 `failures` 에 예외가 담겨 실패한다면 예외 **타입**을 먼저 본다.
`LikeFacade` 는 `DataIntegrityViolationException` 을 잡는데, Hibernate 의 제약 위반이 커밋 시점에 터지면서
`JpaSystemException` 등 다른 스프링 예외로 번역되는 경우가 있다.
그때는 잡는 타입을 실제로 올라온 예외의 공통 상위 타입으로 넓히되,
**`Exception` 까지 넓히지는 않는다** — 그러면 `CoreException` 의 404 까지 삼켜 Task 4 의 테스트가 깨진다.
`org.springframework.dao.DataAccessException` 이 그 상한이다.

- [ ] **Step 3: 설계가 실제로 검증되는지 역으로 확인한다 (권장)**

테스트가 무의미하지 않다는 것을 한 번은 눈으로 봐야 한다.
`ProductLikeJpaRepository.softDelete` 의 `AND l.deletedAt IS NULL` 을 **잠시 지우고** 세 번째 테스트를 돌린다.

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeConcurrencyTest"`
Expected: `decreasesOnce_whenSameUserUnlikesConcurrently` FAIL — 기대 5, 실제 4

확인했으면 조건을 **되돌린다.** 되돌린 뒤 다시 돌려 PASS 를 확인한다.

- [ ] **Step 4: 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeConcurrencyTest.kt
git commit -m "test : 좋아요 동시성 회귀 테스트 추가

조건부 UPDATE 와 원자적 증감이 되돌려지는 것을 막는 유일한 방어선이다.
단일 스레드 테스트는 그 변경 이후에도 전부 통과하기 때문이다.

행 수와 like_count 를 함께 단언해 두 진실 원천의 정합을 확인한다."
```

---

## Task 6: 좋아요 목록 조회

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/LikeService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeIntegrationTest.kt` (보강)

**Interfaces:**
- Produces:
  - `ProductLikeRepository.findLikedProductIds(userId: Long, pageQuery: PageQuery): PageResult<Long>`
  - `LikeService.getLikedProductIds(userId: Long, pageQuery: PageQuery): PageResult<Long>`
  - `ProductRepository.findAllByIds(ids: List<Long>): List<ProductModel>`
  - `ProductService.getProductsByIds(ids: List<Long>): List<ProductModel>`
  - `LikeFacade.getLikedProducts(loginId: LoginId, pageQuery: PageQuery): PageResult<ProductInfo>`

---

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`LikeFacadeIntegrationTest.kt` 에 `@Nested` 블록을 추가한다. 기존 헬퍼를 그대로 쓰되,
브랜드를 재사용할 수 있도록 `saveProduct` 를 다음과 같이 바꾼다.

```kotlin
    private fun saveProduct(likeCount: Long = 0, brandId: Long? = null, name: String = "상품"): ProductModel {
        val resolvedBrandId = brandId ?: brandRepository.save(BrandModel.create(BrandName("루퍼스"))).id
        return productRepository.save(
            ProductModel.create(
                brandId = resolvedBrandId,
                name = ProductName(name),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )
    }
```

```kotlin
    @DisplayName("내가 좋아요한 상품 목록을 조회할 때, ")
    @Nested
    inner class GetLikedProducts {
        @DisplayName("좋아요한 상품만 반환된다.")
        @Test
        fun returnsOnlyLikedProducts() {
            // arrange
            val user = signUp()
            val liked = saveProduct(name = "좋아요한 상품")
            saveProduct(name = "좋아요하지 않은 상품")
            likeFacade.like(user.loginId, liked.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content.first().id).isEqualTo(liked.id) },
                { assertThat(result.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("최근에 좋아요한 상품이 앞에 온다.")
        @Test
        fun ordersByMostRecentlyLiked() {
            // arrange
            val user = signUp()
            val first = saveProduct(name = "먼저")
            val second = saveProduct(name = "나중")
            likeFacade.like(user.loginId, first.id)
            likeFacade.like(user.loginId, second.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(second.id, first.id)
        }

        /**
         * created_at 으로 정렬하면 이 단언이 실패한다.
         * 취소했다 다시 누른 좋아요의 created_at 은 최초 시점이라 방금 누른 상품이 맨 뒤로 간다.
         * (설계 문서 4.5 장)
         */
        @DisplayName("취소했다 다시 좋아요한 상품이, 목록 맨 앞에 온다.")
        @Test
        fun putsRelikedProductFirst() {
            // arrange
            val user = signUp()
            val first = saveProduct(name = "먼저")
            val second = saveProduct(name = "나중")
            likeFacade.like(user.loginId, first.id)
            likeFacade.like(user.loginId, second.id)
            likeFacade.unlike(user.loginId, first.id)
            likeFacade.like(user.loginId, first.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(first.id, second.id)
        }

        @DisplayName("취소한 상품은 목록에서 빠지고, totalElements 도 함께 줄어든다.")
        @Test
        fun excludesUnlikedProduct() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            likeFacade.like(user.loginId, product.id)
            likeFacade.unlike(user.loginId, product.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("다른 회원의 좋아요는 섞이지 않는다.")
        @Test
        fun doesNotMixOtherUsersLikes() {
            // arrange
            val mine = signUp("loopers01")
            val other = signUp("loopers02")
            val myProduct = saveProduct(name = "내 것")
            val otherProduct = saveProduct(name = "남의 것")
            likeFacade.like(mine.loginId, myProduct.id)
            likeFacade.like(other.loginId, otherProduct.id)

            // act
            val result = likeFacade.getLikedProducts(mine.loginId, PageQuery())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(myProduct.id)
        }

        @DisplayName("페이징 메타가 좋아요 개수를 기준으로 채워진다.")
        @Test
        fun fillsPagingMetadata() {
            // arrange
            val user = signUp()
            val brandId = brandRepository.save(BrandModel.create(BrandName("루퍼스"))).id
            repeat(5) { index ->
                val product = saveProduct(brandId = brandId, name = "상품${index + 1}")
                likeFacade.like(user.loginId, product.id)
            }

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery(page = 1, size = 2))

            // assert
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(2) },
                { assertThat(result.totalElements).isEqualTo(5L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }

        @DisplayName("좋아요가 하나도 없으면, 빈 목록과 totalElements 0 이 반환된다.")
        @Test
        fun returnsEmptyPage_whenNothingIsLiked() {
            // arrange
            val user = signUp()

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
                { assertThat(result.totalPages).isEqualTo(0) },
            )
        }

        @DisplayName("상품의 브랜드가 삭제됐으면, brand 는 null 이고 목록에서 빠지지 않는다.")
        @Test
        fun keepsProductWithNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val user = signUp()
            val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
            val product = saveProduct(brandId = brand.id)
            likeFacade.like(user.loginId, product.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content.first().brand).isNull() },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { likeFacade.getLikedProducts(LoginId("nobody"), PageQuery()) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
```

`import com.loopers.domain.support.PageQuery` 를 추가한다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeIntegrationTest"`
Expected: 컴파일 실패 — `Unresolved reference: getLikedProducts`

- [ ] **Step 3: 좋아요 목록 쿼리를 추가한다**

`ProductLikeRepository.kt` 에 추가한다. `PageQuery` / `PageResult` import 가 필요하다.

```kotlin
    /**
     * 회원이 좋아요한 상품 ID 를 최근 좋아요 순으로 페이징 조회한다.
     *
     * 상품 테이블을 조인하지 않으므로 totalElements 가 좋아요 개수와 정확히 일치한다. (설계 문서 7.3 장)
     * 이 성질이 성립하려면 상품이 삭제될 때 좋아요 행도 함께 삭제되어야 한다. Task 7 이 그것을 보장한다.
     */
    fun findLikedProductIds(userId: Long, pageQuery: PageQuery): PageResult<Long>
```

`ProductLikeJpaRepository.kt` 에 추가한다. `Pageable` import 가 필요하다.

```kotlin
    /**
     * updatedAt 으로 정렬하는 이유는 취소 후 재좋아요 때문이다.
     * createdAt 은 최초 좋아요 시점이라, 그것으로 정렬하면 방금 누른 좋아요가 목록 맨 뒤에 나타난다.
     * id DESC 보조 정렬은 같은 시각의 행이 여럿일 때 페이지 경계에서 중복과 누락을 막는다.
     */
    @Query(
        """
        SELECT l.productId FROM ProductLikeModel l
         WHERE l.userId = :userId AND l.deletedAt IS NULL
         ORDER BY l.updatedAt DESC, l.id DESC
        """,
    )
    fun findLikedProductIds(@Param("userId") userId: Long, pageable: Pageable): List<Long>

    fun countByUserIdAndDeletedAtIsNull(userId: Long): Long
```

`ProductLikeRepositoryImpl.kt` 에 추가한다.

```kotlin
    /** Pageable 은 이 클래스 안에서만 쓰이고, 도메인 계약은 PageQuery / PageResult 로 유지된다. */
    override fun findLikedProductIds(userId: Long, pageQuery: PageQuery): PageResult<Long> {
        val productIds = productLikeJpaRepository.findLikedProductIds(
            userId = userId,
            pageable = PageRequest.of(pageQuery.page, pageQuery.size),
        )
        val totalElements = productLikeJpaRepository.countByUserIdAndDeletedAtIsNull(userId)

        return PageResult.of(content = productIds, pageQuery = pageQuery, totalElements = totalElements)
    }
```

`LikeService.kt` 에 추가한다.

```kotlin
    /** 상품 정보는 이 애그리거트의 것이 아니므로 ID 만 돌려준다. 상품 결합은 LikeFacade 가 한다. */
    @Transactional(readOnly = true)
    fun getLikedProductIds(userId: Long, pageQuery: PageQuery): PageResult<Long> {
        return productLikeRepository.findLikedProductIds(userId = userId, pageQuery = pageQuery)
    }
```

- [ ] **Step 4: ID 집합으로 상품을 조회하는 경로를 추가한다**

`ProductRepository.kt` 에 추가한다.

```kotlin
    /** 소프트 삭제된 상품은 제외된다. 좋아요 목록처럼 ID 집합으로 조회하는 경로가 쓴다. */
    fun findAllByIds(ids: List<Long>): List<ProductModel>
```

`ProductJpaRepository.kt` 에 추가한다.

```kotlin
    fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<ProductModel>
```

`ProductRepositoryImpl.kt` 에 추가한다.

```kotlin
    override fun findAllByIds(ids: List<Long>): List<ProductModel> {
        // IN () 은 문법 오류이고 조회할 대상도 없으므로 쿼리 자체를 보내지 않는다. BrandRepositoryImpl 과 같은 처리다.
        if (ids.isEmpty()) return emptyList()

        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
    }
```

`ProductService.kt` 에 추가한다.

```kotlin
    /**
     * ID 집합으로 상품을 조회한다. 없거나 삭제된 ID 는 결과에 없다.
     *
     * 반환 순서는 보장하지 않는다. 순서가 필요한 호출자는 자기가 가진 ID 목록 순서로 재배열해야 한다.
     */
    @Transactional(readOnly = true)
    fun getProductsByIds(ids: List<Long>): List<ProductModel> {
        return productRepository.findAllByIds(ids)
    }
```

- [ ] **Step 5: 파사드에 목록 유스케이스를 추가한다**

`LikeFacade` 생성자에 `private val brandService: BrandService,` 를 추가하고 메서드를 넣는다.

```kotlin
    /**
     * 내가 좋아요한 상품 목록.
     *
     * 좋아요 행만으로 페이징이 끝나므로 totalElements 가 좋아요 개수와 정확히 일치한다.
     * 상품과 브랜드는 그 뒤에 IN 절 한 번씩으로 결합한다 — ProductFacade 와 같은 조합 방식이다. (설계 문서 7.3 장)
     */
    fun getLikedProducts(loginId: LoginId, pageQuery: PageQuery): PageResult<ProductInfo> {
        val user = getUserOrThrow(loginId)
        val likedIds = likeService.getLikedProductIds(userId = user.id, pageQuery = pageQuery)
        val products = productService.getProductsByIds(likedIds.content).associateBy { it.id }
        val brands = brandService.getBrands(products.values.map { it.brandId }.distinct())
            .associate { it.id to BrandInfo.from(it) }

        // 좋아요 순서는 likedIds.content 가 갖고 있다. 상품 조회 결과의 순서는 보장되지 않으므로 이쪽을 기준으로 돈다.
        // mapNotNull 인 이유는 연쇄 삭제 밖의 경로로 상품이 사라졌을 때 목록 전체를 실패시키지 않기 위해서다.
        // Task 7 이후에는 정상 경로에서 누락이 발생하지 않는다.
        val content = likedIds.content.mapNotNull { productId ->
            products[productId]?.let { ProductInfo.of(it, brands[it.brandId]) }
        }

        return PageResult(
            content = content,
            page = likedIds.page,
            size = likedIds.size,
            totalElements = likedIds.totalElements,
        )
    }
```

import 에 `BrandInfo`, `BrandService`, `PageQuery`, `PageResult`, `ProductInfo` 를 추가한다.

> 이 브랜드 결합은 `ProductFacade.loadBrands` 와 사실상 같은 코드다. 호출부가 둘뿐이라 지금은 중복을 남긴다.
> 세 번째 호출부가 생기면 공통 조립기로 뽑는다. 「완료 확인」의 후속 과제에 적혀 있다.

- [ ] **Step 6: 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeIntegrationTest"`
Expected: PASS (20건)

- [ ] **Step 7: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/main/kotlin/com/loopers/ \
        apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeFacadeIntegrationTest.kt
git commit -m "feat : 내가 좋아요한 상품 목록 조회 추가

좋아요 행만으로 페이징을 끝내고 상품과 브랜드를 IN 절로 결합한다.
조인하지 않으므로 totalElements 가 좋아요 개수와 정확히 일치한다.

updated_at 으로 정렬하는 이유는 취소 후 재좋아요 때문이다.
created_at 순이면 방금 누른 좋아요가 목록 맨 뒤에 나타난다."
```

---

## Task 7: 상품 삭제 연쇄

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/like/ProductLikeRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/like/LikeService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt:115` (`deleteAllByBrandId` 반환 타입)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product/ProductAdminFacade.kt:66`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/admin/brand/BrandAdminFacade.kt:57`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/admin/product/ProductAdminFacadeIntegrationTest.kt` (보강)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/admin/brand/BrandAdminFacadeIntegrationTest.kt` (보강)

**Interfaces:**
- Produces:
  - `ProductLikeRepository.deleteAllByProductIds(productIds: List<Long>, now: ZonedDateTime): Int`
  - `LikeService.deleteAllByProductIds(productIds: List<Long>)`
  - `ProductService.deleteAllByBrandId(brandId: Long): List<Long>` — **반환 타입이 바뀐다**

---

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`ProductAdminFacadeIntegrationTest.kt` 의 삭제 관련 `@Nested` 블록에 추가한다.
`likeFacade` 와 `productLikeJpaRepository` 를 생성자 주입에 더한다.

```kotlin
        @DisplayName("상품을 삭제하면, 그 상품의 좋아요도 함께 삭제된다.")
        @Test
        fun softDeletesLikes_whenProductIsDeleted() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            likeFacade.like(user.loginId, product.id)

            // act
            productAdminFacade.delete(product.id)

            // assert — 행은 남지만 살아 있는 좋아요는 0 이다
            assertThat(productLikeJpaRepository.findAll().single().deletedAt).isNotNull()
        }
```

`BrandAdminFacadeIntegrationTest.kt` 에 추가한다.

```kotlin
        @DisplayName("브랜드를 삭제하면, 그 브랜드 상품의 좋아요까지 삭제된다.")
        @Test
        fun softDeletesLikesOfBrandProducts_whenBrandIsDeleted() {
            // arrange
            val user = signUp()
            val brand = saveBrand()
            val product = saveProduct(brandId = brand.id)
            likeFacade.like(user.loginId, product.id)

            // act
            brandAdminFacade.delete(brand.id)

            // assert — 브랜드 → 상품 → 좋아요 2단계 연쇄가 끝까지 도달했는지 본다
            assertThat(productLikeJpaRepository.findAll().single().deletedAt).isNotNull()
        }
```

> 두 테스트가 쓰는 `signUp` / `saveProduct` / `saveBrand` 헬퍼가 해당 파일에 없으면
> `LikeFacadeIntegrationTest` 의 것을 그대로 복사해 넣는다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.admin.*"`
Expected: 두 새 테스트가 FAIL — `deletedAt` 이 `null`

- [ ] **Step 3: 연쇄 삭제 쿼리를 추가한다**

`ProductLikeRepository.kt`

```kotlin
    /**
     * 상품 삭제의 연쇄 처리용. 살아 있는 좋아요만 취소하므로 재호출이 멱등하다.
     * 반환값은 영향 행 수다.
     */
    fun deleteAllByProductIds(productIds: List<Long>, now: ZonedDateTime): Int
```

`ProductLikeJpaRepository.kt`

```kotlin
    /**
     * 여기서는 벌크 UPDATE 를 쓴다.
     * ProductService.deleteAllByBrandId 가 벌크를 피한 이유는 PreUpdate 타임스탬프와 1차 캐시 stale 이었는데,
     * 좋아요 행은 updatedAt 을 SET 절에 직접 쓰고 같은 트랜잭션에서 다시 읽지도 않아 두 이유가 성립하지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductLikeModel l
           SET l.deletedAt = :now, l.updatedAt = :now
         WHERE l.productId IN :productIds AND l.deletedAt IS NULL
        """,
    )
    fun deleteAllByProductIds(
        @Param("productIds") productIds: List<Long>,
        @Param("now") now: ZonedDateTime,
    ): Int
```

`ProductLikeRepositoryImpl.kt`

```kotlin
    override fun deleteAllByProductIds(productIds: List<Long>, now: ZonedDateTime): Int {
        // IN () 은 문법 오류다. 대상이 없으면 쿼리를 보내지 않는다.
        if (productIds.isEmpty()) return 0

        return productLikeJpaRepository.deleteAllByProductIds(productIds = productIds, now = now)
    }
```

`LikeService.kt`

```kotlin
    /**
     * 상품 삭제에 딸린 연쇄 처리.
     *
     * like_count 는 건드리지 않는다. 상품이 삭제되면 그 값은 아무 데도 노출되지 않으므로 조정할 대상이 아니고,
     * 상품을 복구하는 API 가 없어 연쇄가 단방향이라 이 판단이 안전하다. (설계 문서 7.4 장)
     */
    @Transactional
    fun deleteAllByProductIds(productIds: List<Long>) {
        productLikeRepository.deleteAllByProductIds(productIds = productIds, now = ZonedDateTime.now())
    }
```

- [ ] **Step 4: `deleteAllByBrandId` 가 삭제한 상품 ID 를 돌려주게 한다**

`ProductService.kt` 의 `deleteAllByBrandId` 를 바꾼다. 기존 KDoc 은 그대로 두고 한 문단만 덧붙인다.

```kotlin
    /**
     * (기존 주석 유지)
     *
     * 삭제한 상품 ID 를 반환하는 이유는 호출자가 그 상품들의 좋아요를 이어서 지워야 하기 때문이다.
     * 좋아요는 다른 애그리거트이므로 이 서비스가 직접 건드리지 않는다.
     */
    @Transactional
    fun deleteAllByBrandId(brandId: Long): List<Long> {
        return productRepository.findAllByBrandId(brandId)
            .onEach { it.delete() }
            .map { it.id }
    }
```

기존 호출부는 반환값을 무시해도 컴파일되므로 다른 곳은 깨지지 않는다.

- [ ] **Step 5: 어드민 파사드 두 곳을 잇는다**

`ProductAdminFacade.kt` — 생성자에 `private val likeService: LikeService,` 를 추가한다.

```kotlin
    /**
     * 상품을 삭제하고 그 상품의 좋아요도 함께 삭제한다.
     *
     * 연쇄하지 않으면 좋아요 목록의 totalElements 는 20 인데 content 는 17 건인 응답이 나간다. (설계 문서 7.4 장)
     * 두 애그리거트에 걸친 변경이라 여기에 트랜잭션이 필요하다.
     */
    @Transactional
    fun delete(id: Long) {
        productService.delete(id)
        likeService.deleteAllByProductIds(listOf(id))
    }
```

`import org.springframework.transaction.annotation.Transactional` 을 추가한다.

`BrandAdminFacade.kt` — 생성자에 `private val likeService: LikeService,` 를 추가한다.

```kotlin
    @Transactional
    fun delete(id: Long) {
        brandService.delete(id)
        val deletedProductIds = productService.deleteAllByBrandId(id)
        likeService.deleteAllByProductIds(deletedProductIds)
    }
```

기존 KDoc 에 한 줄을 덧붙인다: `브랜드 → 상품 → 좋아요 2단계 연쇄이며, 각 단계가 살아 있는 대상만 고르므로 전체가 멱등하다.`

- [ ] **Step 6: 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.admin.*"`
Expected: PASS — 기존 케이스 전부 + 새 2건

- [ ] **Step 7: 목록에서 삭제된 상품이 사라지는지 확인한다**

`LikeFacadeIntegrationTest` 의 `GetLikedProducts` 에 추가한다.
`productAdminFacade` 를 생성자 주입에 더한다.

```kotlin
        /**
         * 연쇄 삭제가 없으면 content 는 비는데 totalElements 는 1 로 남아 응답이 자기모순에 빠진다.
         */
        @DisplayName("좋아요한 상품이 삭제되면, 목록에서 빠지고 totalElements 도 함께 줄어든다.")
        @Test
        fun excludesDeletedProduct_andShrinksTotalElements() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            likeFacade.like(user.loginId, product.id)

            // act
            productAdminFacade.delete(product.id)
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
            )
        }
```

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeIntegrationTest"`
Expected: PASS (21건)

- [ ] **Step 8: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/
git commit -m "feat : 상품 삭제 시 좋아요 연쇄 삭제 추가

연쇄하지 않으면 좋아요 목록이 totalElements 20 에 content 17 건인
자기모순 응답을 낸다. 브랜드 삭제는 상품을 거쳐 2단계로 연쇄한다.

deleteAllByBrandId 가 삭제한 상품 ID 를 반환하도록 바꾼다.
좋아요는 다른 애그리거트라 도메인 서비스가 직접 건드리지 않고 파사드가 잇는다.

like_count 는 조정하지 않는다. 삭제된 상품의 카운트는 노출되지 않고,
상품 복구 경로가 없어 연쇄가 단방향이라 안전하다."
```

---

## Task 8: 좋아요 등록·취소 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ApiHeaders.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/ProductLikeV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/ProductLikeV1Controller.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt:70`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductLikeV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `LikeFacade.like/unlike(loginId: LoginId, productId: Long)` (Task 4)
- Produces: `ApiHeaders.LOGIN_ID: String` (const), `POST` / `DELETE /api/v1/products/{productId}/likes`

---

- [ ] **Step 1: 실패하는 E2E 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductLikeV1ApiE2ETest.kt`

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductLikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers01"
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID) =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    private fun saveProduct(likeCount: Long = 0): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )
    }

    /** loginId 가 null 이면 헤더를 아예 넣지 않는다. UserV1ApiE2ETest 와 같은 방식이다. */
    private fun request(method: HttpMethod, productId: Any, loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/products/$productId/likes",
            method,
            HttpEntity<Any>(HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }),
            responseType,
        )

    private fun likeCountOf(productId: Long): Long = productRepository.findById(productId)!!.likeCount.value

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class Like {
        @DisplayName("좋아요에 성공하면, 200 을 반환하고 좋아요 수가 1 늘어난다.")
        @Test
        fun returnsOk_andIncreasesLikeCount() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)

            // act
            val response = request(HttpMethod.POST, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(likeCountOf(product.id)).isEqualTo(6L) },
            )
        }

        @DisplayName("이미 좋아요한 상품에 다시 요청해도, 200 이고 좋아요 수는 그대로다.")
        @Test
        fun returnsOk_andKeepsLikeCount_whenRequestedTwice() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)
            request(HttpMethod.POST, product.id)

            // act
            val response = request(HttpMethod.POST, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(likeCountOf(product.id)).isEqualTo(6L) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val product = saveProduct()

            // act
            val response = request(HttpMethod.POST, product.id, loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("로그인 ID 형식이 잘못되면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange
            val product = saveProduct()

            // act
            val response = request(HttpMethod.POST, product.id, loginId = "loopers-01")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProduct()

            // act
            val response = request(HttpMethod.POST, product.id, loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUp()

            // act
            val response = request(HttpMethod.POST, 99999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("상품 ID 가 숫자가 아니면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenProductIdIsNotNumeric() {
            // arrange
            signUp()

            // act
            val response = request(HttpMethod.POST, "abc")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class Unlike {
        @DisplayName("취소에 성공하면, 200 을 반환하고 좋아요 수가 1 줄어든다.")
        @Test
        fun returnsOk_andDecreasesLikeCount() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)
            request(HttpMethod.POST, product.id)

            // act
            val response = request(HttpMethod.DELETE, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(likeCountOf(product.id)).isEqualTo(5L) },
            )
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도, 200 이고 좋아요 수는 그대로다.")
        @Test
        fun returnsOk_andKeepsLikeCount_whenNotLiked() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)

            // act
            val response = request(HttpMethod.DELETE, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(likeCountOf(product.id)).isEqualTo(5L) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val product = saveProduct()

            // act
            val response = request(HttpMethod.DELETE, product.id, loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUp()

            // act
            val response = request(HttpMethod.DELETE, 99999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductLikeV1ApiE2ETest"`
Expected: 컴파일 실패 — `Unresolved reference: ApiHeaders`

- [ ] **Step 3: 헤더 상수를 한 곳으로 모은다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ApiHeaders.kt`

```kotlin
package com.loopers.interfaces.api

/**
 * 요청 헤더 이름.
 *
 * 애노테이션 인자로 쓰이므로 const 여야 한다.
 * 컨트롤러마다 문자열을 따로 두면 세 곳에 같은 리터럴이 흩어지고, 그중 하나만 바뀌어도 아무도 눈치채지 못한다.
 */
object ApiHeaders {
    /** 요청 주체인 회원의 로그인 ID. 이 헤더는 식별만 하며 인증하지 않는다. */
    const val LOGIN_ID = "X-Loopers-LoginId"
}
```

`UserV1Controller.kt` 의 companion object 를 다음과 같이 바꾼다. 이름은 유지하므로 기존 테스트는 그대로 동작한다.

```kotlin
    companion object {
        /** 조회 대상 회원을 식별하는 헤더. 실제 값은 ApiHeaders 가 소유한다. */
        const val HEADER_LOGIN_ID = ApiHeaders.LOGIN_ID
    }
```

`import com.loopers.interfaces.api.ApiHeaders` 를 추가한다.

- [ ] **Step 4: API 스펙과 컨트롤러를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/ProductLikeV1ApiSpec.kt`

```kotlin
package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product Like V1 API", description = "Loopers 상품 좋아요 API 입니다.")
interface ProductLikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "요청은 멱등합니다. 이미 좋아요한 상품에 다시 요청해도 200 이며 좋아요 수는 늘지 않습니다. " +
            "삭제된 상품은 존재하지 않는 것으로 취급해 404 입니다.",
    )
    fun like(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "상품 ID", description = "좋아요할 상품의 ID")
        productId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "요청은 멱등합니다. 좋아요하지 않은 상품을 취소해도 200 이며 좋아요 수는 줄지 않습니다.",
    )
    fun unlike(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "상품 ID", description = "좋아요를 취소할 상품의 ID")
        productId: Long,
    ): ApiResponse<Any>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/ProductLikeV1Controller.kt`

```kotlin
package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 상품에 딸린 좋아요 리소스.
 *
 * 목록 조회가 UserLikeV1Controller 로 갈라져 있는 것은 URL 트리가 다르기 때문이다.
 * 이 프로젝트의 컨트롤러는 클래스 레벨 RequestMapping 으로 자기 리소스 트리를 선언한다. (설계 문서 7.2 장)
 *
 * 헤더 값을 LoginId 로 감싸는 것만으로 "영문과 숫자 10자 이내" 검증이 수행된다.
 * 위반 시 LoginId 생성자가 CoreException(BAD_REQUEST) 를 던지므로 별도 검증 코드를 두지 않는다.
 *
 * 주의: 이 API 는 인증을 수행하지 않는다. 헤더 값의 형식만 검증할 뿐 요청자가 본인인지 확인하지 않으므로,
 * 로그인 ID 를 아는 누구나 타인 명의로 좋아요를 걸고 취소할 수 있다.
 * 의도된 범위 제외이며, 자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다. (설계 문서 11.1 장)
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
class ProductLikeV1Controller(
    private val likeFacade: LikeFacade,
) : ProductLikeV1ApiSpec {
    /**
     * 201 이 아니라 200 이다. 멱등이라 "이번 요청이 실제로 행을 만들었는가" 가 요청마다 다른데,
     * 클라이언트가 그 차이로 분기해서 할 수 있는 일이 없다. (설계 문서 4.4 장)
     */
    @PostMapping
    override fun like(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.like(LoginId(loginId), productId)
        return ApiResponse.success()
    }

    @DeleteMapping
    override fun unlike(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.unlike(LoginId(loginId), productId)
        return ApiResponse.success()
    }
}
```

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductLikeV1ApiE2ETest"`
Expected: PASS (11건)

- [ ] **Step 6: 회원 API 가 깨지지 않았는지 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserV1ApiE2ETest"`
Expected: PASS (기존 그대로)

- [ ] **Step 7: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/
git commit -m "feat : 좋아요 등록·취소 API 추가

POST 와 DELETE 모두 멱등하며 200 을 반환한다.
201 을 쓰지 않는 이유는 '이번 요청이 행을 만들었는가' 가 요청마다 다른데
클라이언트가 그 차이로 분기해서 할 수 있는 일이 없기 때문이다.

X-Loopers-LoginId 리터럴을 ApiHeaders 로 모은다.
컨트롤러가 셋으로 늘면서 같은 문자열이 흩어지기 시작했다."
```

---

## Task 9: 좋아요 목록 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/UserLikeV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/UserLikeV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserLikeV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `LikeFacade.getLikedProducts(loginId: LoginId, pageQuery: PageQuery): PageResult<ProductInfo>` (Task 6), `ProductV1Dto.ProductResponse.from(info)` (기존)
- Produces: `GET /api/v1/users/me/likes`

---

- [ ] **Step 1: 실패하는 E2E 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserLikeV1ApiE2ETest.kt`

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserLikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers01"
    }

    private val listResponseType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
    private val emptyResponseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID) =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    private fun saveProduct(likeCount: Long = 0): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )
    }

    /** loginId 가 null 이면 헤더를 아예 넣지 않는다. */
    private fun getLikes(query: String = "", loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/users/me/likes$query",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }),
            listResponseType,
        )

    private fun like(productId: Long, loginId: String = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/products/$productId/likes",
            HttpMethod.POST,
            HttpEntity<Any>(HttpHeaders().apply { set(ApiHeaders.LOGIN_ID, loginId) }),
            emptyResponseType,
        )

    // 아래 @Nested 블록이 이어진다.
}
```

이어서 클래스 본문에 다음 `@Nested` 블록을 넣는다.

```kotlin
    @DisplayName("GET /api/v1/users/me/likes")
    @Nested
    inner class GetLikedProducts {
        @DisplayName("좋아요한 상품 목록을 200 과 함께 반환한다.")
        @Test
        fun returnsLikedProducts() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 3)
            like(product.id)

            // act
            val response = getLikes()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.content).hasSize(1) },
                { assertThat(data?.content?.first()?.id).isEqualTo(product.id) },
                { assertThat(data?.content?.first()?.likeCount).isEqualTo(4L) },
                { assertThat(data?.content?.first()?.brand?.name).isEqualTo("루퍼스") },
                { assertThat(data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("파라미터가 없으면, page 0 size 20 이 적용된다.")
        @Test
        fun appliesDefaults_whenNoParameterIsGiven() {
            // arrange
            signUp()

            // act
            val response = getLikes()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(data?.page).isEqualTo(0) },
                { assertThat(data?.size).isEqualTo(20) },
            )
        }

        @DisplayName("좋아요가 없으면, 빈 목록과 totalElements 0 을 반환한다.")
        @Test
        fun returnsEmptyPage_whenNothingIsLiked() {
            // arrange
            signUp()

            // act
            val response = getLikes()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.content).isEmpty() },
                { assertThat(data?.totalElements).isEqualTo(0L) },
                { assertThat(data?.totalPages).isEqualTo(0) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()

            // act
            val response = getLikes(loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // act
            val response = getLikes(loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("페이징 파라미터가 범위를 벗어나면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["?page=-1", "?size=0", "?size=101", "?page=abc"])
        fun returnsBadRequest_whenPagingParameterIsInvalid(query: String) {
            // arrange
            signUp()

            // act
            val response = getLikes(query = query)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserLikeV1ApiE2ETest"`
Expected: 404 응답으로 FAIL — 아직 엔드포인트가 없다

- [ ] **Step 3: API 스펙과 컨트롤러를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/UserLikeV1ApiSpec.kt`

```kotlin
package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.product.ProductV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User Like V1 API", description = "Loopers 내 좋아요 API 입니다.")
interface UserLikeV1ApiSpec {
    @Operation(
        summary = "내가 좋아요한 상품 목록 조회",
        description = "최근에 좋아요한 상품이 앞에 옵니다. 취소한 좋아요와 삭제된 상품은 목록과 totalElements 양쪽에서 빠집니다. " +
            "응답의 likeCount 는 그 상품의 전체 좋아요 수이며 이 회원의 것이 아닙니다.",
    )
    fun getLikedProducts(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "페이지 번호", description = "0 이상. 기본값 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 기본값 20")
        size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>
}
```

`import jakarta.servlet.http.HttpServletResponse` 를 추가한다.
`response` 파라미터는 캐시 헤더를 세팅하기 위한 것이며, `UserV1ApiSpec.getMyInfo` 가 이미 같은 형태다.

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/like/UserLikeV1Controller.kt`

```kotlin
package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.product.ProductV1Dto
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 회원에 딸린 좋아요 목록.
 *
 * 경로가 users/{userId} 가 아니라 users/me 인 이유는 세 가지다 — 등록·취소 URI 에 사용자가 없어 주체가
 * 헤더에서 와야 하고, MeResponse 가 의도적으로 id 를 노출하지 않아 클라이언트가 자기 userId 를 모르며,
 * 인증이 없는 상태에서 남의 목록을 지목할 수 있는 URL 을 만들지 않기 위해서다. (설계 문서 4.2 장)
 *
 * 쿼리 파라미터를 DTO 로 묶지 않고 개별 RequestParam 으로 받는 이유는 ProductV1Controller 와 같다.
 * ModelAttribute 바인딩이면 page=abc 가 500 이 되고, 개별 파라미터면 400 이 된다.
 */
@RestController
@RequestMapping("/api/v1/users/me/likes")
class UserLikeV1Controller(
    private val likeFacade: LikeFacade,
) : UserLikeV1ApiSpec {
    @GetMapping
    override fun getLikedProducts(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> {
        // 응답이 URL 이 아닌 헤더에 따라 달라지므로, Vary 없이는 공유 캐시가 다른 사용자에게 이 응답을 재사용한다.
        // GET /api/v1/users/me 가 같은 이유로 같은 처리를 한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.VARY, ApiHeaders.LOGIN_ID)

        return likeFacade.getLikedProducts(LoginId(loginId), PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { ProductV1Dto.ProductResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 4: 캐시 헤더 테스트를 추가한다**

`UserLikeV1ApiE2ETest` 의 `GetLikedProducts` 에 추가한다.

```kotlin
        /**
         * Vary 가 없으면 공유 캐시가 A 의 좋아요 목록을 B 에게 그대로 돌려줄 수 있다.
         * URL 이 모든 사용자에게 동일하기 때문이다.
         */
        @DisplayName("응답에 no-store 와 Vary 헤더가 실린다.")
        @Test
        fun setsCacheHeaders() {
            // arrange
            signUp()

            // act
            val response = getLikes()

            // assert
            assertAll(
                { assertThat(response.headers.getFirst("Cache-Control")).isEqualTo("no-store") },
                { assertThat(response.headers.getFirst("Vary")).isEqualTo("X-Loopers-LoginId") },
            )
        }
```

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserLikeV1ApiE2ETest"`
Expected: PASS (10건)

- [ ] **Step 6: 스타일 검사와 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/
git commit -m "feat : 내가 좋아요한 상품 목록 API 추가

경로를 users/{userId} 가 아니라 users/me 로 둔다.
등록·취소 URI 에 사용자가 없어 주체가 헤더에서 와야 하고,
MeResponse 가 id 를 노출하지 않아 클라이언트가 자기 userId 를 알 수 없다.

응답이 헤더에 따라 달라지므로 no-store 와 Vary 를 세팅한다.
없으면 공유 캐시가 남의 좋아요 목록을 돌려줄 수 있다."
```

---

## Task 10: 시드 회원과 `.http` 요청 파일

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/seed/LocalDataSeeder.kt`
- Create: `http/commerce-api/like-v1.http`

---

- [ ] **Step 1: 시더에 회원을 추가한다**

`LocalDataSeeder.kt` 의 생성자에 `private val userService: UserService,` 를 추가하고, `run` 의 맨 앞에 넣는다.

```kotlin
        // 회원이 없으면 like-v1.http 를 실행할 때마다 회원가입부터 해야 한다.
        // loginId 를 loopers01 로 두지 않는 이유는 user-v1.http 의 첫 요청이 그 ID 로 가입하기 때문이다.
        // 시더가 선점하면 그 파일이 409 로 깨진다.
        val users = USER_SEEDS.map { loginId ->
            userService.signUp(
                UserCommand.SignUp(
                    loginId = LoginId(loginId),
                    password = RawPassword(SEED_PASSWORD),
                    name = UserName("시드회원"),
                    birthDate = BirthDate(SEED_BIRTH_DATE),
                    email = Email("$loginId@loopers.com"),
                ),
            )
        }
```

companion object 에 추가한다.

```kotlin
        /** seeduser01 은 정확히 10자로, LoginId 의 상한이다. */
        private val USER_SEEDS = listOf("seeduser01", "seeduser02", "seeduser03")

        /** 영문·숫자·특수문자를 모두 포함하는 8자이며, 생년월일 19900101 을 포함하지 않는다. */
        private const val SEED_PASSWORD = "Seeder1!"
        private const val SEED_BIRTH_DATE = "1990-01-01"
```

로그 문구를 바꾼다.

```kotlin
        log.info("로컬 시드 데이터 생성 완료 : 회원 {}명, 브랜드 {}개, 상품 {}개", users.size, brands.size, products.size)
```

클래스 KDoc 에 한 문단을 덧붙인다.

```
 * 상품의 likeCount 는 좋아요 행 없이 만들어진 합성 값이다.
 * 정합을 맞추려면 회원 50명과 좋아요 수천 건이 필요한데, likes_desc 정렬 확인이라는 원래 목적에 비해 얻는 것이 없다.
 * 좋아요 API 는 상대 증감만 하므로 출발값이 무엇이든 정확하게 동작한다. (설계 문서 9.2 장)
```

- [ ] **Step 2: 시더가 뜨는지 확인한다**

```bash
docker-compose -f ./docker/infra-compose.yml up -d
./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'
```

Expected: 로그에 `로컬 시드 데이터 생성 완료 : 회원 3명, 브랜드 5개, 상품 137개`

- [ ] **Step 3: `.http` 파일을 만든다**

`http/commerce-api/like-v1.http`

```
// 위에서 아래로 순서대로 실행하는 것을 전제로 한다.
// 상태를 바꾸지 않는 실패 케이스를 앞에 두고, 상태를 바꾸는 요청을 각 구간의 끝에 둔다.
//
// 시드 회원 seeduser01 과 시드 상품 ID 1 을 전제로 한다. (LocalDataSeeder)
// user-v1.http 와 독립적으로 실행할 수 있다. 회원 ID 가 겹치지 않는다.
//
// 주의: 시드의 like_count 는 좋아요 행 없이 만들어진 합성 값이다. (설계 문서 9.2 장)
// 상품 1 의 좋아요 수가 0 이 아니어도 정상이며, 아래 확인 요청은 절대값이 아니라 ±1 변화를 본다.

### 좋아요 전 상태 확인 - 상품 상세
// 이 요청의 likeCount 를 기억해 둔다. 아래 등록/취소 이후와 비교할 기준선이다.
GET {{commerce-api}}/api/v1/products/1

### 좋아요 전 상태 확인 - 내 좋아요 목록 (빈 목록)
GET {{commerce-api}}/api/v1/users/me/likes
X-Loopers-LoginId: seeduser01

### 좋아요 등록 - 헤더 누락 (400 Bad Request)
POST {{commerce-api}}/api/v1/products/1/likes

### 좋아요 등록 - 로그인 ID 형식 위반 (400 Bad Request)
// 하이픈은 영문·숫자만 허용하는 LoginId 규칙에 걸린다.
POST {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seed-user

### 좋아요 등록 - 가입되지 않은 ID (404 Not Found)
POST {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: nobody

### 좋아요 등록 - 존재하지 않는 상품 (404 Not Found)
POST {{commerce-api}}/api/v1/products/99999/likes
X-Loopers-LoginId: seeduser01

### 좋아요 등록 - 상품 ID 가 숫자가 아님 (400 Bad Request)
POST {{commerce-api}}/api/v1/products/abc/likes
X-Loopers-LoginId: seeduser01

### 좋아요 등록
// 이 구간에서 처음으로 상태가 바뀐다.
POST {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

### 좋아요 등록 - 중복 (200 OK, 좋아요 수 불변)
// 409 가 아니다. 클라이언트가 원한 상태가 이미 그 상태이므로 거부할 이유가 없다.
POST {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

### 확인 - 상품 상세의 좋아요 수가 정확히 1 늘었다
// 위에서 POST 를 두 번 보냈지만 1 만 늘어야 한다.
GET {{commerce-api}}/api/v1/products/1

### 확인 - 내 좋아요 목록에 상품 1 이 있다
GET {{commerce-api}}/api/v1/users/me/likes
X-Loopers-LoginId: seeduser01

### 확인 - 다른 회원의 목록은 비어 있다
// 좋아요는 회원별로 독립이다.
GET {{commerce-api}}/api/v1/users/me/likes
X-Loopers-LoginId: seeduser02

### 확인 - 페이징 파라미터 위반 (400 Bad Request)
GET {{commerce-api}}/api/v1/users/me/likes?size=101
X-Loopers-LoginId: seeduser01

### 좋아요 취소 - 헤더 누락 (400 Bad Request)
DELETE {{commerce-api}}/api/v1/products/1/likes

### 좋아요 취소 - 존재하지 않는 상품 (404 Not Found)
DELETE {{commerce-api}}/api/v1/products/99999/likes
X-Loopers-LoginId: seeduser01

### 좋아요 취소 - 좋아요하지 않은 상품 (200 OK, 좋아요 수 불변)
// 상품 2 는 좋아요한 적이 없다. 그래도 404 가 아니라 200 이다.
// "좋아요하지 않은 상태" 가 요청자가 원한 결과이기 때문이다.
DELETE {{commerce-api}}/api/v1/products/2/likes
X-Loopers-LoginId: seeduser01

### 좋아요 취소
DELETE {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

### 좋아요 취소 - 중복 (200 OK, 좋아요 수 불변)
DELETE {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

### 확인 - 좋아요 수가 기준선으로 돌아왔다
// 취소를 두 번 보냈지만 1 만 줄어야 한다.
GET {{commerce-api}}/api/v1/products/1

### 확인 - 내 좋아요 목록이 다시 비었다
GET {{commerce-api}}/api/v1/users/me/likes
X-Loopers-LoginId: seeduser01

### 재등록 - 취소한 상품을 다시 좋아요한다
// 유니크 제약 때문에 새 행을 넣을 수 없어, 기존 행을 되살리는 경로다.
POST {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

### 확인 - 재등록해도 좋아요 수가 정확히 1 만 늘었다
GET {{commerce-api}}/api/v1/products/1

### 정렬 확인 - 두 번째 상품을 좋아요한다
POST {{commerce-api}}/api/v1/products/2/likes
X-Loopers-LoginId: seeduser01

### 정렬 확인 - 목록 맨 앞이 방금 좋아요한 상품 2 다
// 최근 좋아요 순 정렬이다.
GET {{commerce-api}}/api/v1/users/me/likes
X-Loopers-LoginId: seeduser01

### 정렬 확인 - 상품 1 을 취소했다 다시 좋아요한다
DELETE {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

###
POST {{commerce-api}}/api/v1/products/1/likes
X-Loopers-LoginId: seeduser01

### 정렬 확인 - 목록 맨 앞이 다시 상품 1 이다
// created_at 으로 정렬했다면 상품 1 이 뒤에 남는다. updated_at 정렬의 근거다.
GET {{commerce-api}}/api/v1/users/me/likes
X-Loopers-LoginId: seeduser01
```

- [ ] **Step 4: `.http` 를 위에서 아래로 실행해 확인한다**

애플리케이션이 `local` 프로필로 떠 있는 상태에서 IntelliJ HTTP Client 로 순서대로 실행한다.
각 요청의 주석에 적힌 기대 상태 코드와 좋아요 수 변화를 눈으로 확인한다.

- [ ] **Step 5: 전체 테스트와 커밋**

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
git add apps/commerce-api/src/main/kotlin/com/loopers/support/seed/LocalDataSeeder.kt \
        http/commerce-api/like-v1.http
git commit -m "feat : 시드 회원과 좋아요 .http 요청 파일 추가

시더가 회원을 넣지 않아 .http 로 좋아요를 확인하려면 매번 가입부터 해야 했다.
loginId 를 seeduser01~03 으로 두어 user-v1.http 의 loopers01 과 겹치지 않게 한다.

시드의 like_count 는 좋아요 행 없는 합성 값으로 유지한다.
좋아요 API 는 상대 증감만 하므로 출발값이 무엇이든 정확하게 동작하며,
.http 도 절대값이 아니라 ±1 변화를 확인한다."
```

---

## 완료 확인

- [ ] `./gradlew :apps:commerce-api:test` 전체 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과
- [ ] `LikeFacadeConcurrencyTest` 3건 통과 — **이 셋이 설계 문서 6장의 유일한 방어선이다**
- [ ] `like-v1.http` 를 위에서 아래로 실행해 좋아요 수가 정확히 ±1 만 움직이는 것을 눈으로 확인
- [ ] `UserV1ApiE2ETest` / `ProductV1ApiE2ETest` / 어드민 E2E 가 모두 그대로 통과 (기존 계약 무손상)

### 설계 문서와 대조

| 설계 문서 | 구현 위치 |
|---|---|
| 4.2 `{userId}` → `me` | `UserLikeV1Controller` (Task 9) |
| 4.4 `200` 통일 | `ProductLikeV1Controller` (Task 8) |
| 4.5 `updated_at DESC, id DESC` | `ProductLikeJpaRepository.findLikedProductIds` (Task 6) |
| 5.4 유니크 제약이 삭제 행 포함 | `ProductLikeModelPersistenceTest` (Task 1) |
| 6.2 엔티티 메서드를 쓰지 않음 | `ProductLikeRepository.restore/softDelete` (Task 2) |
| 6.3 영향 행 수로 전이 판정 | `LikeService.like/unlike` (Task 2) |
| 6.4 원자적 증감 + 음수 가드 | `ProductJpaRepository` (Task 3) |
| 6.5 `LikeCount` 역할 변화 | `LikeCount.kt` 주석 (Task 3 Step 8) |
| 6.6 선조회 | `LikeService.like` (Task 2) |
| 6.8 경합 흡수 | `LikeFacade.like` (Task 4) |
| 6.9 `TransactionTemplate` | `LikeFacade` (Task 4) |
| 7.2 컨트롤러 분리 | Task 8 / Task 9 |
| 7.3 조인 대신 조합 | `LikeFacade.getLikedProducts` (Task 6) |
| 7.4 연쇄 삭제 | Task 7 |
| 8.1 에러 계약 | E2E 테스트 (Task 8, 9) |
| 9.1 시드 회원 | `LocalDataSeeder` (Task 10) |
| 10.3 동시성 회귀 테스트 | Task 5 |

### 이 계획이 남기는 후속 과제

설계 문서 11장의 8건은 **이번 범위에서 해결하지 않는다.** 인증(11.1)에 착수할 때 그 문서를 먼저 읽는다.
여기에 이 계획이 새로 만든 것 하나를 더한다.

- **브랜드 결합 코드가 `ProductFacade.loadBrands` 와 `LikeFacade.getLikedProducts` 두 곳에 중복된다.**
  "삭제된 브랜드는 `brand = null`" 이라는 규칙이 두 곳에 흩어져 있다. 호출부가 둘뿐이라 지금은 남긴다.
  세 번째가 생기면 공통 조립기로 뽑는다 — 그때는 분모가 커져 값을 한다.
