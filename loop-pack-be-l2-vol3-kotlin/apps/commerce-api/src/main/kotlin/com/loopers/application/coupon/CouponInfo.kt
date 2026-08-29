package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCouponModel
import java.time.ZonedDateTime

/**
 * 쿠폰 계층 밖으로 전달되는 정보.
 *
 * status 는 저장된 값이 아니라 usedAt 과 expiresAt 에서 계산한 것이다. (설계 문서 5.4 장)
 * 그 사실이 밖으로 드러나지 않도록 여기서 확정해 내보낸다.
 *
 * name 을 String 으로 펼치는 이유는 OrderInfo 와 같다 — 이 타입을 소비하는 곳이 컨트롤러 하나뿐이고
 * 거기서 다시 값 객체를 풀어야 한다. discountType 과 status 는 값 객체가 아니라 열거형 자체가 값이므로
 * 그대로 내보낸다.
 */
data class CouponInfo(
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
        /**
         * now 를 인자로 받는 이유는 목록의 모든 원소가 같은 순간을 기준으로 판정되어야 하기 때문이다.
         * 안에서 ZonedDateTime.now() 를 부르면 원소마다 기준 시각이 미세하게 달라져,
         * 만료 경계에 걸린 두 쿠폰의 상태가 서로 모순되는 조합이 나올 수 있다.
         */
        fun of(model: UserCouponModel, now: ZonedDateTime): CouponInfo = CouponInfo(
            id = model.id,
            couponId = model.couponId,
            name = model.name.value,
            discountType = model.discountType,
            discountValue = model.discountValue,
            status = model.statusAt(now),
            expiresAt = model.expiresAt,
            usedAt = model.usedAt,
        )
    }
}
