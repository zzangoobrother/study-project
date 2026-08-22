package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductModel?

    fun findAllByBrandIdAndDeletedAtIsNull(brandId: Long): List<ProductModel>

    /**
     * products 의 updated_at 은 건드리지 않는다. (설계 문서 6.4 장)
     * 좋아요는 상품을 편집한 것이 아니라 비정규화된 카운터를 움직인 것이므로,
     * 여기서 타임스탬프를 밀면 어드민 목록에서 아무도 수정하지 않은 상품이 계속 "방금 수정됨" 으로 보인다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductModel p
           SET p.likeCount.value = p.likeCount.value + 1
         WHERE p.id = :productId AND p.deletedAt IS NULL
        """,
    )
    fun increaseLikeCount(@Param("productId") productId: Long): Int

    /** like_count 가 0 보다 클 때만 줄인다. 이 조건이 음수 방지의 실질적 책임자다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductModel p
           SET p.likeCount.value = p.likeCount.value - 1
         WHERE p.id = :productId AND p.deletedAt IS NULL AND p.likeCount.value > 0
        """,
    )
    fun decreaseLikeCount(@Param("productId") productId: Long): Int
}
