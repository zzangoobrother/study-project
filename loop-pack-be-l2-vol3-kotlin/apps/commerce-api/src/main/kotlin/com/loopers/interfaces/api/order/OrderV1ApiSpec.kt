package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import java.time.LocalDate

@Tag(name = "Order V1 API", description = "Loopers 주문 API 입니다.")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = "주문 시점의 상품명과 가격이 스냅샷으로 저장됩니다. " +
            "한 항목이라도 재고가 모자라면 주문 전체가 실패하며 409 입니다. " +
            "같은 상품을 여러 항목으로 보내면 400 입니다.",
    )
    fun place(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "주문 요청", description = "주문할 상품과 수량 목록")
        request: OrderV1Dto.PlaceRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(
        summary = "내 주문 목록 조회",
        description = "최근 주문이 앞에 옵니다. 응답에 주문 항목은 담기지 않으며 상세 조회에서 확인합니다. " +
            "startAt / endAt 은 생략할 수 있고, endAt 은 그날을 포함합니다.",
    )
    fun getOrders(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "조회 시작일", description = "yyyy-MM-dd. 생략하면 제한이 없습니다")
        startAt: LocalDate?,
        @Schema(name = "조회 종료일", description = "yyyy-MM-dd. 그날을 포함합니다. 생략하면 제한이 없습니다")
        endAt: LocalDate?,
        @Schema(name = "페이지 번호", description = "0 이상. 기본값 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 기본값 20")
        size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<OrderV1Dto.OrderSummaryResponse>>

    @Operation(
        summary = "내 주문 상세 조회",
        description = "주문 항목까지 반환합니다. 다른 회원의 주문은 존재 여부를 숨기기 위해 404 입니다.",
    )
    fun getOrder(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "주문 ID", description = "조회할 주문의 ID")
        orderId: Long,
        response: HttpServletResponse,
    ): ApiResponse<OrderV1Dto.OrderResponse>
}
