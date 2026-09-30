# 좋아요 수 보정 배치 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** `products.like_count` 가 실제 좋아요 행 수와 어긋난 상품을 찾아, 운영 중인 좋아요 트래픽을 막지 않고
틀린 값을 새로 만들지 않으면서 되돌리는 배치 잡 `likeCountReconcileJob` 을 commerce-batch 에 추가한다.

**아키텍처:** Tasklet 스텝 1 개. ① 락 없는 집계 쿼리 1 회로 후보를 뽑고, ② 후보마다 READ COMMITTED 트랜잭션 1 개에서
상품 행을 `FOR UPDATE` 로 잠근 **뒤** 락 없이 다시 세어, 다를 때만 덮어쓴다. commerce-batch 는 commerce-api 도메인을 모르므로
SQL 은 `JdbcTemplate` 으로 직접 쓴다. 잡 파라미터 `dryRun` 의 기본값은 `true` 다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Spring Batch 5 / `JdbcTemplate` / MySQL 8.0 /
JUnit 5 · AssertJ · mockito-kotlin (`@MockitoSpyBean`) / Testcontainers

**설계 문서:** `docs/superpowers/specs/2026-09-28-like-count-reconcile-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **응답·주석·커밋 메시지·문서는 한국어.** 변수명·함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백이 있다. (`feat : ...`, `test : ...`, `docs : ...`)
  커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- **`modules/` 와 저장소 루트의 `supports/` 를 수정하지 않는다.** `modules/jpa` 의 테스트 픽스처(`DatabaseCleanUp`)도 고치지 않는다.
  `DatabaseCleanUp` 은 JPA 엔티티만 비우는데 commerce-batch 에는 엔티티가 없다 — 이 계획의 테스트는 자기 테이블을 스스로 비운다.
- **commerce-api 의 main 코드는 Task 4 의 KDoc 한 단락 외에 건드리지 않는다.**
- **`product_likes.product_id` 인덱스를 추가하지 않는다.** 테스트 DDL 에도 넣지 않는다 — 넣으면 락 동작이 운영과 달라져 Task 3 의 경합 테스트가 다른 것을 검증하게 된다. (2026-09-28 설계 문서 1 장 제외 표, 2026-08-20 설계 문서 11.7 장)
- **`ktlintFormat` 을 실행하지 않는다.** 무관한 파일까지 건드린다. 검증은 `ktlintCheck` 로 한다.
- **ktlint 최대 줄 길이 130 자** (유니코드 문자 수 기준). `*Test.kt` 는 예외다. star import 금지.
- **블록 주석 안에 `/**` 를 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되어 `Unclosed comment` 로 컴파일이 깨진다.
- **주석은 "무엇" 이 아니라 "왜" 를 적는다.** 이 계획이 새로 쓰는 인용은 전부 `(2026-09-28 설계 문서 N 장)` 형식이다.
  좋아요 API 설계를 가리킬 때는 `(2026-08-20 설계 문서 N 장)` 로 적는다. **날짜 없는 `(설계 문서 N 장)` 을 새로 쓰지 않는다.**
- **`@Transactional` 을 테스트 클래스·메서드에 붙이지 않는다.** 붙이면 경합 테스트의 스레드가 각자의 트랜잭션을 갖지 못하고,
  보정 트랜잭션(`REQUIRES_NEW`)과 테스트 데이터가 서로 보이지 않게 된다.
- **모든 Gradle 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** 이 디렉터리가 Gradle 루트다.
  단 **Git 루트는 상위 `study-project/` 다** — `git status` 는 경로를 좁혀서 본다.
  - 전체: `./gradlew :apps:commerce-batch:test`
  - 단일 클래스: `./gradlew :apps:commerce-batch:test --tests 'com.loopers.<FQCN>'`
  - 린트: `./gradlew :apps:commerce-batch:ktlintCheck`
- 테스트는 Testcontainers 로 MySQL 8.0 을 띄운다. **Docker 가 실행 중이어야 한다.**

## 리뷰 초점

설계 문서가 암시하지만 정상 경로 테스트만으로는 드러나지 않는, 사용자를 실제로 물 가능성이 큰 입력·조건이다.
각 줄의 테스트는 담당 태스크에 들어가 있다.

1. **`dryRun` 오타(`flase`, `ture`, 빈 문자열)** — 조용히 덮어쓰기로 가면 안 된다. 아무것도 바꾸지 않고 잡이 실패해야 한다. → Task 2 `failsWithoutWriting_whenDryRunIsNotBoolean`
2. **좋아요 행이 하나도 없는데 카운트가 양수인 상품** — `INNER JOIN` 으로 쓰면 정확히 이 방향이 빠진다(2026-09-28 설계 문서 3.1 장). → Task 1 `includesProductsWithoutAnyLikeRow`
3. **탐지와 재검증 사이에 상품이 삭제됨** — 보정도 실패도 아닌 건너뜀이어야 한다. → Task 1 `returnsProductGone_whenProductIsDeleted`
4. **후보 하나의 실패(락 대기 초과 등)** — 나머지 후보는 보정되고, 잡은 `FAILED` 로 끝나 재실행 대상임이 드러나야 한다. → Task 2 `correctsOthersAndFails_whenOneProductFails`
5. **보정 직후 재실행** — 두 번째 실행은 후보 0 건으로 정상 종료해야 한다. → Task 2 `findsNothing_whenRunAgainAfterCorrection`
6. **직전 실행이 `dryRun=false` 였고 이번엔 생략** — Boot 러너는 직전 파라미터를 incrementer 에 넘기고 `RunIdIncrementer` 는 그것을 복사하므로
   `dryRun=false` 가 조용히 이어진다(2026-09-28 설계 문서 2.2 장). 덮어쓰지 않아야 한다. → Task 2 `doesNotCarryOverDryRun_whenOmittedOnNextLaunch`

---

## 기준선

- 브랜치: `main` HEAD `3935a73e` (설계 문서 커밋). Task 1 의 첫 단계에서 작업 브랜치를 만든다.
- `./gradlew :apps:commerce-batch:test` → **3 tests** (`CommerceBatchApplicationTest` 1, `DemoJobE2ETest` 2)
  - 2026-09-28 계획 작성 시점에는 Docker 데몬이 꺼져 있어 3 건 모두 컨텍스트 로드 단계에서 실패했다(`Could not find a valid Docker environment`).
    **통과 여부는 Task 1 Step 1 에서 실측한다.** Docker 를 켜고도 실패하면 이 계획을 진행하지 말고 멈춰 보고한다.

---

## 파일 구조

### 신규 — main (`apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/`)

| 파일 | 책임 |
|---|---|
| `LikeCountReconcileRepository.kt` | SQL 4 개 — 후보 조회, 상품 행 잠금 조회, 활성 좋아요 수, 카운트 덮어쓰기. 판단은 하지 않는다 |
| `LikeCountReconciler.kt` | 상품 하나의 재검증·보정 트랜잭션(`REQUIRES_NEW`, `READ_COMMITTED`)과 결과 타입 `ReconcileOutcome` |
| `LikeCountReconcileJobConfig.kt` | 잡·스텝 빈. `DemoJobConfig` 와 같은 형태 |
| `step/LikeCountReconcileTasklet.kt` | `dryRun` 해석, 후보 순회, 실패 집계, 로그, `readCount` / `writeCount` |

Repository 와 Reconciler 를 나누는 이유: Task 3 의 경합 테스트가 **"센 뒤, 쓰기 전"** 에 보정 트랜잭션을 멈춰야 한다.
한 클래스 안의 호출은 스파이가 가로채지 못하므로 SQL 을 별도 빈으로 둔다. Task 2 의 실패 주입도 같은 이음매를 쓴다.

### 신규 — test (`apps/commerce-batch/src/test/`)

| 파일 | 책임 |
|---|---|
| `resources/sql/like-count-reconcile-schema.sql` | `products` · `product_likes` 테스트 DDL (배치가 쓰는 컬럼만) |
| `kotlin/com/loopers/job/likecount/LikeCountTables.kt` | 테스트 데이터 삽입·조회·정리 |
| `kotlin/com/loopers/job/likecount/LikeCountReconcilerIntegrationTest.kt` | Task 1 |
| `kotlin/com/loopers/job/likecount/LikeCountReconcileJobE2ETest.kt` | Task 2 |
| `kotlin/com/loopers/job/likecount/LikeCountReconcileConcurrencyTest.kt` | Task 3 |

### 수정 (Task 4)

- `docs/superpowers/specs/2026-08-20-product-like-design.md` — 2 장 제외 표, 11.3 장 머리
- `docs/superpowers/specs/2026-09-28-like-count-reconcile-design.md` — 상태 줄
- `apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt` — 클래스 KDoc 한 단락
- `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconciler.kt` — `REQUIRES_NEW` 근거 단락
- `CLAUDE.md` (Gradle 루트) — 테스트 명령, 새 파일 위치 규칙의 예외

---

## 태스크 개요

| # | 태스크 | 산출물 | 테스트 |
|---|---|---|---|
| 1 | 탐지·재검증 SQL 과 보정 트랜잭션 | Repository, Reconciler, 테스트 DDL·헬퍼 | 통합 7 |
| 2 | 잡 · Tasklet | JobConfig, Tasklet | E2E 6 |
| 3 | 경합 테스트 | (테스트만) | 경합 3 |
| 4 | 문서 갱신 | 설계 문서 2 개, KDoc 2 개, CLAUDE.md | — |

태스크가 끝날 때마다 멈춰 보고하고 다음 진행 여부를 묻는다.

---

### Task 1: 탐지·재검증 SQL 과 보정 트랜잭션

**파일:**
- 생성: `apps/commerce-batch/src/test/resources/sql/like-count-reconcile-schema.sql`
- 생성: `apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountTables.kt`
- 생성: `apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcilerIntegrationTest.kt`
- 생성: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconcileRepository.kt`
- 생성: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconciler.kt`

**인터페이스:**
- 사용: 없음 (첫 태스크)
- 제공:
  - `com.loopers.batch.job.likecount.LikeCountReconcileRepository`
    - `fun findCandidateProductIds(): List<Long>`
    - `fun lockLikeCount(productId: Long): Long?`
    - `fun countActiveLikes(productId: Long): Long`
    - `fun updateLikeCount(productId: Long, likeCount: Long): Int`
  - `com.loopers.batch.job.likecount.LikeCountReconciler`
    - `fun findCandidateProductIds(): List<Long>`
    - `fun reconcile(productId: Long): ReconcileOutcome`
  - `sealed interface ReconcileOutcome` — `data class Corrected(val before: Long, val after: Long)`, `data object AlreadyConsistent`, `data object ProductGone`
  - 테스트 헬퍼 `com.loopers.job.likecount.LikeCountTables(jdbcTemplate)` — `insertProduct(id, likeCount, deleted = false)`,
    `insertLike(productId, userId, deleted = false)`, `likeCountOf(productId)`, `activeLikeCountOf(productId)`,
    `updatedAtOf(productId): LocalDateTime`, `truncate()`, `FIXED_TIME`

- [ ] **Step 0: 작업 브랜치를 만든다**

```bash
git switch -c feature/like-count-reconcile
```

- [ ] **Step 1: 기준선 실측**

Docker 가 실행 중인지 먼저 확인한다(`docker info`). 그다음:

```bash
./gradlew :apps:commerce-batch:test
```

기대: **3 tests / 0 failures**. 실패하면 멈추고 보고한다 — 이 계획의 기대 테스트 수가 전부 이 값에서 출발한다.

- [ ] **Step 2: 테스트 DDL 을 쓴다**

`apps/commerce-batch/src/test/resources/sql/like-count-reconcile-schema.sql`

```sql
-- 좋아요 수 보정 배치 테스트용 스키마.
--
-- commerce-batch 에는 엔티티가 없어 ddl-auto 가 이 테이블들을 만들지 않는다.
-- 배치가 쓰는 컬럼과 유니크 제약만 원본과 맞춘다. 원본은 아래 두 엔티티다 — 컬럼 이름이 바뀌면 여기도 고친다.
--   apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt
--   apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeModel.kt
--
-- product_likes.product_id 인덱스는 일부러 두지 않는다. 운영에도 없고(2026-08-20 설계 문서 11.7 장),
-- 있으면 락 범위가 달라져 경합 테스트가 운영과 다른 것을 검증하게 된다. (2026-09-28 설계 문서 4.1 장)
CREATE TABLE IF NOT EXISTS products (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    like_count BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS product_likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_likes_user_product UNIQUE (user_id, product_id)
);
```

- [ ] **Step 3: 테스트 헬퍼를 쓴다**

`apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountTables.kt`

```kotlin
package com.loopers.job.likecount

import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

/**
 * 보정 배치 테스트의 테이블 조작.
 *
 * commerce-batch 에는 엔티티가 없어 modules/jpa 의 DatabaseCleanUp 이 이 테이블들을 모른다. 정리도 여기서 한다.
 * 상품 ID 를 호출자가 정하는 것은 AUTO_INCREMENT 값을 되읽지 않기 위해서다 —
 * JdbcTemplate 은 문장마다 풀에서 다른 커넥션을 받을 수 있어 LAST_INSERT_ID() 를 믿을 수 없다.
 */
