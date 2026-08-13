package com.loopers.domain.support

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PageQueryTest {
    @DisplayName("페이징 요청을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("페이지 번호가 0 이상이고 크기가 1~100 이면, 정상 생성된다.")
        @ParameterizedTest
        @CsvSource("0, 1", "0, 20", "0, 100", "7, 20", "1000, 100")
        fun createsPageQuery_whenValuesAreValid(page: Int, size: Int) {
            // act
            val pageQuery = PageQuery(page, size)

            // assert
            assertAll(
                { assertThat(pageQuery.page).isEqualTo(page) },
                { assertThat(pageQuery.size).isEqualTo(size) },
            )
        }

        @DisplayName("페이지 번호가 음수면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [-1, -100])
        fun throwsBadRequestException_whenPageIsNegative(page: Int) {
            // act
            val result = assertThrows<CoreException> { PageQuery(page, 20) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("페이지 크기가 1~100 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, 101, 1000000])
        fun throwsBadRequestException_whenSizeIsOutOfRange(size: Int) {
            // act
            val result = assertThrows<CoreException> { PageQuery(0, size) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("offset 은, ")
    @Nested
    inner class Offset {
        @DisplayName("page 와 size 의 곱이며, Int 범위를 넘어도 정확하다.")
        @ParameterizedTest
        @CsvSource("0, 20, 0", "1, 20, 20", "7, 20, 140", "50000000, 100, 5000000000")
        fun returnsPageTimesSize(page: Int, size: Int, expected: Long) {
            // assert
            assertThat(PageQuery(page, size).offset).isEqualTo(expected)
        }
    }

    @DisplayName("of 로 생성할 때, ")
    @Nested
    inner class Of {
        @DisplayName("인자가 null 이면, 기본값 page=0 size=20 이 적용된다.")
        @Test
        fun appliesDefaults_whenArgumentsAreNull() {
            // act
            val pageQuery = PageQuery.of(null, null)

            // assert
            assertAll(
                { assertThat(pageQuery.page).isEqualTo(0) },
                { assertThat(pageQuery.size).isEqualTo(20) },
            )
        }

        @DisplayName("인자가 있으면, 그 값이 그대로 쓰인다.")
        @Test
        fun usesGivenValues_whenArgumentsArePresent() {
            // act
            val pageQuery = PageQuery.of(3, 50)

            // assert
            assertAll(
                { assertThat(pageQuery.page).isEqualTo(3) },
                { assertThat(pageQuery.size).isEqualTo(50) },
            )
        }
    }
}
