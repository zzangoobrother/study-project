package com.loopers.application.product

import com.loopers.domain.product.ProductModel

/**
 * 상품 캐시에 저장하는 값. 원시 타입만 담는다.
 *
 * ProductModel(엔티티)이나 ProductInfo(값 객체)를 그대로 직렬화하지 않는다. 그 클래스를 고치는 사람이
 * 캐시의 JSON 형식까지 바꾸고 있다는 사실을 모르게 된다.
 *
 * 브랜드는 brandId 만 담는다. 브랜드를 고칠 때 그 브랜드 상품의 키를 모두 지우지 않아도 되게 하기 위해서다.
 * (2026-10-04 상품 캐시 설계 3.3 장)
 *
 * 필드를 바꾸면 ProductRedisCache 의 키 버전(v1)을 올린다. (2026-10-04 상품 캐시 설계 4 장)
 */
data class ProductCacheValue(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
) {
    companion object {
        fun from(model: ProductModel): ProductCacheValue =
            ProductCacheValue(
                id = model.id,
                name = model.name.value,
                price = model.price.value,
                likeCount = model.likeCount.value,
                brandId = model.brandId,
            )
    }
}
