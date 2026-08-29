package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon V1 API", description = "쿠폰 API")
interface CouponV1ApiSpec {
    @Operation(summary = "쿠폰 발급", description = "쿠폰 정책 ID 로 내 쿠폰을 발급받는다. 1인 1매다.")
    fun issue(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "쿠폰 정책 ID", description = "발급받을 쿠폰 정책의 ID")
        couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponResponse>
}
