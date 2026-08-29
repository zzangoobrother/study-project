package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 회원에 딸린 쿠폰 목록.
 *
 * 경로가 users/me 인 이유는 UserLikeV1Controller 와 같다 — 인증이 없는 상태에서
 * 남의 목록을 지목할 수 있는 URL 을 만들지 않기 위해서다.
 *
 * 쿼리 파라미터를 DTO 로 묶지 않고 개별 RequestParam 으로 받는 이유도 같다.
 * ModelAttribute 바인딩이면 page=abc 가 500 이 되고, 개별 파라미터면 400 이 된다.
 */
@RestController
@RequestMapping("/api/v1/users/me/coupons")
class UserCouponV1Controller(
    private val couponFacade: CouponFacade,
) : UserCouponV1ApiSpec {
    @GetMapping
    override fun getUserCoupons(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<CouponV1Dto.CouponResponse>> {
        // 응답이 URL 이 아닌 헤더에 따라 달라지므로, Vary 없이는 공유 캐시가 다른 사용자에게 이 응답을 재사용한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.VARY, ApiHeaders.LOGIN_ID)

        return couponFacade.getUserCoupons(LoginId(loginId), PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { CouponV1Dto.CouponResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }
}
