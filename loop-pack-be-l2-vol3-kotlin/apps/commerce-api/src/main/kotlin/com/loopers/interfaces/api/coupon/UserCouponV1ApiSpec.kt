package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse

@Tag(name = "User Coupon V1 API", description = "내 쿠폰 API")
interface UserCouponV1ApiSpec {
    @Operation(summary = "내 쿠폰 목록", description = "발급받은 쿠폰을 최근순으로 조회한다. 상태를 함께 반환한다.")
    fun getUserCoupons(
        @Parameter(name = ApiHeaders.LOGIN_ID, description = "로그인 ID", required = true)
        loginId: String,
        @Parameter(description = "페이지 번호 (0 이상)")
        page: Int?,
        @Parameter(description = "페이지 크기 (1~100)")
        size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<CouponV1Dto.CouponResponse>>
}
