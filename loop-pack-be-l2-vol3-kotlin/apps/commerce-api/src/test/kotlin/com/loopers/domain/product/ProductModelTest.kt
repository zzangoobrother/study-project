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

    @DisplayName("상품 정보를 변경할 때, ")
    @Nested
    inner class Change {
        private fun product(): ProductModel =
            ProductModel.create(brandId = 1L, name = ProductName("운동화"), price = Price(39000))

        @DisplayName("이름과 가격이 모두 교체된다.")
        @Test
        fun replacesNameAndPrice() {
            // arrange
            val sut = product()

            // act
            sut.change(ProductName("러닝화"), Price(59000), Stock.ZERO)

            // assert
            assertAll(
                { assertThat(sut.name).isEqualTo(ProductName("러닝화")) },
                { assertThat(sut.price).isEqualTo(Price(59000)) },
            )
        }

        /**
         * change 의 시그니처에 brandId 가 없다는 것 자체가 "상품의 브랜드는 수정할 수 없음" 요구사항의 이행이다.
         * 런타임 검증이 아니라 컴파일 타임 차단이며, 이 테스트는 그 성질이 유지되는지 확인한다.
         */
        @DisplayName("브랜드 ID 는 변경되지 않는다.")
        @Test
        fun keepsBrandId() {
            // arrange
            val sut = product()

            // act
            sut.change(ProductName("러닝화"), Price(59000), Stock.ZERO)

            // assert
            assertThat(sut.brandId).isEqualTo(1L)
        }

        @DisplayName("좋아요 수는 변경되지 않는다.")
        @Test
        fun keepsLikeCount() {
            // arrange
            val sut = ProductModel.create(
                brandId = 1L,
                name = ProductName("운동화"),
                price = Price(39000),
                likeCount = LikeCount(7),
            )

            // act
            sut.change(ProductName("러닝화"), Price(59000), Stock.ZERO)

            // assert
            assertThat(sut.likeCount).isEqualTo(LikeCount(7))
        }

        @DisplayName("가격을 0 으로 변경할 수 있다.")
        @Test
        fun allowsZeroPrice() {
            // arrange
            val sut = product()

            // act
            sut.change(ProductName("사은품"), Price(0), Stock.ZERO)

            // assert
            assertThat(sut.price).isEqualTo(Price(0))
        }
    }
}
