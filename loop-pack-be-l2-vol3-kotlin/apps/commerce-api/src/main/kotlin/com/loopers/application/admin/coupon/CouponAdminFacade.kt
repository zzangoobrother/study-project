package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 쿠폰 정책 어드민 유스케이스.
 *
 * 트랜잭션이 없다. 정책 삭제가 발급분을 건드리지 않아(2026-09-01 설계 문서 5.5 장) 두 애그리거트에 걸친 변경이
 * 하나도 없기 때문이다. ProductAdminFacade.delete 가 @Transactional 을 필요로 했던 것과 대비된다.
 * 쓰기 경계는 CouponService 의 @Transactional 이 소유한다.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 */
@Component
class CouponAdminFacade(
    private val couponService: CouponService,
) {
    fun register(command: CouponCommand.Register): CouponAdminInfo {
        // 갓 등록한 정책의 발급 건수는 반드시 0 이다. 세러 가지 않는다.
        return CouponAdminInfo.of(couponService.register(command), issuedCount = 0)
    }

    fun getCoupon(id: Long): CouponAdminInfo {
        val coupon = couponService.getCouponIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $id] 존재하지 않는 쿠폰입니다.",
            )

        return toInfo(coupon)
    }

    fun change(command: CouponCommand.Change): CouponAdminInfo {
        return toInfo(couponService.change(command))
    }

    fun delete(id: Long) {
        couponService.delete(id)
    }

    private fun toInfo(coupon: CouponModel): CouponAdminInfo {
        val issuedCount = couponService.countIssuedByCouponIds(listOf(coupon.id))[coupon.id] ?: 0
        return CouponAdminInfo.of(coupon, issuedCount)
    }
}