class LikeCountTables(private val jdbcTemplate: JdbcTemplate) {
    fun insertProduct(id: Long, likeCount: Long, deleted: Boolean = false) {
        jdbcTemplate.update(
            "INSERT INTO products (id, like_count, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
            id,
            likeCount,
            FIXED_TIME,
            FIXED_TIME,
            if (deleted) FIXED_TIME else null,
        )
    }

    fun insertLike(productId: Long, userId: Long, deleted: Boolean = false) {
        jdbcTemplate.update(
            "INSERT INTO product_likes (user_id, product_id, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
            userId,
            productId,
            FIXED_TIME,
            FIXED_TIME,
            if (deleted) FIXED_TIME else null,
        )
    }

    fun likeCountOf(productId: Long): Long =
        jdbcTemplate.queryForObject("SELECT like_count FROM products WHERE id = ?", Long::class.javaObjectType, productId)!!

    fun activeLikeCountOf(productId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_likes WHERE product_id = ? AND deleted_at IS NULL",
            Long::class.javaObjectType,
            productId,
        )!!

    fun updatedAtOf(productId: Long): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT updated_at FROM products WHERE id = ?", LocalDateTime::class.java, productId)!!

    fun truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE product_likes")
        jdbcTemplate.execute("TRUNCATE TABLE products")
    }

    companion object {
        val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
```

- [ ] **Step 4: 실패하는 테스트를 쓴다**

`apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcilerIntegrationTest.kt`

```kotlin
package com.loopers.job.likecount

import com.loopers.batch.job.likecount.LikeCountReconciler
import com.loopers.batch.job.likecount.ReconcileOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql

@SpringBootTest
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
@Sql(scripts = ["/sql/like-count-reconcile-schema.sql"])
class LikeCountReconcilerIntegrationTest @Autowired constructor(
    private val reconciler: LikeCountReconciler,
    jdbcTemplate: JdbcTemplate,
) {
    private val tables = LikeCountTables(jdbcTemplate)

    @AfterEach
    fun tearDown() {
        tables.truncate()
    }

    @DisplayName("보정 후보를 찾을 때, ")
    @Nested
    inner class FindCandidates {
        @DisplayName("카운트가 활성 좋아요 수와 다른, 삭제되지 않은 상품만 ID 오름차순으로 돌려준다.")
        @Test
        fun returnsOnlyMismatchedLiveProducts() {
            // arrange
            tables.insertProduct(id = 1L, likeCount = 1L) // 실제 3 — 작다
            (1L..3L).forEach { tables.insertLike(productId = 1L, userId = it) }
            tables.insertProduct(id = 2L, likeCount = 2L) // 실제 2 — 정합
            (1L..2L).forEach { tables.insertLike(productId = 2L, userId = it) }
            tables.insertProduct(id = 3L, likeCount = 1L) // 활성 1 + 취소 1 — 정합
            tables.insertLike(productId = 3L, userId = 1L)
            tables.insertLike(productId = 3L, userId = 2L, deleted = true)
            tables.insertProduct(id = 4L, likeCount = 9L, deleted = true) // 어긋났지만 삭제됨
            tables.insertProduct(id = 5L, likeCount = 0L) // 행 없음, 0 — 정합

            // act
            val candidates = reconciler.findCandidateProductIds()

            // assert
            assertThat(candidates).containsExactly(1L)
        }

        @DisplayName("좋아요 행이 하나도 없는데 카운트가 양수인 상품도 후보가 된다.")
        @Test
        fun includesProductsWithoutAnyLikeRow() {
            // arrange
            tables.insertProduct(id = 1L, likeCount = 5L)
            tables.insertProduct(id = 2L, likeCount = 3L)
            tables.insertLike(productId = 2L, userId = 1L, deleted = true) // 취소된 행만 있음

            // act
            val candidates = reconciler.findCandidateProductIds()

            // assert
            assertThat(candidates).containsExactly(1L, 2L)
        }
    }

    @DisplayName("상품 하나를 보정할 때, ")
    @Nested
    inner class Reconcile {
        @DisplayName("카운트가 실제보다 크면, 활성 좋아요 수로 덮어쓰고 이전·이후 값을 돌려준다.")
        @Test
        fun overwritesWithActiveLikeCount_whenCountIsTooLarge() {
            // arrange
            tables.insertProduct(id = 1L, likeCount = 5L)
            tables.insertLike(productId = 1L, userId = 1L)
            tables.insertLike(productId = 1L, userId = 2L)
            tables.insertLike(productId = 1L, userId = 3L, deleted = true)

            // act
            val outcome = reconciler.reconcile(1L)

            // assert
            assertAll(
                { assertThat(outcome).isEqualTo(ReconcileOutcome.Corrected(before = 5L, after = 2L)) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(2L) },
            )
        }

        @DisplayName("카운트가 실제보다 작으면, 활성 좋아요 수로 덮어쓴다.")
        @Test
        fun overwritesWithActiveLikeCount_whenCountIsTooSmall() {
            // arrange
            tables.insertProduct(id = 1L, likeCount = 0L)
            (1L..4L).forEach { tables.insertLike(productId = 1L, userId = it) }

            // act
            val outcome = reconciler.reconcile(1L)

            // assert
            assertAll(
                { assertThat(outcome).isEqualTo(ReconcileOutcome.Corrected(before = 0L, after = 4L)) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(4L) },
            )
        }

        @DisplayName("덮어써도 updated_at 은 바뀌지 않는다.")
        @Test
        fun keepsUpdatedAt() {
            // arrange
            tables.insertProduct(id = 1L, likeCount = 5L)

            // act
            reconciler.reconcile(1L)

            // assert
            assertAll(
                { assertThat(tables.likeCountOf(1L)).isEqualTo(0L) },
                { assertThat(tables.updatedAtOf(1L)).isEqualTo(LikeCountTables.FIXED_TIME) },
            )
        }

        @DisplayName("이미 정합하면, 쓰지 않고 AlreadyConsistent 를 돌려준다.")
        @Test
        fun returnsAlreadyConsistent_whenCountMatches() {
            // arrange
            tables.insertProduct(id = 1L, likeCount = 1L)
            tables.insertLike(productId = 1L, userId = 1L)

            // act
            val outcome = reconciler.reconcile(1L)

            // assert
            assertAll(
                { assertThat(outcome).isEqualTo(ReconcileOutcome.AlreadyConsistent) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(1L) },
            )
        }

        @DisplayName("상품이 삭제됐으면, 건드리지 않고 ProductGone 을 돌려준다.")
        @Test
        fun returnsProductGone_whenProductIsDeleted() {
            // arrange — 탐지 뒤 재검증 전에 삭제된 상황과 같다
            tables.insertProduct(id = 1L, likeCount = 5L, deleted = true)

            // act
            val outcome = reconciler.reconcile(1L)

            // assert
            assertAll(
                { assertThat(outcome).isEqualTo(ReconcileOutcome.ProductGone) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
            )
        }
    }
}
```

`returnsProductGone_whenProductDoesNotExist` 는 두지 않는다. 행이 없는 경우와 삭제된 경우는 같은 `SELECT ... WHERE deleted_at IS NULL` 이 똑같이 빈 결과로 처리하므로 테스트가 새로 검증하는 것이 없다.

- [ ] **Step 5: 실패를 확인한다**

```bash
./gradlew :apps:commerce-batch:test --tests 'com.loopers.job.likecount.LikeCountReconcilerIntegrationTest'
```

기대: 컴파일 실패 — `Unresolved reference: LikeCountReconciler`, `ReconcileOutcome`.

- [ ] **Step 6: Repository 를 쓴다**

`apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconcileRepository.kt`

```kotlin
package com.loopers.batch.job.likecount

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

/**
 * 좋아요 수 보정에 쓰는 SQL. 판단은 하지 않고 SQL 만 담는다.
 *
 * commerce-batch 는 commerce-api 를 의존하지 않아 ProductModel 을 모른다. 테이블과 컬럼 이름을 직접 쓰는 대가로
 * 스키마가 바뀌어도 컴파일러가 알려주지 않는다. (2026-09-28 설계 문서 2.3 장)
 *
 * LikeCountReconciler 와 나눈 것은 테스트 이음매 때문이다 — 경합 테스트가 "센 뒤, 쓰기 전" 에 트랜잭션을 멈춰야 하는데,
 * 한 클래스 안의 호출은 스파이가 가로채지 못한다.
 *
 * 잡 이름 조건(@ConditionalOnProperty)을 달지 않는다. 잡 없이 보정 로직만 검증하는 통합 테스트가 이 빈을 쓴다.
 */
@Component
class LikeCountReconcileRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * 파생 테이블 GROUP BY 로 product_likes 를 한 번만 훑는다. 상품마다 상관 서브쿼리로 세면
     * product_id 인덱스가 없어 상품 수만큼 풀스캔이 된다. (2026-09-28 설계 문서 3.1 장)
     *
     * LEFT JOIN + COALESCE 는 "좋아요 행이 하나도 없는데 카운트가 양수" 인 방향을 잡기 위해서다.
     * 일반 SELECT 라 락을 걸지 않으므로 결과는 후보일 뿐이고, 재검증은 lockLikeCount 이후에 한다.
     */
    fun findCandidateProductIds(): List<Long> =
        jdbcTemplate.queryForList(FIND_CANDIDATES_SQL, Long::class.javaObjectType)

    /** 삭제됐거나 없는 상품이면 null. 상품 행의 X 락은 호출자 트랜잭션이 끝날 때까지 유지된다. */
    fun lockLikeCount(productId: Long): Long? =
        jdbcTemplate.query(LOCK_PRODUCT_SQL, RowMapper { rs, _ -> rs.getLong("like_count") }, productId).firstOrNull()

    /**
     * 락 없는 일반 SELECT 여야 한다. 잠금 읽기로 바꾸면 진행 중인 좋아요 트랜잭션의 미커밋 행에서 대기하고,
     * 그 트랜잭션은 우리가 쥔 상품 행에서 대기해 교착 상태가 된다. (2026-09-28 설계 문서 3.4 장)
     */
    fun countActiveLikes(productId: Long): Long =
        jdbcTemplate.queryForObject(COUNT_ACTIVE_LIKES_SQL, Long::class.javaObjectType, productId)!!

    /** updated_at 을 건드리지 않는다. 좋아요 증감 UPDATE 와 같은 이유다. (2026-08-20 설계 문서 6.4 장) */
    fun updateLikeCount(productId: Long, likeCount: Long): Int =
        jdbcTemplate.update(UPDATE_LIKE_COUNT_SQL, likeCount, productId)

    private companion object {
        const val FIND_CANDIDATES_SQL = """
            SELECT p.id
              FROM products p
              LEFT JOIN (SELECT product_id, COUNT(*) AS cnt
                           FROM product_likes
                          WHERE deleted_at IS NULL
                          GROUP BY product_id) l ON l.product_id = p.id
             WHERE p.deleted_at IS NULL
               AND p.like_count <> COALESCE(l.cnt, 0)
             ORDER BY p.id
        """

        const val LOCK_PRODUCT_SQL = """
            SELECT like_count FROM products WHERE id = ? AND deleted_at IS NULL FOR UPDATE
        """

        const val COUNT_ACTIVE_LIKES_SQL = """
            SELECT COUNT(*) FROM product_likes WHERE product_id = ? AND deleted_at IS NULL
        """

        const val UPDATE_LIKE_COUNT_SQL = """
            UPDATE products SET like_count = ? WHERE id = ?
        """
    }
}
```

- [ ] **Step 7: Reconciler 를 쓴다**

`apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconciler.kt`

```kotlin
package com.loopers.batch.job.likecount

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 하나의 좋아요 수를 재검증하고 보정한다.
 *
 * 순서가 전부다 — 상품 행을 먼저 잠그고, 그 뒤에 락 없이 센다. LikeFacade 는 좋아요 행을 먼저 바꾸고
 * 상품 행을 나중에 갱신하므로, 이 순서면 진행 중인 좋아요 트랜잭션이 어느 지점에 있어도 최종값이 맞는다.
 * (2026-09-28 설계 문서 3.3 장)
 *
 * READ_COMMITTED 를 명시하는 이유: 세는 SELECT 의 스냅샷이 잠금 이후에 잡혀야 한다.
 * REPEATABLE READ 에서도 지금 순서로는 우연히 맞지만, 잠금 앞에 일반 SELECT 가 하나라도 끼어들면
 * 스냅샷이 잠금 이전으로 당겨져 깨진다. 그 전제를 코드 순서가 아니라 설정이 보장하게 한다.
 *
 * REQUIRES_NEW 인 이유: 스텝은 ResourcelessTransactionManager 로 돈다. 상품마다 트랜잭션을 끊어야
 * 한 상품의 락이 다음 상품을 처리하는 동안 남지 않는다. (2026-09-28 설계 문서 3.2 장)
 */
@Component
class LikeCountReconciler(
    private val repository: LikeCountReconcileRepository,
) {
    fun findCandidateProductIds(): List<Long> = repository.findCandidateProductIds()

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    fun reconcile(productId: Long): ReconcileOutcome {
        val before = repository.lockLikeCount(productId) ?: return ReconcileOutcome.ProductGone
        val actual = repository.countActiveLikes(productId)
        if (before == actual) {
            return ReconcileOutcome.AlreadyConsistent
        }
        repository.updateLikeCount(productId, actual)
        return ReconcileOutcome.Corrected(before = before, after = actual)
    }
}

sealed interface ReconcileOutcome {
    data class Corrected(val before: Long, val after: Long) : ReconcileOutcome

    /** 탐지 시점엔 어긋나 보였지만 잠근 뒤 다시 세니 맞았다. 진행 중이던 좋아요가 그 사이 커밋된 경우다. */
    data object AlreadyConsistent : ReconcileOutcome

    /** 탐지 뒤에 삭제됐다. 삭제된 상품의 카운트는 맞출 대상이 아니다. (2026-08-20 설계 문서 7.4 장) */
    data object ProductGone : ReconcileOutcome
}
```

- [ ] **Step 8: 통과를 확인한다**

```bash
./gradlew :apps:commerce-batch:test --tests 'com.loopers.job.likecount.LikeCountReconcilerIntegrationTest'
```

기대: **7 tests / 0 failures**.

- [ ] **Step 9: 전체 테스트와 린트**

```bash
./gradlew :apps:commerce-batch:test :apps:commerce-batch:ktlintCheck
```

기대: **10 tests / 0 failures** (기준선 3 + 7), ktlint 통과.

- [ ] **Step 10: 커밋**

```bash
git add apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount \
        apps/commerce-batch/src/test/resources/sql/like-count-reconcile-schema.sql \
        apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount
git commit -m "feat : 좋아요 수 보정의 탐지 쿼리와 상품 단위 보정 트랜잭션을 추가한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 잡 · Tasklet

**파일:**
- 생성: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconcileJobConfig.kt`
- 생성: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/step/LikeCountReconcileTasklet.kt`
- 생성: `apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcileJobE2ETest.kt`

**인터페이스:**
- 사용: Task 1 의 `LikeCountReconciler.findCandidateProductIds()`, `LikeCountReconciler.reconcile(productId): ReconcileOutcome`,
  `LikeCountReconcileRepository.lockLikeCount(productId)`(테스트의 실패 주입), `LikeCountTables`
- 제공: `LikeCountReconcileJobConfig.JOB_NAME = "likeCountReconcileJob"`. 잡 파라미터 `dryRun`(String, `true`/`false`, 대소문자 무시, 생략 시 `true`).
  스텝 `readCount` = 탐지된 후보 수, `writeCount` = 실제로 보정한 상품 수.

**배경:**
- `dryRun` 을 파싱할 수 없으면 **탐지보다 먼저** 예외를 던진다. `"flase".toBoolean()` 은 `false` 라 Kotlin 기본 변환을 쓰면 오타가 곧 덮어쓰기가 된다. (리뷰 초점 1)
- 상품 하나의 실패는 잡아서 세고 다음으로 넘어간다. 끝에 하나라도 있으면 예외를 던져 스텝을 `FAILED` 로 만든다.
  `ExitStatus` 만 바꾸면 `BatchStatus` 가 `COMPLETED` 로 남아 같은 파라미터로 재실행할 수 없다. 이미 보정된 상품은 상품마다 커밋됐으므로 남는다.
- 잡는 예외는 `DataAccessException`(락 대기 초과·교착 상태 희생 등 SQL 실패)과 `TransactionException`(커밋 실패)이다. 그 밖의 예외는 버그이므로 잡지 않고 즉시 스텝을 끝낸다.
- `StepContribution` 에는 읽기 건수를 한 번에 더하는 메서드가 없어 `incrementReadCount()` 를 후보 수만큼 부른다.
- 테스트에서 `spring.batch.job.enabled=false` 를 준다. 안 주면 컨텍스트 기동 시 Boot 의 `JobLauncherApplicationRunner` 가 테이블 생성(`@Sql`) 전에 잡을 한 번 돌린다.
- **`RunIdIncrementer` 를 쓰지 않는다** (리뷰 초점 6, 2026-09-28 설계 문서 2.2 장). Boot 러너는 `getNextJobParameters(job)` 로 직전 실행의 파라미터를
  incrementer 에 넘기고, `RunIdIncrementer` 는 그것을 복사한 채 `run.id` 만 올린다. `run.id` 만 새로 만드는 incrementer 를 쓴다.
  `uniqueJobParametersBuilder` 는 incrementer 를 거치지 않으므로, 이 경로를 검증하는 테스트는 `JobParametersBuilder(jobExplorer).getNextJobParameters(job)` 로
  Boot 러너와 같은 방식으로 파라미터를 만든다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcileJobE2ETest.kt`

```kotlin
package com.loopers.job.likecount

import com.loopers.batch.job.likecount.LikeCountReconcileJobConfig
import com.loopers.batch.job.likecount.LikeCountReconcileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.CannotAcquireLockException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest
@SpringBatchTest
@TestPropertySource(
    properties = [
        "spring.batch.job.name=${LikeCountReconcileJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
    ],
)
@Sql(scripts = ["/sql/like-count-reconcile-schema.sql"])
class LikeCountReconcileJobE2ETest @Autowired constructor(
    // IDE 정적 분석 상 [SpringBatchTest] 의 주입보다 [SpringBootTest] 의 주입이 우선되어 오류처럼 보일 수 있으나 정상 동작한다. (DemoJobE2ETest 와 같다)
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(LikeCountReconcileJobConfig.JOB_NAME) private val job: Job,
    private val jobExplorer: JobExplorer,
    jdbcTemplate: JdbcTemplate,
) {
    @MockitoSpyBean
    private lateinit var repository: LikeCountReconcileRepository

    private val tables = LikeCountTables(jdbcTemplate)

    @AfterEach
    fun tearDown() {
        tables.truncate()
    }

    /** 어긋난 상품 1·2, 정합한 상품 3, 어긋났지만 삭제된 상품 4. */
    private fun arrangeProducts() {
        tables.insertProduct(id = 1L, likeCount = 5L) // 실제 0
        tables.insertProduct(id = 2L, likeCount = 0L) // 실제 2
        tables.insertLike(productId = 2L, userId = 1L)
        tables.insertLike(productId = 2L, userId = 2L)
        tables.insertProduct(id = 3L, likeCount = 1L) // 실제 1
        tables.insertLike(productId = 3L, userId = 1L)
        tables.insertProduct(id = 4L, likeCount = 7L, deleted = true)
    }

    private fun launch(dryRun: String?): JobExecution {
        jobLauncherTestUtils.job = job
        val builder = jobLauncherTestUtils.uniqueJobParametersBuilder
        dryRun?.let { builder.addString("dryRun", it) }
        return jobLauncherTestUtils.launchJob(builder.toJobParameters())
    }

    /**
     * `--job.name` 기동 때 Boot 의 JobLauncherApplicationRunner 가 파라미터를 만드는 방식과 같다 —
     * 직전 실행의 파라미터를 잡의 incrementer 에 넘긴다. uniqueJobParametersBuilder 는 incrementer 를 거치지 않는다.
     */
    private fun launchLikeBootRunner(): JobExecution {
        jobLauncherTestUtils.job = job
        return jobLauncherTestUtils.launchJob(JobParametersBuilder(jobExplorer).getNextJobParameters(job).toJobParameters())
    }

    @DisplayName("dryRun 을 주지 않으면, ")
    @Nested
    inner class DefaultDryRun {
        @DisplayName("후보만 세고 아무것도 덮어쓰지 않는다.")
        @Test
        fun countsCandidatesWithoutWriting() {
            // arrange
            arrangeProducts()

            // act
            val execution = launch(dryRun = null)

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(2L) },
                { assertThat(step.writeCount).isEqualTo(0L) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(0L) },
            )
        }

        @DisplayName("직전 실행이 dryRun=false 였어도, 이전 파라미터를 이어받지 않아 덮어쓰지 않는다.")
        @Test
        fun doesNotCarryOverDryRun_whenOmittedOnNextLaunch() {
            // arrange — 한 번 보정한 뒤 새로 어긋난 상품을 만든다
            arrangeProducts()
            launch(dryRun = "false")
            tables.insertProduct(id = 5L, likeCount = 4L) // 실제 0

            // act
            val execution = launchLikeBootRunner()

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.jobParameters.getString("dryRun")).isNull() },
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(1L) },
                { assertThat(step.writeCount).isEqualTo(0L) },
                { assertThat(tables.likeCountOf(5L)).isEqualTo(4L) },
            )
        }
    }

    @DisplayName("dryRun=false 로 실행하면, ")
    @Nested
    inner class Apply {
        @DisplayName("어긋난 상품만 활성 좋아요 수로 덮어쓴다.")
        @Test
        fun correctsOnlyMismatchedLiveProducts() {
            // arrange
            arrangeProducts()

            // act
            val execution = launch(dryRun = "false")

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(2L) },
                { assertThat(step.writeCount).isEqualTo(2L) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(0L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(2L) },
                { assertThat(tables.likeCountOf(3L)).isEqualTo(1L) },
                { assertThat(tables.likeCountOf(4L)).isEqualTo(7L) },
            )
        }

        @DisplayName("보정 직후 다시 실행하면, 후보 0 건으로 정상 종료한다.")
        @Test
        fun findsNothing_whenRunAgainAfterCorrection() {
            // arrange
            arrangeProducts()
            launch(dryRun = "false")

            // act
            val second = launch(dryRun = "false")

            // assert
            val step = second.stepExecutions.single()
            assertAll(
                { assertThat(second.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(0L) },
                { assertThat(step.writeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("한 상품이 실패해도 나머지는 보정하고, 잡은 FAILED 로 끝난다.")
        @Test
        fun correctsOthersAndFails_whenOneProductFails() {
            // arrange
            arrangeProducts()
            doThrow(CannotAcquireLockException("락 대기 시간 초과")).whenever(repository).lockLikeCount(1L)

            // act
            val execution = launch(dryRun = "false")

            // assert
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(2L) },
            )
        }
    }

    @DisplayName("dryRun 이 true / false 가 아니면, ")
    @Nested
    inner class InvalidDryRun {
        @DisplayName("아무것도 덮어쓰지 않고 잡이 실패한다.")
        @Test
        fun failsWithoutWriting_whenDryRunIsNotBoolean() {
            // arrange
            arrangeProducts()

            // act
            val execution = launch(dryRun = "flase")

            // assert
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(0L) },
            )
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-batch:test --tests 'com.loopers.job.likecount.LikeCountReconcileJobE2ETest'
```

기대: 컴파일 실패 — `Unresolved reference: LikeCountReconcileJobConfig`.

- [ ] **Step 3: Tasklet 을 쓴다**

`apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/step/LikeCountReconcileTasklet.kt`

```kotlin
package com.loopers.batch.job.likecount.step

import com.loopers.batch.job.likecount.LikeCountReconcileJobConfig
import com.loopers.batch.job.likecount.LikeCountReconciler
import com.loopers.batch.job.likecount.ReconcileOutcome
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.TransactionException

/**
 * 좋아요 수 보정 스텝.
 *
 * Chunk 가 아니라 Tasklet 이다. 탐지 쿼리는 product_likes 풀스캔 집계라 페이징 리더가 페이지마다 다시 실행하면
 * 풀스캔이 페이지 수만큼 반복된다. 후보는 Long 목록이라 한 번에 읽어도 된다. (2026-09-28 설계 문서 2.1 장)
 *
 * dryRun 기본값은 true 다. 측정용 시드(loadtest/seed-products.sql)는 좋아요 행 없이 합성 like_count 를 심으므로,
 * 그 DB 에서 한 번 덮어쓰면 측정 데이터가 영구히 망가진다. (2026-09-28 설계 문서 2.2 장)
 */
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = LikeCountReconcileJobConfig.JOB_NAME)
@Component
class LikeCountReconcileTasklet(
    private val reconciler: LikeCountReconciler,
    @param:Value("#{jobParameters['dryRun']}") private val dryRunParameter: String?,
) : Tasklet {
    private val log = LoggerFactory.getLogger(LikeCountReconcileTasklet::class.java)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        // 탐지보다 먼저 해석한다. 잘못된 값이면 아무것도 읽거나 쓰기 전에 끝난다.
        val dryRun = parseDryRun(dryRunParameter)

        val candidates = reconciler.findCandidateProductIds()
        // StepContribution 에는 읽기 건수를 한 번에 더하는 메서드가 없다.
        repeat(candidates.size) { contribution.incrementReadCount() }

        if (dryRun) {
            log.warn(
                "[dryRun] 좋아요 수 불일치 후보 {} 건 — 재검증 전이라 진행 중이던 좋아요로 인한 일시적 불일치가 섞일 수 있다. 앞 {} 건 : {}",
                candidates.size,
                SAMPLE_SIZE,
                candidates.take(SAMPLE_SIZE),
            )
            return RepeatStatus.FINISHED
        }

        var failures = 0
        candidates.forEach { productId ->
            try {
                val outcome = reconciler.reconcile(productId)
                if (outcome is ReconcileOutcome.Corrected) {
                    // 어긋남은 그 자체로 버그 신호다. INFO 로 묻히지 않게 WARN 으로 남긴다. (2026-09-28 설계 문서 3.2 장)
                    log.warn("좋아요 수 보정 : productId={}, {} -> {}", productId, outcome.before, outcome.after)
                    contribution.incrementWriteCount(1)
                }
            } catch (e: DataAccessException) {
                failures++
                log.error("좋아요 수 보정 실패 : productId={}", productId, e)
            } catch (e: TransactionException) {
                failures++
                log.error("좋아요 수 보정 실패 : productId={}", productId, e)
            }
        }

        // 한 건 때문에 나머지를 멈추지 않되, 스텝은 FAILED 로 끝내 재실행 대상임을 드러낸다.
        // 이미 보정한 상품은 상품마다 커밋됐으므로 남는다.
        check(failures == 0) { "좋아요 수 보정 실패 $failures 건 — 로그를 확인하고 재실행한다." }
        return RepeatStatus.FINISHED
    }

    /** "flase".toBoolean() 은 false 다. 기본 변환을 쓰면 오타가 곧 덮어쓰기가 되므로 두 값만 받는다. */
    private fun parseDryRun(raw: String?): Boolean =
        when (raw?.lowercase()) {
            null, "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("dryRun 은 true 또는 false 여야 합니다 : $raw")
        }

    private companion object {
        const val SAMPLE_SIZE = 20
    }
}
```

- [ ] **Step 4: JobConfig 를 쓴다**

`apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconcileJobConfig.kt`

```kotlin
package com.loopers.batch.job.likecount

