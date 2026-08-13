package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 상품 엔티티.
 *
 * 브랜드를 객체가 아닌 brandId 로 참조한다. (설계 문서 5.3 장)
 * 애그리거트 경계를 도메인 타입으로 강제하고, 목록 조회에서 N+1 이 생길 경로를 문법적으로 차단한다.
 */
@Entity
@Table(
    name = "products",
    indexes = [Index(name = "idx_products_brand_id", columnList = "brand_id")],
)
class ProductModel private constructor(
    brandId: Long,
    name: ProductName,
    price: Price,
    likeCount: LikeCount,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = ProductName.MAX_LENGTH))
    var name: ProductName = name
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "price", nullable = false))
    var price: Price = price
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "like_count", nullable = false))
    var likeCount: LikeCount = likeCount
        protected set

    init {
        // brandId 만 값 객체가 아니라 원시 타입이므로(설계 문서 5.2 장) 이 검증만 애그리거트가 직접 한다.
        // 브랜드 ID 라는 개념은 BrandModel 쪽에 속하며, 상품이 그것을 감싸는 타입을 따로 정의하면
        // 같은 식별자에 두 개의 타입이 생긴다.
        if (brandId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 양수여야 합니다.")
        }
    }

    companion object {
        /**
         * likeCount 는 기본값 0 이며, 인자로 받는 경로는 로컬 시드 데이터를 위해 열어둔 것이다. (설계 문서 8.1 장)
         * increase() / decrease() 를 여는 것보다 표면이 좁다.
         */
        fun create(
            brandId: Long,
            name: ProductName,
            price: Price,
            likeCount: LikeCount = LikeCount.ZERO,
        ): ProductModel = ProductModel(brandId = brandId, name = name, price = price, likeCount = likeCount)
    }
}
