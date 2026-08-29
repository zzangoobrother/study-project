package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CouponNameTest {
    @DisplayName("쿠폰 이름을 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("공백이 아니고 길이 상한 이내면, 생성된다.")
        @Test
        fun creates_whenValueIsValid() {
            // act
            val name = CouponName("신규가입 5천원")

            // assert
            assertThat(name.value).isEqualTo("신규가입 5천원")
        }

        @DisplayName("공백만으로 이루어지면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsBadRequest_whenValueIsBlank(value: String) {
            // act
            val result = assertThrows<CoreException> { CouponName(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("길이 상한을 넘으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenValueIsTooLong() {
            // act
            val result = assertThrows<CoreException> { CouponName("가".repeat(CouponName.MAX_LENGTH + 1)) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("길이 상한과 정확히 같으면, 생성된다.")
        @Test
        fun creates_whenValueLengthIsExactlyMax() {
            // act
            val name = CouponName("가".repeat(CouponName.MAX_LENGTH))

            // assert
            assertThat(name.value).hasSize(CouponName.MAX_LENGTH)
        }
    }
}
