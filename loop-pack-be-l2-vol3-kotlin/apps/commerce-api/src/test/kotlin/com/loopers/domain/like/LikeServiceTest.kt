package com.loopers.domain.like

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * LikeService 의 순수 단위 테스트. ProductLikeRepository 를 목으로 대체해 DB 없이 분기와 협력자 호출만 본다.
 *
 * AdminAuthInterceptorTest 처럼 손으로 만든 최소 구현이 아니라 목을 쓰는 이유: 이 테스트의 핵심 단언은
 * "특정 메서드를 호출하지 않았다"(verify(never())) 는 것인데, 손수 만든 페이크로 호출 여부를 추적하려면
 * 그 자체로 플래그 같은 테스트 인프라를 새로 심어야 한다. 호출 스파이가 내장된 목이 이 목적에 더 적합하다.
 */
class LikeServiceTest {
    companion object {
        private const val USER_ID = 1L
        private const val PRODUCT_ID = 2L
    }

    private val productLikeRepository = mock<ProductLikeRepository>()
    private val likeService = LikeService(productLikeRepository)

    private fun softDeletedLike(): ProductLikeModel =
        ProductLikeModel.create(userId = USER_ID, productId = PRODUCT_ID).apply { delete() }

    @DisplayName("좋아요를 걸 때, ")
    @Nested
    inner class Like {
        @DisplayName("행이 없으면, save 를 호출하고 true 를 반환한다.")
        @Test
        fun savesAndReturnsTrue_whenRowDoesNotExist() {
            // arrange
            whenever(productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID))
                .thenReturn(null)
            val captor = argumentCaptor<ProductLikeModel>()

            // act
            val result = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            verify(productLikeRepository).save(captor.capture())
            assertAll(
                { assertThat(result).isTrue() },
                { assertThat(captor.firstValue.userId).isEqualTo(USER_ID) },
                { assertThat(captor.firstValue.productId).isEqualTo(PRODUCT_ID) },
                { verify(productLikeRepository, never()).restore(any(), any(), any()) },
            )
        }

        @DisplayName("취소된 행이 있으면, save 하지 않고 restore 를 호출하며 true 를 반환한다.")
        @Test
        fun restoresAndReturnsTrue_whenRowIsSoftDeleted() {
            // arrange
            whenever(productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID))
                .thenReturn(softDeletedLike())
            whenever(productLikeRepository.restore(userId = eq(USER_ID), productId = eq(PRODUCT_ID), now = any()))
                .thenReturn(1)

            // act
            val result = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(result).isTrue() },
                { verify(productLikeRepository, never()).save(any()) },
            )
        }

        /**
         * 취소된 행을 되살리려 했지만 restore 가 0 행이면, 동시에 다른 요청이 먼저 되살린 경우다.
         * 이런 경합은 통합 테스트로 재현하기 까다롭지만, 목에서는 restore 의 반환값만 조작하면 그대로 고정된다.
         * 이 케이스가 이 단위 테스트가 통합 테스트 대비 갖는 가장 큰 값이다.
         */
        @DisplayName("취소된 행이 있지만 경합으로 이미 되살아났다면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenRestoreAffectsNoRows() {
            // arrange
            whenever(productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID))
                .thenReturn(softDeletedLike())
            whenever(productLikeRepository.restore(userId = eq(USER_ID), productId = eq(PRODUCT_ID), now = any()))
                .thenReturn(0)

            // act
            val result = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("이미 좋아요 상태면, save 도 restore 도 호출하지 않고 false 를 반환한다.")
        @Test
        fun returnsFalse_whenAlreadyLiked() {
            // arrange
            whenever(productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID))
                .thenReturn(ProductLikeModel.create(userId = USER_ID, productId = PRODUCT_ID))

            // act
            val result = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(result).isFalse() },
                { verify(productLikeRepository, never()).save(any()) },
                { verify(productLikeRepository, never()).restore(any(), any(), any()) },
            )
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    inner class Unlike {
        @DisplayName("softDelete 가 1 행이면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenSoftDeleteAffectsOneRow() {
            // arrange
            whenever(productLikeRepository.softDelete(userId = eq(USER_ID), productId = eq(PRODUCT_ID), now = any()))
                .thenReturn(1)

            // act
            val result = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("softDelete 가 0 행이면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenSoftDeleteAffectsNoRows() {
            // arrange
            whenever(productLikeRepository.softDelete(userId = eq(USER_ID), productId = eq(PRODUCT_ID), now = any()))
                .thenReturn(0)

            // act
            val result = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertThat(result).isFalse()
        }

        /** LikeService.kt 의 unlike 설계 판단 - 등록과 달리 선조회가 없다 - 을 테스트로 고정한다. */
        @DisplayName("선조회 없이 곧바로 softDelete 를 호출한다.")
        @Test
        fun doesNotLookUpBeforeSoftDelete() {
            // arrange
            whenever(productLikeRepository.softDelete(userId = eq(USER_ID), productId = eq(PRODUCT_ID), now = any()))
                .thenReturn(1)

            // act
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            verify(productLikeRepository, never()).findIncludingDeleted(any(), any())
        }
    }

    @DisplayName("좋아요한 상품 ID 를 조회할 때, ")
    @Nested
    inner class GetLikedProductIds {
        @DisplayName("저장소에 그대로 위임하고 결과를 반환한다.")
        @Test
        fun delegatesToRepository() {
            // arrange
            val pageQuery = PageQuery(page = 0, size = 20)
            val expected = PageResult(content = listOf(PRODUCT_ID), page = 0, size = 20, totalElements = 1L)
            whenever(productLikeRepository.findLikedProductIds(userId = USER_ID, pageQuery = pageQuery))
                .thenReturn(expected)

            // act
            val result = likeService.getLikedProductIds(userId = USER_ID, pageQuery = pageQuery)

            // assert
            assertThat(result).isEqualTo(expected)
        }
    }

    @DisplayName("상품 삭제에 딸린 좋아요를 정리할 때, ")
    @Nested
    inner class DeleteAllByProductIds {
        @DisplayName("저장소에 상품 ID 목록을 그대로 넘긴다.")
        @Test
        fun passesProductIdsToRepository() {
            // arrange
            val productIds = listOf(1L, 2L, 3L)

            // act
            likeService.deleteAllByProductIds(productIds)

            // assert
            verify(productLikeRepository).deleteAllByProductIds(productIds = eq(productIds), now = any())
        }
    }
}
