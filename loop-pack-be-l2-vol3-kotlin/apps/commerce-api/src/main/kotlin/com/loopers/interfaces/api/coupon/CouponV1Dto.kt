package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponInfo
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

class CouponV1Dto {
    /**
     * 쿠폰 응답. 발급 응답과 목록 원소가 같은 타입이다.
     *
     * 필드명이 도메인과 다른 것은 요구사항 명세의 와이어 계약을 따르기 때문이다.
     * 변환이 이 from() 한 곳에만 있으므로 도메인은 discountType / discountValue / expiresAt 를 유지한다.
     * (2026-09-01 설계 문서 5.2 장)
     *
     * couponId 는 정책 ID 다. 이 값을 그대로 주문 요청의 couponId 에 넣는다.
     */
    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long,
        val status: CouponStatus,
        val expiredAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse = CouponResponse(
                couponId = info.couponId,
                name = info.name,
                type = info.discountType,
                value = info.discountValue,
                minOrderAmount = info.minOrderAmount,
                status = info.status,
                expiredAt = info.expiresAt,
                usedAt = info.usedAt,
            )
        }
    }
}
