package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PriceTest {
    @DisplayName("가격을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상이면, 정상 생성된다. 사은품처럼 0원인 상품이 있을 수 있어 0 을 허용한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, 1, 29000, 9_999_999_999])
        fun createsPrice_whenValueIsNotNegative(value: Long) {
            // assert
            assertThat(Price(value).value).isEqualTo(value)
        }

        @DisplayName("음수면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [-1, -29000])
        fun throwsBadRequestException_whenValueIsNegative(value: Long) {
            // act
            val result = assertThrows<CoreException> { Price(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
