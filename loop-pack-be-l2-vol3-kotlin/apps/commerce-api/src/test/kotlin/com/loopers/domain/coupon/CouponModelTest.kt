package com.loopers.domain.coupon

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
import java.time.ZonedDateTime

class CouponModelTest {
    private val future: ZonedDateTime = ZonedDateTime.now().plusDays(30)

    private fun coupon(type: DiscountType, value: Long): CouponModel =
        CouponModel.create(
            name = CouponName("테스트 쿠폰"),
            discountType = type,
            discountValue = value,
            expiresAt = future,
        )

    @DisplayName("쿠폰 정책을 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("정액 쿠폰의 할인액이 1 이상이면, 생성된다.")
        @Test
        fun creates_whenFixedAmountIsPositive() {
            // act
            val model = coupon(DiscountType.FIXED, 5_000)

            // assert
            assertAll(
                { assertThat(model.discountType).isEqualTo(DiscountType.FIXED) },
                { assertThat(model.discountValue).isEqualTo(5_000) },
                { assertThat(model.expiresAt).isEqualTo(future) },
            )
        }

        @DisplayName("정액 쿠폰의 할인액이 1 미만이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenFixedAmountIsNotPositive(value: Long) {
            // act
            val result = assertThrows<CoreException> { coupon(DiscountType.FIXED, value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정률 쿠폰의 비율이 1 이상 100 이하면, 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = [1L, 50L, 100L])
        fun creates_whenPercentageIsInRange(value: Long) {
            // act
            val model = coupon(DiscountType.RATE, value)

            // assert
            assertThat(model.discountValue).isEqualTo(value)
        }

        @DisplayName("정률 쿠폰의 비율이 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L, 101L])
        fun throwsBadRequest_whenPercentageIsOutOfRange(value: Long) {
            // act
            val result = assertThrows<CoreException> { coupon(DiscountType.RATE, value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
