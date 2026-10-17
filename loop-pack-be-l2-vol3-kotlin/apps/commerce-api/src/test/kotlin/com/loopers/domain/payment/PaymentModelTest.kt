package com.loopers.domain.payment

import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentModelTest {
    private fun payment(orderId: Long = 1L, userId: Long = 1L, amount: Long = 5_000) =
        PaymentModel.create(
            orderId = orderId,
            userId = userId,
            amount = Price(amount),
            cardType = CardType.SAMSUNG,
            cardNo = CardNo("1234-5678-9814-1451"),
        )

    @DisplayName("결제를 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("요청됨 상태이고 거래 키가 없다.")
        @Test
        fun startsAsRequested() {
            // act
            val payment = payment()

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(payment.transactionKey).isNull() },
                { assertThat(payment.failureReason).isNull() },
            )
        }

        @DisplayName("주문 ID 가 양수가 아니면 400 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenOrderIdIsNotPositive() {
            // act
            val result = assertThrows<CoreException> { payment(orderId = 0L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("회원 ID 가 양수가 아니면 400 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUserIdIsNotPositive() {
            // act
            val result = assertThrows<CoreException> { payment(userId = 0L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상태를 바꿀 때, ")
    @Nested
    inner class Transition {
        @DisplayName("요청됨에서 PG 접수를 받으면 거래 키와 함께 대기가 된다.")
        @Test
        fun becomesPending_whenAcceptedFromRequested() {
            // arrange
            val payment = payment()

            // act
            payment.markPending("20251013:TR:abc123")

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.transactionKey).isEqualTo("20251013:TR:abc123") },
            )
        }

        @DisplayName("대기에서 다시 접수를 받으면 무시한다.")
        @Test
        fun ignoresMarkPending_whenAlreadyPending() {
            // arrange
            val payment = payment()
            payment.markPending("20251013:TR:abc123")

            // act
            payment.markPending("20251013:TR:zzz999")

            // assert
            assertThat(payment.transactionKey).isEqualTo("20251013:TR:abc123")
        }

        @DisplayName("성공 · 실패로 확정된다.")
        @Test
        fun becomesFinal() {
            // arrange
            val succeeded = payment()
            val failed = payment()

            // act
            succeeded.succeed()
            failed.fail("한도초과입니다.")

            // assert
            assertAll(
                { assertThat(succeeded.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(failed.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(failed.failureReason).isEqualTo("한도초과입니다.") },
            )
        }

        /**
         * 콜백과 스케줄러가 같은 결과를 동시에 들고 오는 것이 정상 경로라 예외를 던지면 안 된다. (2026-10-13 결제 설계 3.1 장)
         */
        @DisplayName("확정된 뒤의 전이 요청은 예외 없이 무시한다.")
        @Test
        fun ignoresTransition_whenAlreadyFinal() {
            // arrange
            val payment = payment()
            payment.succeed()

            // act
            payment.fail("늦게 온 실패")
            payment.markPending("20251013:TR:abc123")

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(payment.failureReason).isNull() },
                { assertThat(payment.transactionKey).isNull() },
            )
        }
    }
}
