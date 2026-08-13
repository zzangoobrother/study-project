package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo

class ProductV1Dto {
    /**
     * 상품 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * 브랜드를 평면 필드(brandId / brandName)가 아니라 중첩 객체로 두는 이유는,
     * "브랜드 정보를 알 수 없음" 을 brand = null 하나로 표현하기 위해서다.
     * 평면이면 두 필드가 따로 null 이 되어 한쪽만 null 인 어긋난 상태가 표현 가능해진다.
     *
     * 브랜드 설명은 담지 않는다. 필요하면 GET /api/v1/brands/{id} 를 부른다. (설계 문서 4.5 장)
     */
    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brand: BrandSummary?,
    ) {
        data class BrandSummary(
            val id: Long,
            val name: String,
        )

        companion object {
            fun from(info: ProductInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name.value,
                    price = info.price.value,
                    likeCount = info.likeCount.value,
                    brand = info.brand?.let { BrandSummary(id = it.id, name = it.name.value) },
                )
            }
        }
    }
}
