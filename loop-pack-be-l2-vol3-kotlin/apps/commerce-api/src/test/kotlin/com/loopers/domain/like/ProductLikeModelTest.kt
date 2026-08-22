package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductLikeModelTest {
    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("회원 ID 와 상품 ID 가 모두 양수면, 생성된다.")
        @Test
        fun creates_whenBothIdsArePositive() {
            // act
            val like = ProductLikeModel.create(userId = 1L, productId = 2L)

            // assert
            assertAll(
                { assertThat(like.userId).isEqualTo(1L) },
                { assertThat(like.productId).isEqualTo(2L) },
                { assertThat(like.deletedAt).isNull() },
            )
        }

        @DisplayName("회원 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenUserIdIsNotPositive(userId: Long) {
            // act
            val result = assertThrows<CoreException> { ProductLikeModel.create(userId = userId, productId = 1L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenProductIdIsNotPositive(productId: Long) {
            // act
            val result = assertThrows<CoreException> { ProductLikeModel.create(userId = 1L, productId = productId) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
