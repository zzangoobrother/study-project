package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 쿠폰 정책 정보.
 *
 * 공개 CouponInfo 를 재사용하지 않는 이유는 이 타입이 deletedAt 과 issuedCount 를 담기 때문이다.
 * 둘 다 공개 응답에 있어서는 안 되는 값이므로, 타입을 나눠 공개 경로로 샐 여지를 없앤다.
 * (2026-09-01 설계 문서 7.2 장)
 *
 * 공개 CouponInfo 가 발급된 쿠폰(user_coupons)을 나르는 것과 달리 이것은 정책(coupons)을 나른다.
 * 이름이 비슷하지만 다른 것을 가리킨다.
 */
data class CouponAdminInfo(
    val id: Long,
    val name: CouponName,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long,
    val expiresAt: ZonedDateTime,
    val issuedCount: Long,
    val deletedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    /** deletedAt 만으로는 안 되는 이유는 ProductAdminInfo 와 같다 — Jackson 의 NON_NULL 설정 때문이다. */
    val deleted: Boolean get() = deletedAt != null

    companion object {
        fun of(model: CouponModel, issuedCount: Long): CouponAdminInfo = CouponAdminInfo(
            id = model.id,
            name = model.name,
            discountType = model.discountType,
            discountValue = model.discountValue,
            minOrderAmount = model.minOrderAmount,
            expiresAt = model.expiresAt,
            issuedCount = issuedCount,
            deletedAt = model.deletedAt,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
        )
    }
}
