package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DiscountTypeTest {
    @DisplayName("정액 쿠폰의 할인을 계산할 때, ")
    @Nested
    inner class FixedAmount {
        @DisplayName("총액보다 작으면, 그 금액이 그대로 할인된다.")
        @Test
        fun returnsDiscountValue_whenLessThanTotal() {
            // act
            val result = DiscountType.FIXED_AMOUNT.calculate(discountValue = 5_000, totalPrice = 30_000)

            // assert
            assertThat(result).isEqualTo(5_000)
        }

        @DisplayName("총액보다 크면, 총액까지만 할인된다.")
        @Test
        fun returnsTotalPrice_whenGreaterThanTotal() {
            // act
            val result = DiscountType.FIXED_AMOUNT.calculate(discountValue = 10_000, totalPrice = 5_000)

            // assert
            assertThat(result).isEqualTo(5_000)
        }

        @DisplayName("총액과 같으면, 총액 전부가 할인된다.")
        @Test
        fun returnsTotalPrice_whenEqualToTotal() {
            // act
            val result = DiscountType.FIXED_AMOUNT.calculate(discountValue = 5_000, totalPrice = 5_000)

            // assert
            assertThat(result).isEqualTo(5_000)
        }
    }

    @DisplayName("정률 쿠폰의 할인을 계산할 때, ")
    @Nested
    inner class Percentage {
        @DisplayName("총액에 비율을 곱한 값이 할인된다.")
        @Test
        fun returnsProportionalDiscount() {
            // act
            val result = DiscountType.PERCENTAGE.calculate(discountValue = 10, totalPrice = 30_000)

            // assert
            assertThat(result).isEqualTo(3_000)
        }

        @DisplayName("원 단위 미만은 버린다.")
        @Test
        fun truncatesBelowWon() {
            // act
            // 33,333 의 20% 는 6,666.6 이다. 내림해서 6,666 이어야 한다.
            val result = DiscountType.PERCENTAGE.calculate(discountValue = 20, totalPrice = 33_333)

            // assert
            assertThat(result).isEqualTo(6_666)
        }

        @DisplayName("100 퍼센트면, 총액 전부가 할인된다.")
        @Test
        fun returnsTotalPrice_whenHundredPercent() {
            // act
            val result = DiscountType.PERCENTAGE.calculate(discountValue = 100, totalPrice = 30_000)

            // assert
            assertThat(result).isEqualTo(30_000)
        }

        @DisplayName("작은 총액에 작은 비율이면, 0 원이 된다.")
        @Test
        fun returnsZero_whenResultIsBelowOneWon() {
            // act
            // 9 원의 10% 는 0.9 원이라 내림하면 0 이다.
            val result = DiscountType.PERCENTAGE.calculate(discountValue = 10, totalPrice = 9)

            // assert
            assertThat(result).isEqualTo(0)
        }
    }

    @DisplayName("총액이 0 원이면, ")
    @Nested
    inner class ZeroTotal {
        @DisplayName("어떤 쿠폰이든 할인이 0 원이다.")
        @Test
        fun returnsZero_forEveryType() {
            // act & assert
            assertThat(DiscountType.FIXED_AMOUNT.calculate(discountValue = 5_000, totalPrice = 0)).isEqualTo(0)
            assertThat(DiscountType.PERCENTAGE.calculate(discountValue = 50, totalPrice = 0)).isEqualTo(0)
        }
    }
}
