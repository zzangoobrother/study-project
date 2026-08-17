package com.loopers.application.admin.product

import com.loopers.application.admin.brand.BrandAdminInfo
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 상품 정보.
 *
 * brand 가 nullable 인 것은 공개 ProductInfo 와 같지만 의미가 다르다.
 * 공개에서는 "브랜드가 삭제됨" 도 null 이지만, 어드민에서는 삭제된 브랜드도 채워지므로
 * null 은 정말로 브랜드 행이 없는 경우뿐이다. FK 가 없어 이론상 가능하다.
 */
data class ProductAdminInfo(
    val id: Long,
    val name: ProductName,
    val price: Price,
    val likeCount: LikeCount,
    val brand: BrandAdminInfo?,
    val deletedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    /** deletedAt 만으로는 안 되는 이유는 BrandAdminInfo 와 같다 — Jackson 의 NON_NULL 설정 때문이다. */
    val deleted: Boolean get() = deletedAt != null

    companion object {
        fun of(model: ProductModel, brand: BrandAdminInfo?): ProductAdminInfo {
            return ProductAdminInfo(
                id = model.id,
                name = model.name,
                price = model.price,
                likeCount = model.likeCount,
                brand = brand,
                deletedAt = model.deletedAt,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
            )
        }
    }
}
