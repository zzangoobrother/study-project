package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.ProductAdminInfo
import java.time.ZonedDateTime

class ProductAdminV1Dto {
    /**
     * 어드민 상품 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * 브랜드를 평면 필드가 아니라 중첩 객체로 두는 이유는 공개 API 와 같다 —
     * 평면이면 brandId 와 brandName 이 따로 null 이 되어 어긋난 상태가 표현 가능해진다.
     *
     * BrandSummary 에 deleted 를 두는 이유는 어드민만의 요구다.
     * 공개 API 는 삭제된 브랜드를 brand = null 로 표현하지만, 어드민에서 그러면
     * "브랜드가 삭제됨" 과 "브랜드를 알 수 없음" 이 같은 표현으로 뭉개진다.
     */
    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brand: BrandSummary?,
        val deleted: Boolean,
        val deletedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        data class BrandSummary(
            val id: Long,
            val name: String,
            val deleted: Boolean,
        )

        companion object {
            fun from(info: ProductAdminInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name.value,
                    price = info.price.value,
                    likeCount = info.likeCount.value,
                    brand = info.brand?.let {
                        BrandSummary(id = it.id, name = it.name.value, deleted = it.deleted)
                    },
                    deleted = info.deleted,
                    deletedAt = info.deletedAt,
                    createdAt = info.createdAt,
                    updatedAt = info.updatedAt,
                )
            }
        }
    }
}
