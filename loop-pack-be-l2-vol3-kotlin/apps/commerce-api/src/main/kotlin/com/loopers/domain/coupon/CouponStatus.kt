package com.loopers.domain.coupon

/**
 * 발급된 쿠폰의 상태.
 *
 * 컬럼으로 저장하지 않는다. usedAt 과 expiresAt 에서 계산한 표현 전용 값이다. (설계 문서 5.4 장)
 * 저장하면 EXPIRED 로의 전이를 누군가 수행해야 하는데, 시간이 흐를 뿐 아무도 그 전이를 일으키지 않는다.
 */
enum class CouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}
