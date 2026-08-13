package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class BrandNameTest {
    companion object {
        @JvmStatic
        fun validNames() = listOf("루", "루퍼스", "a", "A".repeat(50))

        @JvmStatic
        fun invalidNames() = listOf("", " ", "   ", "\t", "A".repeat(51))
    }

    @DisplayName("브랜드명을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("1~50자면, 정상 생성된다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.brand.BrandNameTest#validNames")
        fun createsBrandName_whenValueIsValid(value: String) {
            // act
            val name = BrandName(value)

            // assert
            assertThat(name.value).isEqualTo(value)
        }

        @DisplayName("비어 있거나 공백뿐이거나 50자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.brand.BrandNameTest#invalidNames")
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { BrandName(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("브랜드명은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = BrandName("루퍼스")
            val second = BrandName("루퍼스")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("루퍼스") },
            )
        }
    }
}
