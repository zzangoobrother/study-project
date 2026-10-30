package com.loopers.infrastructure.payment

import com.loopers.application.payment.ManualReviewReason
import com.loopers.application.payment.ManualReviewRecorder
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class MicrometerManualReviewRecorderTest @Autowired constructor(
    private val meterRegistry: MeterRegistry,
    private val transactionTemplate: TransactionTemplate,
    private val manualReviewRecorder: ManualReviewRecorder,
) {
    // find 로 읽는다 — counter() 는 없으면 만들어 미등록을 가린다
    private fun count(reason: ManualReviewReason): Double =
        meterRegistry.find("payment.manual.review").tag("reason", reason.tag).counter()?.count()
            ?: error("카운터가 등록되지 않았습니다: ${reason.tag}")

    private fun snapshot(): Map<ManualReviewReason, Double> = ManualReviewReason.entries.associateWith { count(it) }

    @Test
    @DisplayName("기동 직후 다섯 이유의 카운터가 모두 등록돼 있다")
    fun registersAllReasonsOnStartup() {
        ManualReviewReason.entries.forEach { reason ->
            assertThat(meterRegistry.find("payment.manual.review").tag("reason", reason.tag).counter())
                .describedAs(reason.tag)
                .isNotNull
        }
        assertThat(ManualReviewReason.entries).hasSize(5)
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 즉시 센다")
    fun countsImmediately_outsideTransaction() {
        // arrange
        val before = snapshot()

        // act
        manualReviewRecorder.record(ManualReviewReason.AMOUNT_MISMATCH)

        // assert
        ManualReviewReason.entries.forEach { reason ->
            val expected = if (reason == ManualReviewReason.AMOUNT_MISMATCH) 1.0 else 0.0
            assertThat(count(reason) - before.getValue(reason)).describedAs(reason.tag).isEqualTo(expected)
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋된 뒤에야 센다")
    fun countsOnlyAfterCommit() {
        // arrange
        val before = count(ManualReviewReason.ORDER_MISMATCH)

        // act
        transactionTemplate.executeWithoutResult {
            manualReviewRecorder.record(ManualReviewReason.ORDER_MISMATCH)
            assertThat(count(ManualReviewReason.ORDER_MISMATCH) - before).isEqualTo(0.0)
        }

        // assert
        assertThat(count(ManualReviewReason.ORDER_MISMATCH) - before).isEqualTo(1.0)
    }

    @Test
    @DisplayName("롤백되면 세지 않는다")
    fun doesNotCount_whenRolledBack() {
        // arrange
        val before = count(ManualReviewReason.KEY_OWNER_CONFLICT)

        // act
        assertThrows<IllegalStateException> {
            transactionTemplate.executeWithoutResult {
                manualReviewRecorder.record(ManualReviewReason.KEY_OWNER_CONFLICT)
                throw IllegalStateException("롤백")
            }
        }

        // assert
        assertThat(count(ManualReviewReason.KEY_OWNER_CONFLICT) - before).isEqualTo(0.0)
    }
}
