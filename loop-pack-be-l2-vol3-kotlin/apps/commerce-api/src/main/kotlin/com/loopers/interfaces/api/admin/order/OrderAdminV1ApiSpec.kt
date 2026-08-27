package com.loopers.interfaces.api.admin.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Order Admin V1 API", description = "Loopers 주문 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface OrderAdminV1ApiSpec {
    @Operation(
        summary = "주문 목록 조회",
        description = "전체 회원의 주문을 최신순으로 조회합니다. 응답에 주문 항목은 담기지 않으며 상세 조회에서 확인합니다. " +
            "탈퇴한 회원의 주문도 포함되며 loginId 가 채워집니다.",
    )
    fun getOrders(
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<OrderAdminV1Dto.OrderSummaryResponse>>

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 ID 로 조회합니다. 주문 항목까지 반환합니다. " +
            "공개 API 와 달리 소유자를 검증하지 않으므로 어떤 회원의 주문이든 조회할 수 있습니다.",
    )
    fun getOrder(
        @Schema(name = "주문 ID", description = "조회할 주문의 ID")
        orderId: Long,
    ): ApiResponse<OrderAdminV1Dto.OrderResponse>
}
