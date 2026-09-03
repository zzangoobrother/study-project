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

    @DisplayName("최소 주문 금액을 지정할 때, ")
    @Nested
    inner class MinOrderAmount {
        @DisplayName("생략하면 0 이 된다.")
        @Test
        fun defaultsToZero_whenOmitted() {
            // act
            val coupon = CouponModel.create(
                name = CouponName("신규가입"),
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                expiresAt = ZonedDateTime.now().plusDays(30),
            )

            // assert
            assertThat(coupon.minOrderAmount).isEqualTo(0L)
        }

        @DisplayName("0 이상이면 그 값이 그대로 저장된다.")
        @Test
        fun keepsValue_whenNotNegative() {
            // act
            val coupon = CouponModel.create(
                name = CouponName("신규가입"),
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                minOrderAmount = 10_000,
                expiresAt = ZonedDateTime.now().plusDays(30),
            )

            // assert
            assertThat(coupon.minOrderAmount).isEqualTo(10_000L)
        }

        @DisplayName("음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNegative() {
            // act
            val result = assertThrows<CoreException> {
                CouponModel.create(
                    name = CouponName("신규가입"),
                    discountType = DiscountType.FIXED,
                    discountValue = 5_000,
                    minOrderAmount = -1,
                    expiresAt = ZonedDateTime.now().plusDays(30),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("정책을 수정할 때, ")
    @Nested
    inner class Change {
        @DisplayName("전 필드가 새 값으로 교체된다.")
        @Test
        fun replacesAllFields() {
            // arrange
            val coupon = CouponModel.create(
                name = CouponName("이전 이름"),
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                minOrderAmount = 0,
                expiresAt = ZonedDateTime.now().plusDays(10),
            )
            val newExpiresAt = ZonedDateTime.now().plusDays(60)

            // act
            coupon.change(
                name = CouponName("새 이름"),
                discountType = DiscountType.RATE,
                discountValue = 20,
                minOrderAmount = 30_000,
                expiresAt = newExpiresAt,
            )

            // assert
            assertAll(
                { assertThat(coupon.name).isEqualTo(CouponName("새 이름")) },
                { assertThat(coupon.discountType).isEqualTo(DiscountType.RATE) },
                { assertThat(coupon.discountValue).isEqualTo(20L) },
                { assertThat(coupon.minOrderAmount).isEqualTo(30_000L) },
                { assertThat(coupon.expiresAt).isEqualTo(newExpiresAt) },
            )
        }

        @DisplayName("등록과 같은 규칙으로 검증한다. 정률 101 은 거부된다.")
        @Test
        fun throwsBadRequest_whenRateExceedsHundred() {
            // arrange
            val coupon = CouponModel.create(
                name = CouponName("이전 이름"),
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                expiresAt = ZonedDateTime.now().plusDays(10),
            )

            // act
            val result = assertThrows<CoreException> {
                coupon.change(
                    name = CouponName("새 이름"),
                    discountType = DiscountType.RATE,
                    discountValue = 101,
                    minOrderAmount = 0,
                    expiresAt = ZonedDateTime.now().plusDays(60),
                )
            }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                // 검증이 대입보다 앞에 있어야 한다. 실패한 수정이 절반만 반영되면 안 된다.
                { assertThat(coupon.name).isEqualTo(CouponName("이전 이름")) },
            )
        }
    }
}
