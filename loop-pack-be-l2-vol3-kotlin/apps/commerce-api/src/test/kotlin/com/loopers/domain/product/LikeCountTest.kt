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

class LikeCountTest {
    @DisplayName("좋아요 수를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상이면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = [0, 1, 42])
        fun createsLikeCount_whenValueIsNotNegative(value: Long) {
            // assert
            assertThat(LikeCount(value).value).isEqualTo(value)
        }

        @DisplayName("음수면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [-1, -42])
        fun throwsBadRequestException_whenValueIsNegative(value: Long) {
            // act
            val result = assertThrows<CoreException> { LikeCount(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("ZERO 상수는, ")
    @Nested
    inner class Zero {
        @DisplayName("0 을 값으로 갖는다.")
        @Test
        fun hasZeroValue() {
            // assert
            assertThat(LikeCount.ZERO.value).isZero()
        }
    }
}
