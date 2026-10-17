package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment V1 API", description = "Loopers 결제 API 입니다.")
interface PaymentV1ApiSpec {
    @Operation(
        summary = "결제 요청",
        description = "주문에 대해 카드 결제를 요청합니다. 결제는 비동기로 처리됩니다. " +
            "응답 status 가 PENDING · REQUESTED 면 결과가 아직 없으며 주문 조회의 status 로 확인합니다. " +
            "FAILED 면 다른 카드로 다시 요청할 수 있습니다. 이미 결제 중이거나 완료된 주문이면 409 입니다.",
    )
    fun request(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "결제 요청", description = "주문 ID 와 카드 정보")
        request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(
        summary = "PG 결제 결과 콜백",
        description = "PG 가 결제 처리를 마치면 호출합니다. 본문의 결과를 그대로 반영하지 않고 PG 에 다시 조회해 확정합니다. 항상 200 입니다.",
    )
    fun callback(
        @Schema(name = "콜백 본문", description = "PG 의 거래 정보")
        request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any>
}
