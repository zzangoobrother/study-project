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
