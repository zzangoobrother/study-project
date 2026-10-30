package com.loopers.infrastructure.payment

import com.loopers.application.payment.ManualReviewReason
import com.loopers.application.payment.ManualReviewRecorder
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 수동 확인 결제를 커밋된 뒤에만 센다. (2026-10-30 결제 경보 설계 3 장)
 * 컨텍스트가 공유되므로 값이 아니라 증가분으로 단언한다.
 */
@SpringBootTest
@DisplayName("수동 확인 기록기는, ")
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

    @Nested
    @DisplayName("기동했을 때, ")
    inner class OnStartup {
        @Test
        @DisplayName("다섯 이유의 카운터가 모두 등록돼 있다.")
        fun registersAllReasonsOnStartup() {
            // arrange
            val reasons = ManualReviewReason.entries

            // act
            val counters = reasons.associateWith { meterRegistry.find("payment.manual.review").tag("reason", it.tag).counter() }

            // assert
            assertAll(
                *reasons.map<ManualReviewReason, () -> Unit> { reason ->
                    { assertThat(counters[reason]).describedAs(reason.tag).isNotNull }
                }.toTypedArray(),
            )
        }

        @Test
        @DisplayName("이유는 다섯 가지뿐이다.")
        fun hasOnlyFiveReasons() {
            // arrange
            val reasons = ManualReviewReason.entries

            // act
            val size = reasons.size

            // assert
            assertThat(size).isEqualTo(5)
        }
    }

    @Nested
    @DisplayName("트랜잭션 밖에서 기록하면, ")
    inner class OutsideTransaction {
        @Test
        @DisplayName("즉시 센다.")
        fun countsImmediately_outsideTransaction() {
            // arrange
            val before = snapshot()

            // act
            manualReviewRecorder.record(ManualReviewReason.AMOUNT_MISMATCH)

            // assert
            assertAll(
                *ManualReviewReason.entries.map<ManualReviewReason, () -> Unit> { reason ->
                    {
                        val expected = if (reason == ManualReviewReason.AMOUNT_MISMATCH) 1.0 else 0.0
                        assertThat(count(reason) - before.getValue(reason)).describedAs(reason.tag).isEqualTo(expected)
                    }
                }.toTypedArray(),
            )
        }
    }

    @Nested
    @DisplayName("트랜잭션 안에서 기록하면, ")
    inner class InsideTransaction {
        @Test
        @DisplayName("커밋된 뒤에야 센다.")
        fun countsOnlyAfterCommit() {
            // arrange
            val before = count(ManualReviewReason.ORDER_MISMATCH)
            var insideBlock = Double.NaN

            // act
            transactionTemplate.executeWithoutResult {
                manualReviewRecorder.record(ManualReviewReason.ORDER_MISMATCH)
                insideBlock = count(ManualReviewReason.ORDER_MISMATCH) - before
            }

            // assert
            assertAll(
                { assertThat(insideBlock).isEqualTo(0.0) },
                { assertThat(count(ManualReviewReason.ORDER_MISMATCH) - before).isEqualTo(1.0) },
            )
        }

        @Test
        @DisplayName("롤백되면 세지 않는다.")
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
}
