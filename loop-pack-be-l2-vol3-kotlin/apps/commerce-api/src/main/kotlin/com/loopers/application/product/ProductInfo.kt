package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName

/**
 * 계층 밖으로 전달되는 상품 정보.
 *
 * brand 가 nullable 인 것은 브랜드가 소프트 삭제된 경우를 표현하기 위해서다. (설계 문서 6.3 장)
 * 상품 자체는 살아 있으므로 목록에서 빠지거나 404 가 되어서는 안 된다.
 */
data class ProductInfo(
    val id: Long,
    val name: ProductName,
    val price: Price,
    val likeCount: LikeCount,
    val brand: BrandInfo?,
) {
    companion object {
        fun of(model: ProductModel, brand: BrandInfo?): ProductInfo {
            return ProductInfo(
                id = model.id,
                name = model.name,
                price = model.price,
                likeCount = model.likeCount,
                brand = brand,
            )
        }

        /** 캐시에서 읽은 값. DB 에서 읽은 값을 그대로 담았으므로 값 객체 검증에서 실패하지 않는다. */
        fun of(value: ProductCacheValue, brand: BrandInfo?): ProductInfo {
            return ProductInfo(
                id = value.id,
                name = ProductName(value.name),
                price = Price(value.price),
                likeCount = LikeCount(value.likeCount),
                brand = brand,
            )
        }
    }
}
