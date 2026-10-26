package com.loopers.interfaces.api.admin.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결제 어드민 API.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다. 공개 결제 API 에 두지 않고 어드민 경로에 둔 이유는
 * 이 호출이 PG 조회를 일으키고 결제 · 주문 상태를 바꿀 수 있어서다. 인증 없는 경로에 열면 누구나 PG 를 두드릴 수 있다.
 * 별도 어드민 Facade 를 두지 않고 PaymentFacade 를 직접 부른다 — 확정 경로를 sync 하나로 유지하기 위해서다. (2026-10-26 결제 설계 5.5 장)
 */
@RestController
@RequestMapping("/api-admin/v1/payments")
class PaymentAdminV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentAdminV1ApiSpec {
    @PostMapping("/{paymentId}/sync")
    override fun syncPayment(
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentAdminV1Dto.PaymentResponse> {
        return paymentFacade.syncManually(paymentId)
            .let { PaymentAdminV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
