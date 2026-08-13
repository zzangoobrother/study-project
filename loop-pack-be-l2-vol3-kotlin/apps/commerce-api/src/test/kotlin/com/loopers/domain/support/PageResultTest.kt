package com.loopers.domain.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PageResultTest {
    @DisplayName("totalPages 는, ")
    @Nested
    inner class TotalPages {
        @DisplayName("총 개수와 페이지 크기로 계산되며, 총 개수가 0 이면 0 이다.")
        @ParameterizedTest
        @CsvSource("0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "137, 20, 7", "140, 20, 7", "141, 20, 8")
        fun calculatesFromTotalElementsAndSize(totalElements: Long, size: Int, expected: Int) {
            // arrange
            val result = PageResult(content = emptyList<String>(), page = 0, size = size, totalElements = totalElements)

            // assert
            assertThat(result.totalPages).isEqualTo(expected)
        }
    }

    @DisplayName("map 은, ")
    @Nested
    inner class Map {
        @DisplayName("content 만 변환하고 페이징 메타 정보는 그대로 보존한다.")
        @Test
        fun transformsContentAndPreservesMetadata() {
            // arrange
            val result = PageResult(content = listOf(1, 2, 3), page = 2, size = 20, totalElements = 137L)

            // act
            val mapped = result.map { it * 10 }

            // assert
            assertAll(
                { assertThat(mapped.content).containsExactly(10, 20, 30) },
                { assertThat(mapped.page).isEqualTo(2) },
                { assertThat(mapped.size).isEqualTo(20) },
                { assertThat(mapped.totalElements).isEqualTo(137L) },
                { assertThat(mapped.totalPages).isEqualTo(7) },
            )
        }
    }

    @DisplayName("of 로 생성하면, ")
    @Nested
    inner class Of {
        @DisplayName("PageQuery 의 page 와 size 가 그대로 반영된다.")
        @Test
        fun copiesPageAndSizeFromPageQuery() {
            // act
            val result = PageResult.of(listOf("a"), PageQuery(3, 50), 137L)

            // assert
            assertAll(
                { assertThat(result.page).isEqualTo(3) },
                { assertThat(result.size).isEqualTo(50) },
                { assertThat(result.totalElements).isEqualTo(137L) },
            )
        }
    }
}
