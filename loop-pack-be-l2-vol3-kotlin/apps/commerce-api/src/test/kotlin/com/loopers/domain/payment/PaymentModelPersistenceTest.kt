package com.loopers.domain.payment

import com.loopers.domain.product.Price
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
import org.springframework.dao.OptimisticLockingFailureException
import java.time.ZonedDateTime

@SpringBootTest
class PaymentModelPersistenceTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun save(orderId: Long = 1L, configure: (PaymentModel) -> Unit = {}): PaymentModel {
        val payment = PaymentModel.create(
            orderId = orderId,
            userId = 1L,
            amount = Price(5_000),
            cardType = CardType.KB,
            cardNo = CardNo("1234-5678-9814-1451"),
        )
        configure(payment)
        return paymentRepository.save(payment)
    }

    @DisplayName("결제를 저장하면, 값 객체와 상태가 그대로 다시 읽힌다.")
    @Test
    fun savesAndLoads() {
        // arrange
        val saved = save { it.markPending("20251013:TR:abc123") }

        // act
        val found = paymentRepository.findById(saved.id)!!

        // assert
        assertAll(
            { assertThat(found.amount).isEqualTo(Price(5_000)) },
            { assertThat(found.cardNo).isEqualTo(CardNo("1234-5678-9814-1451")) },
            { assertThat(found.cardType).isEqualTo(CardType.KB) },
            { assertThat(found.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(found.transactionKey).isEqualTo("20251013:TR:abc123") },
        )
    }

    @DisplayName("거래 키로 찾을 수 있다.")
    @Test
    fun findsByTransactionKey() {
        // arrange
        val saved = save { it.markPending("20251013:TR:abc123") }
        save()

        // act
        val found = paymentRepository.findByTransactionKey("20251013:TR:abc123")

        // assert
        assertThat(found?.id).isEqualTo(saved.id)
    }

    /**
     * 검토 초점 6. 두 확정 경로가 같은 버전을 읽고 서로 다른 결과로 바꾸는 상황을 트랜잭션 둘로 재현한다.
     * findById 는 저장소 밖에서 부르면 각자 끝난 트랜잭션의 분리된 엔티티를 돌려주므로 "동시에 읽은 두 트랜잭션" 과 같다.
     * (2026-10-13 결제 설계 3.1 장)
     */
    @DisplayName("같은 결제를 두 곳에서 읽고 각자 바꾸면, 늦게 저장한 쪽이 낙관적 락 예외로 실패하고 먼저 저장한 값이 남는다.")
    @Test
    fun rejectsStaleUpdate() {
        // arrange
        val saved = save()
        val givingUp = paymentRepository.findById(saved.id)!!
        val confirming = paymentRepository.findById(saved.id)!!
        givingUp.fail("결제 요청이 PG 에 도달하지 않았습니다.")
        paymentRepository.save(givingUp)
        confirming.succeed()

        // act
        assertThrows<OptimisticLockingFailureException> { paymentRepository.save(confirming) }

        // assert
        assertThat(paymentRepository.findById(saved.id)!!.status).isEqualTo(PaymentStatus.FAILED)
    }

    @DisplayName("주문의 진행 중 결제를 찾을 때, ")
    @Nested
    inner class FindInProgress {
        @DisplayName("요청됨 · 대기 건만 찾고 확정 건은 건너뛴다.")
        @Test
        fun findsOnlyInProgress() {
            // arrange
            save(orderId = 7L) { it.fail("한도초과입니다.") }
            val inProgress = save(orderId = 7L)

            // act
            val found = paymentRepository.findInProgressByOrderId(7L)

            // assert
            assertThat(found?.id).isEqualTo(inProgress.id)
        }

        @DisplayName("확정 건만 있으면 null 이다.")
        @Test
        fun returnsNull_whenOnlyFinal() {
            // arrange
            save(orderId = 7L) { it.succeed() }

            // act
            val found = paymentRepository.findInProgressByOrderId(7L)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("동기화 대상을 찾을 때, ")
    @Nested
    inner class FindSyncTargets {
        @DisplayName("기준 시각 전에 마지막으로 바뀐 진행 중 결제만 id 순으로 limit 만큼 찾는다.")
        @Test
        fun findsInProgressBeforeCutoff() {
            // arrange
            val first = save()
            val second = save { it.markPending("20251013:TR:abc123") }
            save()
            save { it.succeed() }

            // act
            val found = paymentRepository.findSyncTargets(updatedBefore = ZonedDateTime.now().plusSeconds(1), limit = 2)

            // assert
            assertThat(found.map { it.id }).containsExactly(first.id, second.id)
        }

        @DisplayName("기준 시각 뒤에 바뀐 결제는 찾지 않는다.")
        @Test
        fun skipsRecentlyUpdated() {
            // arrange
            save()

            // act
            val found = paymentRepository.findSyncTargets(updatedBefore = ZonedDateTime.now().minusSeconds(30), limit = 100)

            // assert
            assertThat(found).isEmpty()
        }
    }
}
