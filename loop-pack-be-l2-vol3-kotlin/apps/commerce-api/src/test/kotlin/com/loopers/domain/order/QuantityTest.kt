package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class QuantityTest {
    @DisplayName("1 이상이면 생성된다.")
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 1000])
    fun createsQuantity_whenValueIsPositive(value: Int) {
        assertThat(Quantity(value).value).isEqualTo(value)
    }

    /**
     * Price 는 0 을 허용하지만 Quantity 는 막는다.
     * 0 원 상품(사은품)은 실재하지만, 0 개를 사는 주문은 항목이 있으면서 아무것도 사지 않는 상태다.
     */
    @DisplayName("0 이하면, BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun throwsBadRequest_whenValueIsNotPositive(value: Int) {
        val result = assertThrows<CoreException> { Quantity(value) }

        assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
