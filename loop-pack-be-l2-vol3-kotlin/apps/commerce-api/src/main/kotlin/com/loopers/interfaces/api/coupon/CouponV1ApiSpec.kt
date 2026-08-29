package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon V1 API", description = "쿠폰 API")
interface CouponV1ApiSpec {
    @Operation(summary = "쿠폰 발급", description = "쿠폰 정책 ID 로 내 쿠폰을 발급받는다. 1인 1매다.")
    fun issue(
        @Parameter(name = ApiHeaders.LOGIN_ID, description = "로그인 ID", required = true)
        loginId: String,
        @Parameter(description = "쿠폰 정책 ID", required = true)
        couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponResponse>
}
