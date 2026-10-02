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
     * LikeFacade.doLike 의 쓰기를 재현한다 — 한 트랜잭션 안에서 좋아요 행 먼저, 상품 행 UPDATE 나중.
     * 순서는 배치의 전제가 아니지만, "좋아요 행만 바뀐" 순간이 생기는 지금 순서가 더 어려운 쪽이라 그대로 따른다.
     * (2026-09-28 설계 문서 3.3 장)
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