import com.loopers.batch.job.likecount.step.LikeCountReconcileTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.JobParametersIncrementer
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 좋아요 수 보정 잡. `--job.name=likeCountReconcileJob` 으로 기동하고, 덮어쓰려면 `dryRun=false` 를 명시한다.
 *
 * 스텝 트랜잭션이 ResourcelessTransactionManager 인 것은 의도다. 실제 DB 트랜잭션은
 * LikeCountReconciler.reconcile 이 상품마다 따로 연다. 스텝 전체를 한 트랜잭션으로 감싸면
 * 앞에서 잠근 상품 행이 스텝이 끝날 때까지 풀리지 않는다. (2026-09-28 설계 문서 3.2 장)
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = LikeCountReconcileJobConfig.JOB_NAME)
@Configuration
class LikeCountReconcileJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val likeCountReconcileTasklet: LikeCountReconcileTasklet,
) {
    companion object {
        const val JOB_NAME = "likeCountReconcileJob"
        private const val STEP_NAME = "likeCountReconcileStep"
        private const val RUN_ID = "run.id"

        /**
         * run.id 만 새로 만들고 직전 실행의 나머지 파라미터는 버린다.
         *
         * RunIdIncrementer 를 쓰지 않는 이유: Boot 러너는 직전 실행의 파라미터를 incrementer 에 넘기고, RunIdIncrementer 는
         * 그것을 복사한 채 run.id 만 올린다. 어제 dryRun=false 로 돌렸다면 오늘 dryRun 을 생략해도 false 가 이어져 덮어쓰기가 된다.
         * (2026-09-28 설계 문서 2.2 장)
         */
        private val RUN_ID_ONLY_INCREMENTER = JobParametersIncrementer { previous ->
            val lastRunId = previous?.getLong(RUN_ID) ?: 0L
            JobParametersBuilder().addLong(RUN_ID, lastRunId + 1).toJobParameters()
        }
    }

    @Bean(JOB_NAME)
    fun likeCountReconcileJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RUN_ID_ONLY_INCREMENTER)
            .start(likeCountReconcileStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun likeCountReconcileStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(likeCountReconcileTasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
}
```

- [ ] **Step 5: 통과를 확인한다**

```bash
./gradlew :apps:commerce-batch:test --tests 'com.loopers.job.likecount.LikeCountReconcileJobE2ETest'
```

기대: **6 tests / 0 failures**.

`correctsOthersAndFails_whenOneProductFails` 가 `COMPLETED` 로 끝나면 스파이 스텁이 먹지 않은 것이다.
`doThrow(...).whenever(repository)` 형태인지 확인한다 — `whenever(repository.lockLikeCount(1L))` 형태는 스텁 설정 중에 실제 메서드를 호출한다.

**`doesNotCarryOverDryRun_whenOmittedOnNextLaunch` 가 실제로 이어받기를 잡는지 한 번 확인한다 (커밋하지 않는다).**
`.incrementer(RUN_ID_ONLY_INCREMENTER)` 를 잠깐 `.incrementer(RunIdIncrementer())` 로 바꿔 이 테스트만 실행한다.
기대: **실패** — `dryRun` 이 `"false"` 로 이어지고 상품 5 가 0 으로 덮인다. 확인했으면 되돌린다.
실패하지 않으면 멈추고 보고한다 — 리뷰 초점 6 의 전제(2026-09-28 설계 문서 2.2 장)가 이 Spring Batch 버전에서 성립하지 않는다는 뜻이므로
설계 문서를 먼저 고쳐야 한다.

- [ ] **Step 6: 전체 테스트와 린트**

```bash
./gradlew :apps:commerce-batch:test :apps:commerce-batch:ktlintCheck
```

기대: **16 tests / 0 failures** (10 + 6), ktlint 통과.
Tasklet 의 `log.warn("[dryRun] ...")` 줄이 130 자를 넘으면 메시지 문자열을 두 줄로 나눠 `+` 로 잇는다.

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount \
        apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcileJobE2ETest.kt
git commit -m "feat : 좋아요 수 보정 배치 잡을 추가한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 경합 테스트

**파일:**
- 생성: `apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcileConcurrencyTest.kt`

**인터페이스:**
- 사용: Task 1 의 `LikeCountReconciler.reconcile(productId)`, `LikeCountReconcileRepository.countActiveLikes(productId)`(스파이로 멈춤), `ReconcileOutcome`, `LikeCountTables`
- 제공: 없음 (마지막 코드 태스크)

**배경:** 2026-09-28 설계 문서 3.3 장 표의 세 상황을 래치로 고정한다. commerce-batch 에는 `LikeFacade` 가 없으므로
그 쓰기 순서(좋아요 행 INSERT → 상품 행 `like_count + 1`, 한 트랜잭션)를 `JdbcTemplate` 으로 재현한다.

| 케이스 | 좋아요 트랜잭션 T 의 위치 | 보는 것 |
|---|---|---|
| 1 | 좋아요 행만 넣고 멈춤 | 보정이 **기다리지 않고** 끝난다 — 세는 SELECT 가 락 없는 읽기라는 증거. 최종값 = 실제 |
| 2 | 상품 행까지 갱신하고 커밋 전 멈춤 | 보정이 상품 행 잠금에서 **기다린다**. T 커밋 뒤 최종값 = 실제 |
| 3 | 보정이 센 뒤 쓰기 전에 멈춤, 그때 T 시작 | T 가 상품 행에서 **기다린다** — 잠금·세기·쓰기가 한 트랜잭션이라는 증거. 최종값 = 실제 |

케이스 3 은 설계 문서 4.3 장의 두 케이스에 **더한 것**이다. 케이스 3 이 없으면 `JdbcTemplate` 이 `@Transactional` 트랜잭션에 묶이지 않아 문장마다 자동 커밋되는 사고를 잡지 못한다.
케이스 1·2 는 그 상태에서도 통과한다 — 덮어쓰는 값이 절댓값이라서다.

모든 대기에는 시간 제한을 둔다. 멈춘 스레드가 락을 쥔 채 남으면 `@AfterEach` 의 `TRUNCATE` 가 걸려 테스트 전체가 멈추므로,
게이트는 `finally` 에서 반드시 연다.

- [ ] **Step 1: 테스트를 쓴다**

`apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcileConcurrencyTest.kt`

```kotlin
package com.loopers.job.likecount

import com.loopers.batch.job.likecount.LikeCountReconcileRepository
import com.loopers.batch.job.likecount.LikeCountReconciler
import com.loopers.batch.job.likecount.ReconcileOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 보정 트랜잭션과 진행 중인 좋아요 트랜잭션의 경합. (2026-09-28 설계 문서 3.3 장, 4.3 장)
 *
 * @Transactional 을 붙이지 않는다. 붙이면 스레드가 각자의 트랜잭션을 갖지 못해 경합이 일어나지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
@Sql(scripts = ["/sql/like-count-reconcile-schema.sql"])
class LikeCountReconcileConcurrencyTest @Autowired constructor(
    private val reconciler: LikeCountReconciler,
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) {
    @MockitoSpyBean
    private lateinit var repository: LikeCountReconcileRepository

    private val tables = LikeCountTables(jdbcTemplate)
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val executor = Executors.newFixedThreadPool(2)

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
        executor.awaitTermination(15, TimeUnit.SECONDS)
        tables.truncate()
    }

    /** 카운트 5, 활성 좋아요 2 (회원 1·2). 어긋난 상태에서 시작해야 보정이 실제로 쓴다. */
    private fun arrangeMismatchedProduct() {
        tables.insertProduct(id = PRODUCT_ID, likeCount = 5L)
        tables.insertLike(productId = PRODUCT_ID, userId = 1L)
        tables.insertLike(productId = PRODUCT_ID, userId = 2L)
    }

    /**
     * LikeFacade.doLike 의 쓰기 순서를 재현한다 — 좋아요 행 먼저, 상품 행 UPDATE 나중, 한 트랜잭션.
     * 두 SQL 은 commerce-api 의 LikeService.like(신규 행 저장)와 ProductJpaRepository.increaseLikeCount 와 같은 모양이다.
     */
    private fun startLike(userId: Long, afterLikeRow: Gate, afterProductRow: Gate): Future<*> =
        // Runnable 을 명시한다. 람다만 넘기면 submit(Runnable) / submit(Callable) 오버로드가 모호해진다.
        executor.submit(
            Runnable {
                transactionTemplate.executeWithoutResult {
                    jdbcTemplate.update(
                        "INSERT INTO product_likes (user_id, product_id, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
                        userId,
                        PRODUCT_ID,
                    )
                    afterLikeRow.pass()
                    jdbcTemplate.update(
                        "UPDATE products SET like_count = like_count + 1 WHERE id = ? AND deleted_at IS NULL",
                        PRODUCT_ID,
                    )
                    afterProductRow.pass()
                }
            },
        )

    private fun startReconcile(): Future<ReconcileOutcome> =
        executor.submit<ReconcileOutcome> { reconciler.reconcile(PRODUCT_ID) }

    @DisplayName("좋아요 행만 넣고 멈춘 트랜잭션이 있으면, 보정은 기다리지 않고 그 행을 빼고 쓰며, 그 트랜잭션이 커밋한 뒤 최종값이 실제와 같다.")
    @Test
    fun doesNotWait_whenLikeRowIsUncommitted() {
        // arrange
        arrangeMismatchedProduct()
        val afterLikeRow = Gate.closed()
        val like = startLike(userId = 3L, afterLikeRow = afterLikeRow, afterProductRow = Gate.opened())
        afterLikeRow.awaitArrival()

        // act
        val outcome = try {
            startReconcile().get(5, TimeUnit.SECONDS) // 세는 SELECT 가 잠금 읽기면 여기서 시간 초과다
        } finally {
            afterLikeRow.release()
        }
        like.get(5, TimeUnit.SECONDS)

        // assert
        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.Corrected(before = 5L, after = 2L)) },
            { assertThat(tables.likeCountOf(PRODUCT_ID)).isEqualTo(3L) },
            { assertThat(tables.activeLikeCountOf(PRODUCT_ID)).isEqualTo(3L) },
        )
    }

    @DisplayName("상품 행까지 갱신하고 커밋 전인 트랜잭션이 있으면, 보정은 그 커밋을 기다렸다가 실제 값을 쓴다.")
    @Test
    fun waitsForCommit_whenProductRowIsLocked() {
        // arrange
        arrangeMismatchedProduct()
        val afterProductRow = Gate.closed()
        val like = startLike(userId = 3L, afterLikeRow = Gate.opened(), afterProductRow = afterProductRow)
        afterProductRow.awaitArrival()

        // act
        val reconcile = startReconcile()
        try {
            assertThrows<TimeoutException> { reconcile.get(500, TimeUnit.MILLISECONDS) } // 상품 행 잠금에서 대기 중
        } finally {
            afterProductRow.release()
        }
        like.get(5, TimeUnit.SECONDS)
        val outcome = reconcile.get(5, TimeUnit.SECONDS)

        // assert
        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.Corrected(before = 6L, after = 3L)) },
            { assertThat(tables.likeCountOf(PRODUCT_ID)).isEqualTo(3L) },
            { assertThat(tables.activeLikeCountOf(PRODUCT_ID)).isEqualTo(3L) },
        )
    }

    @DisplayName("보정이 센 뒤 쓰기 전이면, 새 좋아요는 상품 행에서 기다리고 최종값이 실제와 같다.")
    @Test
    fun likeWaits_whileReconcileHoldsProductRow() {
        // arrange
        arrangeMismatchedProduct()
        val afterCount = Gate.closed()
        doAnswer { invocation ->
            val counted = invocation.callRealMethod()
            afterCount.pass()
            counted
        }.whenever(repository).countActiveLikes(PRODUCT_ID)
        val reconcile = startReconcile()
        afterCount.awaitArrival()

        // act
        val like = startLike(userId = 3L, afterLikeRow = Gate.opened(), afterProductRow = Gate.opened())
        try {
            // 잠금·세기·쓰기가 한 트랜잭션이 아니라 문장마다 커밋되면 여기서 좋아요가 바로 끝난다
            assertThrows<TimeoutException> { like.get(500, TimeUnit.MILLISECONDS) }
        } finally {
            afterCount.release()
        }
        val outcome = reconcile.get(5, TimeUnit.SECONDS)
        like.get(5, TimeUnit.SECONDS)

        // assert
        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.Corrected(before = 5L, after = 2L)) },
            { assertThat(tables.likeCountOf(PRODUCT_ID)).isEqualTo(3L) },
            { assertThat(tables.activeLikeCountOf(PRODUCT_ID)).isEqualTo(3L) },
        )
    }

    /** 한 스레드를 지정한 지점에 세워 두는 장치. 도착을 알리고, 열릴 때까지 기다린다. */
    private class Gate private constructor(open: Boolean) {
        private val arrived = CountDownLatch(1)
        private val released = CountDownLatch(if (open) 0 else 1)

        fun pass() {
            arrived.countDown()
            check(released.await(10, TimeUnit.SECONDS)) { "게이트가 10 초 안에 열리지 않았다." }
        }

        fun awaitArrival() {
            check(arrived.await(10, TimeUnit.SECONDS)) { "스레드가 10 초 안에 게이트에 도착하지 않았다." }
        }

        fun release() = released.countDown()

        companion object {
            fun opened() = Gate(open = true)

            fun closed() = Gate(open = false)
        }
    }

    private companion object {
        const val PRODUCT_ID = 1L
    }
}
```

- [ ] **Step 2: 통과를 확인한다**

```bash
./gradlew :apps:commerce-batch:test --tests 'com.loopers.job.likecount.LikeCountReconcileConcurrencyTest'
```

기대: **3 tests / 0 failures**.

이 태스크는 이미 있는 구현을 검증하므로 처음부터 초록이 기대값이다. 그래서 Step 3 에서 **테스트가 실제로 순서를 검증하는지** 확인한다.

- [ ] **Step 3: 테스트가 잘못된 구현을 잡는지 한 번 확인한다 (커밋하지 않는다)**

두 가지를 잠깐 바꿔 각각 실행하고, 확인한 뒤 되돌린다.

(a) `LikeCountReconcileRepository.COUNT_ACTIVE_LIKES_SQL` 끝에 ` LOCK IN SHARE MODE` 를 붙인다 — 설계 문서 3.4 장의 잠금 읽기.

기대: `doesNotWait_whenLikeRowIsUncommitted` 가 `TimeoutException` 으로 **실패**한다.
실제 락 대기나 교착 상태가 생기므로 `innodb_lock_wait_timeout`(기본 50 초)만큼 늘어질 수 있다. **느려도 정상이다** — 실패로 끝나기만 하면 된다.

(b) `LikeCountReconciler.reconcile` 의 `@Transactional(...)` 줄을 지운다 — 문장마다 자동 커밋.

기대: `likeWaits_whileReconcileHoldsProductRow` 가 **실패**한다(좋아요가 기다리지 않고 끝나 `assertThrows<TimeoutException>` 이 깨진다).

```bash
git diff --stat apps/commerce-batch/src/main   # 되돌린 뒤 비어 있어야 한다
```

둘 중 하나라도 실패하지 않으면 멈추고 보고한다 — 그 케이스는 검증하는 척만 하고 있다.

- [ ] **Step 4: 전체 테스트와 린트**

```bash
./gradlew :apps:commerce-batch:test :apps:commerce-batch:ktlintCheck
```

기대: **19 tests / 0 failures** (16 + 3), ktlint 통과.

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-batch/src/test/kotlin/com/loopers/job/likecount/LikeCountReconcileConcurrencyTest.kt
git commit -m "test : 좋아요 수 보정과 진행 중인 좋아요의 경합을 검증한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 문서 갱신

**파일:**
- 수정: `docs/superpowers/specs/2026-08-20-product-like-design.md` — 2 장 제외 표의 "좋아요 수 보정 배치" 행, 11.3 장 머리
- 수정: `docs/superpowers/specs/2026-09-28-like-count-reconcile-design.md` — 머리의 상태 줄
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt` — 클래스 KDoc
- 수정: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconciler.kt` — 클래스 KDoc 의 `REQUIRES_NEW` 단락
- 수정: `CLAUDE.md` (Gradle 루트 `loop-pack-be-l2-vol3-kotlin/CLAUDE.md`)

**인터페이스:**
- 사용: Task 1~3 의 결과 (문서가 가리키는 파일·잡 이름)
- 제공: 없음

- [ ] **Step 1: 2026-08-20 설계 문서 — 제외 표**

다음 행을

```markdown
| 좋아요 수 보정 배치 | 정합성은 같은 트랜잭션 안에서 보장한다. 사후 검증·복구 수단은 두지 않는다. 11.3 장 참고. |
```

아래로 바꾼다.

```markdown
| 좋아요 수 보정 배치 | ~~정합성은 같은 트랜잭션 안에서 보장한다. 사후 검증·복구 수단은 두지 않는다.~~ **2026-09-28 에 도입했다** — [좋아요 수 보정 배치 설계](2026-09-28-like-count-reconcile-design.md). 11.3 장 참고. |
```

- [ ] **Step 2: 2026-08-20 설계 문서 — 11.3 장 머리**

`### 11.3 정합성을 검증하거나 복구할 수단이 없다` 제목 바로 아래에 인용 블록을 넣는다. 본문은 당시 판단의 기록이므로 지우지 않는다.

