package com.loopers.interfaces.api.admin.coupon

import com.loopers.application.admin.coupon.CouponAdminFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 쿠폰 정책 어드민 API.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 * 목록에 필터 파라미터가 없는 것은 요구사항에 없기 때문이며, 정렬은 최신순 고정이다.
 */
@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponAdminV1Controller(
    private val couponAdminFacade: CouponAdminFacade,
) : CouponAdminV1ApiSpec {
    @GetMapping("/{couponId}")
    override fun getCoupon(
        @PathVariable couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        return couponAdminFacade.getCoupon(couponId)
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    override fun register(
        @RequestBody request: CouponAdminV1Dto.RegisterRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        return couponAdminFacade.register(request.toCommand())
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{couponId}")
    override fun change(
        @PathVariable couponId: Long,
        @RequestBody request: CouponAdminV1Dto.ChangeRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        return couponAdminFacade.change(request.toCommand(couponId))
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{couponId}")
    override fun delete(
        @PathVariable couponId: Long,
    ): ApiResponse<Any> {
        couponAdminFacade.delete(couponId)
        return ApiResponse.success()
    }
}
