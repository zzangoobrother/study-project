package com.loopers.domain.coupon

import java.time.ZonedDateTime

/**
 * 쿠폰 정책 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * discountValue 와 minOrderAmount 가 원시 타입인 것은 유효 범위가 discountType 에 따라 달라
 * 단일 값으로 판정할 수 없기 때문이다. 그 검증은 CouponModel 이 소유한다.
 * (2026-08-30 설계 문서 5.6 장)
 */
class CouponCommand {
    data class Register(
        val name: CouponName,
        val discountType: DiscountType,
        val discountValue: Long,
        val minOrderAmount: Long,
        val expiresAt: ZonedDateTime,
    )

    data class Change(
        val id: Long,
        val name: CouponName,
        val discountType: DiscountType,
        val discountValue: Long,
        val minOrderAmount: Long,
        val expiresAt: ZonedDateTime,
    )
}
