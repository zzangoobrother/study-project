package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StockTest {
    @DisplayName("재고를 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상이면 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = [0, 1, 1_000_000])
        fun createsStock_whenValueIsNotNegative(value: Long) {
            // act
            val stock = Stock(value)

            // assert
            assertThat(stock.value).isEqualTo(value)
        }

        /**
         * 0 을 막지 않는 이유는 품절이 오류가 아니라 정상 상태이기 때문이다.
         * 막아야 하는 것은 음수뿐이며, 음수는 차감 쿼리의 WHERE 절이 이미 막는다.
         * 이 검증은 그 방어선이 뚫렸을 때 조회 시점에 드러나게 하는 읽기 측 계약이다.
         */
        @DisplayName("음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenValueIsNegative() {
            // act
            val result = assertThrows<CoreException> { Stock(-1) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("ZERO 는 0 이다.")
        @Test
        fun zeroIsZero() {
            assertThat(Stock.ZERO.value).isEqualTo(0L)
        }

        @DisplayName("toString 은 값만 낸다.")
        @Test
        fun toStringReturnsValue() {
            assertThat(Stock(42).toString()).isEqualTo("42")
        }
    }
}
