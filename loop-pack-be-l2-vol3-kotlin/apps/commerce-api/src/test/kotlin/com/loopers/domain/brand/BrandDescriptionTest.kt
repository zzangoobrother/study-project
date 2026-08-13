package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class BrandDescriptionTest {
    companion object {
        @JvmStatic
        fun validDescriptions() = listOf("", "   ", "일상을 조금 낫게", "가".repeat(200))
    }

    @DisplayName("브랜드 설명을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("200자 이내면, 빈 문자열도 정상 생성된다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.brand.BrandDescriptionTest#validDescriptions")
        fun createsBrandDescription_whenValueIsValid(value: String) {
            // act
            val description = BrandDescription(value)

            // assert
            assertThat(description.value).isEqualTo(value)
        }

        @DisplayName("200자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenValueIsTooLong() {
            // act
            val result = assertThrows<CoreException> { BrandDescription("가".repeat(201)) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("EMPTY 상수는, ")
    @Nested
    inner class Empty {
        @DisplayName("빈 문자열을 값으로 갖는다.")
        @Test
        fun hasBlankValue() {
            // assert
            assertThat(BrandDescription.EMPTY.value).isEmpty()
        }
    }
}
