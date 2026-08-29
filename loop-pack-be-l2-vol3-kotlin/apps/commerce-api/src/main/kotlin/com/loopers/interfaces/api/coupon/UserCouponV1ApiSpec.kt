package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse

@Tag(name = "User Coupon V1 API", description = "내 쿠폰 API")
interface UserCouponV1ApiSpec {
    @Operation(summary = "내 쿠폰 목록", description = "발급받은 쿠폰을 최근순으로 조회한다. 상태를 함께 반환한다.")
    fun getUserCoupons(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "페이지 번호", description = "0 이상. 기본값 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 기본값 20")
        size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<CouponV1Dto.CouponResponse>>
}
