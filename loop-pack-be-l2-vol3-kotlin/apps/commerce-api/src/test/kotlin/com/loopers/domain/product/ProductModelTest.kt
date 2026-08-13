package com.loopers.domain.product

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

class ProductModelTest {
    @DisplayName("상품을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값을 주면, 정상 생성된다.")
        @Test
        fun createsProduct_whenValuesAreValid() {
            // act
            val product = ProductModel.create(
                brandId = 1L,
                name = ProductName("베이직 티셔츠"),
                price = Price(29000),
                likeCount = LikeCount(42),
            )

            // assert
            assertAll(
                { assertThat(product.brandId).isEqualTo(1L) },
                { assertThat(product.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(product.price).isEqualTo(Price(29000)) },
                { assertThat(product.likeCount).isEqualTo(LikeCount(42)) },
            )
        }

        @DisplayName("좋아요 수를 생략하면, 0 으로 시작한다.")
        @Test
        fun startsWithZeroLikeCount_whenLikeCountIsOmitted() {
            // act
            val product = ProductModel.create(
                brandId = 1L,
                name = ProductName("베이직 티셔츠"),
                price = Price(29000),
            )

            // assert
            assertThat(product.likeCount).isEqualTo(LikeCount.ZERO)
        }

        @DisplayName("브랜드 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, -1])
        fun throwsBadRequestException_whenBrandIdIsNotPositive(brandId: Long) {
            // act
            val result = assertThrows<CoreException> {
                ProductModel.create(brandId = brandId, name = ProductName("베이직 티셔츠"), price = Price(29000))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
