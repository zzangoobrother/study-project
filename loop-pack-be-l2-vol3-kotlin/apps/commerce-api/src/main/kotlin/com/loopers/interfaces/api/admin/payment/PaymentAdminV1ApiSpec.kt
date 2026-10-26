package com.loopers.interfaces.api.admin.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment Admin V1 API", description = "Loopers 결제 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface PaymentAdminV1ApiSpec {
    @Operation(
        summary = "결제 수동 동기화",
        description = "콜백이나 스케줄러를 기다리지 않고 특정 결제를 PG 에 다시 물어 상태를 복구합니다. " +
            "스케줄러와 같은 확정 경로를 쓰므로 요청된 지 6 초 안의 건은 아직 바뀌지 않을 수 있고, PG 조회가 실패해도 " +
            "오류 없이 현재 상태를 돌려줍니다. 응답의 status 로 확정 여부를 판단합니다. " +
            "이미 SUCCESS · FAILED 인 결제는 다시 열지 않고 그대로 돌려줍니다.",
    )
    fun syncPayment(
        @Schema(name = "결제 ID", description = "동기화할 결제의 ID")
        paymentId: Long,
    ): ApiResponse<PaymentAdminV1Dto.PaymentResponse>
}