```markdown
> ✅ **2026-09-28 해소.** commerce-batch 의 `likeCountReconcileJob` 이 이 공백을 메운다 —
> [좋아요 수 보정 배치 설계](2026-09-28-like-count-reconcile-design.md).
> 아래 "후속" 의 상관 서브쿼리 형태는 채택하지 않았다. 상품마다 풀스캔이 되고, 보정 UPDATE 에 넣으면 잠금 읽기가 되어
> 좋아요 등록과 교착 상태를 만든다(그 문서 3.1 · 3.4 장). 11.2 장의 시드 문제는 시드를 바꾸는 대신 `dryRun` 기본값으로 막았다(그 문서 2.2 장).
```

- [ ] **Step 3: 2026-09-28 설계 문서 — 상태 줄**

```markdown
- 상태: **설계 승인 대기**
```

를 아래로 바꾼다.

```markdown
- 상태: **구현 완료 (2026-09-28)** — 계획은 [plans/2026-09-28-like-count-reconcile.md](../plans/2026-09-28-like-count-reconcile.md)
```

- [ ] **Step 4: `LikeFacade` 클래스 KDoc**

클래스 KDoc 의 마지막 단락(`읽기 경로인 getLikedProducts 에는 ...` 로 시작하는 단락) 바로 뒤, 닫는 `*/` 앞에 한 단락을 넣는다.

