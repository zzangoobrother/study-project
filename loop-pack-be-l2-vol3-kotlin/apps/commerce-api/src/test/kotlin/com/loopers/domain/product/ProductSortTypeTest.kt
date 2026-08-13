package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ProductSortTypeTest {
    @DisplayName("정렬 기준을 파라미터에서 만들 때, ")
    @Nested
    inner class From {
        @DisplayName("지원하는 값이면, 해당 정렬 기준이 반환된다.")
        @ParameterizedTest
        @CsvSource("latest, LATEST", "price_asc, PRICE_ASC", "likes_desc, LIKES_DESC")
        fun returnsSortType_whenParameterIsSupported(parameter: String, expected: ProductSortType) {
            // assert
            assertThat(ProductSortType.from(parameter)).isEqualTo(expected)
        }

        @DisplayName("파라미터가 생략되면, 기본값 latest 가 반환된다.")
        @Test
        fun returnsDefault_whenParameterIsNull() {
            // assert
            assertThat(ProductSortType.from(null)).isEqualTo(ProductSortType.LATEST)
        }

        @DisplayName("지원하지 않는 값이면, BAD_REQUEST 예외가 발생한다. 대소문자도 정확히 일치해야 한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "LATEST", "Latest", "priceAsc", "price_desc", "likes_asc", "weird"])
        fun throwsBadRequestException_whenParameterIsNotSupported(parameter: String) {
            // act
            val result = assertThrows<CoreException> { ProductSortType.from(parameter) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("예외 메시지에 사용 가능한 값이 모두 안내된다.")
        @Test
        fun listsSupportedParametersInErrorMessage() {
            // act
            val result = assertThrows<CoreException> { ProductSortType.from("weird") }

            // assert
            assertThat(result.customMessage).contains("latest", "price_asc", "likes_desc")
        }
    }

    @DisplayName("기본 정렬 기준은, ")
    @Nested
    inner class Default {
        @DisplayName("latest 다.")
        @Test
        fun isLatest() {
            // assert
            assertThat(ProductSortType.DEFAULT).isEqualTo(ProductSortType.LATEST)
        }
    }
}
