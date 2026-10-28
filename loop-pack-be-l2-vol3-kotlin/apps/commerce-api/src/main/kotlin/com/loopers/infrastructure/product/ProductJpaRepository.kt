package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductModel?

    fun findAllByBrandIdAndDeletedAtIsNull(brandId: Long): List<ProductModel>

    fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<ProductModel>

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

    /**
     * 초과 판매를 막는 것은 stock >= :quantity 조건이다.
     * 이 조건이 없으면 재고가 음수가 되고, 영향 행 수가 항상 1 이라 호출자가 실패를 알 수 없다.
     *
     * like_count 증감과 마찬가지로 updated_at 을 건드리지 않는다. (설계 문서 6.3 장)
     * 재고가 빠진 것은 상품을 편집한 것이 아니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductModel p
           SET p.stock.value = p.stock.value - :quantity
         WHERE p.id = :productId AND p.deletedAt IS NULL AND p.stock.value >= :quantity
        """,
    )
    fun decreaseStock(@Param("productId") productId: Long, @Param("quantity") quantity: Int): Int

    /**
     * 취소된 주문의 재고를 되돌린다. 삭제된 상품도 대상이다.
     *
     * deleted_at 조건이 없는 것은 의도적이다. 주문이 살아 있는 동안 상품이 삭제될 수 있는데,
     * 그 상품을 건너뛰면 차감해 간 재고가 영영 돌아오지 않는다. (설계 문서 4.3 장)
     * 상한 조건도 두지 않는다. 되돌리는 수량은 차감했던 수량이므로 초과할 수 없다.
     *
     * decreaseStock 과 마찬가지로 updated_at 을 건드리지 않는다. 재고가 돌아온 것은 상품을 편집한 것이 아니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductModel p
           SET p.stock.value = p.stock.value + :quantity
         WHERE p.id = :productId
        """,
    )
    fun increaseStock(@Param("productId") productId: Long, @Param("quantity") quantity: Int): Int
}