```kotlin
 *
 * doLike / doUnlike 가 좋아요 행을 먼저 바꾸고 상품 행을 나중에 갱신하는 순서는 commerce-batch 의 좋아요 수 보정 배치가 전제한다.
 * 순서를 뒤집거나 카운트 갱신을 이 트랜잭션 밖(이벤트 · Redis)으로 옮기면 그 배치가 틀린 값을 쓴다. (2026-09-28 설계 문서 3.3, 6.3 장)
```

첫 줄이 130 자를 넘으면 `상품 행을 나중에 갱신하는 순서는` 뒤에서 줄을 나눈다.

- [ ] **Step 4-1: `LikeCountReconciler` 클래스 KDoc — `REQUIRES_NEW` 근거 바로잡기**

지금 스텝은 `ResourcelessTransactionManager` 로 돌아 바깥에 DB 트랜잭션이 없다. 그래서 `REQUIRED` 여도 상품마다 새 트랜잭션이 열린다.
현재 단락은 `REQUIRES_NEW` 가 **지금** 필요한 것처럼 읽히므로, 실제로 지키는 것(나중의 변경)으로 고친다. 동작은 바뀌지 않는다.

다음 단락을

```kotlin
 * REQUIRES_NEW 인 이유: 스텝은 ResourcelessTransactionManager 로 돈다. 상품마다 트랜잭션을 끊어야
 * 한 상품의 락이 다음 상품을 처리하는 동안 남지 않는다. (2026-09-28 설계 문서 3.2 장)
```

