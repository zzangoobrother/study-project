package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 쿠폰 발급.
 *
 * 캐시 헤더를 세팅하지 않는다. POST 응답은 캐시 대상이 아니다.
 * 경로 변수가 숫자가 아니면 MethodArgumentTypeMismatchException 이 되어 이미 400 으로 처리된다.
 */
@RestController
@RequestMapping("/api/v1/coupons")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
) : CouponV1ApiSpec {
    @PostMapping("/{couponId}/issue")
    override fun issue(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponResponse> {
        return couponFacade.issue(LoginId(loginId), couponId)
            .let { CouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
