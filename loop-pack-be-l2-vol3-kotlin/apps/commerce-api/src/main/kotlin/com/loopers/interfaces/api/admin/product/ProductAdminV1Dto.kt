package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.ProductAdminInfo
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.Stock
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
        val stock: Long,
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
                    stock = info.stock.value,
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

    /**
     * 상품 등록 요청.
     *
     * likeCount 를 받지 않는다. 등록 시 항상 0 이며, 이 값을 바꾸는 메서드의 모양은
     * 좋아요 기능이 붙을 때 결정되어야 한다는 LikeCount 의 주석을 그대로 존중한다.
     */
    data class RegisterRequest(
        val brandId: Long,
        val name: String,
        val price: Long,
        val stock: Long,
    ) {
        fun toCommand(): ProductCommand.Register = ProductCommand.Register(
            brandId = brandId,
            name = ProductName(name),
            price = Price(price),
            stock = Stock(stock),
        )
    }

    /**
     * 상품 수정 요청. PUT 이므로 전체 교체다.
     *
     * brandId 필드가 없는 것이 "상품의 브랜드는 수정할 수 없음" 요구사항의 이행이다.
     * 클라이언트가 본문에 brandId 를 실어 보내면 FAIL_ON_UNKNOWN_PROPERTIES 가 꺼져 있어 조용히 무시된다.
     * 그 침묵은 설계 문서 10.3 장에 위험으로 기록돼 있으며, 필요해지면 "있으면 400" 으로 강화한다.
     */
    data class ChangeRequest(
        val name: String,
        val price: Long,
        val stock: Long,
    ) {
        fun toCommand(id: Long): ProductCommand.Change = ProductCommand.Change(
            id = id,
            name = ProductName(name),
            price = Price(price),
            stock = Stock(stock),
        )
    }
}
