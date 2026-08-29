package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponInfo
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

class CouponV1Dto {
    /**
     * 쿠폰 응답. 발급 응답과 목록 원소가 같은 타입이다.
     *
     * id 와 couponId 를 둘 다 내려준다. 주문에 넣어야 하는 것은 id(발급된 쿠폰)이고,
     * couponId 는 어떤 정책에서 나왔는지를 표시할 때만 쓴다. (설계 문서 4.1 장)
     */
    data class CouponResponse(
        val id: Long,
        val couponId: Long,
        val name: String,
        val discountType: DiscountType,
        val discountValue: Long,
        val status: CouponStatus,
        val expiresAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse = CouponResponse(
                id = info.id,
                couponId = info.couponId,
                name = info.name,
                discountType = info.discountType,
                discountValue = info.discountValue,
                status = info.status,
                expiresAt = info.expiresAt,
                usedAt = info.usedAt,
            )
        }
    }
}