아래로 바꾼다.

```kotlin
 * 상품마다 트랜잭션을 끊어야 한 상품의 락이 다음 상품을 처리하는 동안 남지 않는다. (2026-09-28 설계 문서 3.2 장)
 * 지금은 스텝이 ResourcelessTransactionManager 로 돌아 바깥 DB 트랜잭션이 없으므로 REQUIRED 여도 같다.
 * REQUIRES_NEW 는 누군가 스텝 트랜잭션 매니저를 실제 DB 매니저로 바꿨을 때 모든 상품이 한 트랜잭션에 묶이는 것을 막는다.
```

- [ ] **Step 5: `CLAUDE.md`**

(a) 맨 위 명령 블록에 한 줄을 더한다.

```bash
./gradlew :apps:commerce-api:test          # 전체 테스트 (Docker 필요 — Testcontainers)
./gradlew :apps:commerce-api:ktlintCheck   # 스타일 검사 (커밋 전 필수)
./gradlew :apps:commerce-batch:test        # 배치 잡 테스트 (Docker 필요)
```

(b) 모듈 구조 절의 `**새 파일은 \`apps/commerce-api\` 아래에 만든다.** ...` 단락 바로 뒤에 한 단락을 넣는다.

```markdown
예외는 **배치 잡**이다. 잡은 `apps/commerce-batch` 의 `batch/job/<잡>/` 에 둔다. commerce-batch 는 commerce-api 를 의존하지 않아
도메인 클래스를 쓸 수 없으므로 `JdbcTemplate` 으로 테이블을 직접 다루고, 테스트 스키마는 `src/test/resources/sql/` 에 따로 둔다
— 엔티티와 자동으로 동기화되지 않으니 컬럼을 바꾸면 같이 고친다. (2026-09-28 좋아요 수 보정 설계 2.3 장)
```

