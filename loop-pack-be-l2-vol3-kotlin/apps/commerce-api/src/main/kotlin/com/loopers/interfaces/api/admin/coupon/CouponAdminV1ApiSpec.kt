package com.loopers.interfaces.api.admin.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 정책 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface CouponAdminV1ApiSpec {
    @Operation(
        summary = "쿠폰 정책 상세 조회",
        description = "정책 ID 로 조회합니다. 삭제된 정책도 200 으로 반환하며 deleted 가 true 입니다.",
    )
    fun getCoupon(
        @Schema(name = "쿠폰 정책 ID", description = "조회할 정책의 ID")
        couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 정책 등록",
        description = "정액(FIXED) 또는 정률(RATE) 정책을 등록합니다. " +
            "정액은 1 원 이상, 정률은 1 이상 100 이하여야 하며 아니면 400 입니다.",
    )
    fun register(
        request: CouponAdminV1Dto.RegisterRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 정책 수정",
        description = "전 필드를 교체합니다. 이미 발급된 쿠폰은 발급 시점 조건을 유지합니다. " +
            "삭제된 정책은 409 Conflict 입니다.",
    )
    fun change(
        @Schema(name = "쿠폰 정책 ID", description = "수정할 정책의 ID")
        couponId: Long,
        request: CouponAdminV1Dto.ChangeRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 정책 삭제",
        description = "정책을 소프트 삭제합니다. 이미 발급된 쿠폰은 회수되지 않고 계속 사용할 수 있습니다. " +
            "이미 삭제된 정책에 대해서도 200 입니다.",
    )
    fun delete(
        @Schema(name = "쿠폰 정책 ID", description = "삭제할 정책의 ID")
        couponId: Long,
    ): ApiResponse<Any>
}