- [ ] **Step 6: 회귀 확인**

KDoc 만 바꿨지만 commerce-api 도 컴파일·린트를 확인한다.

```bash
./gradlew :apps:commerce-api:compileKotlin :apps:commerce-api:ktlintCheck :apps:commerce-batch:test
```

기대: 성공, commerce-batch **19 tests / 0 failures**.

- [ ] **Step 7: 커밋**

```bash
git add docs/superpowers/specs/2026-08-20-product-like-design.md \
        docs/superpowers/specs/2026-09-28-like-count-reconcile-design.md \
        apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt \
        apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/likecount/LikeCountReconciler.kt \
        CLAUDE.md
git commit -m "docs : 좋아요 수 보정 배치 도입을 설계 문서와 규약에 반영한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 계획 밖

- **주기 실행(cron)·운영 배포 설정** — 이 레포 밖이다. (2026-09-28 설계 문서 1 장)
- **`product_likes.product_id` 인덱스** — 후보가 많아 보정이 느린 일이 실제로 생기면 2026-08-20 설계 문서 11.7 장의 거래와 함께 다시 본다.
- **commerce-api 스키마를 테스트 DDL 로 가져오는 자동화** — 마이그레이션 도구가 생기면 한다. (2026-09-28 설계 문서 6.1 장)
