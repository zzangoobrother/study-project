package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결제 API.
 *
 * PG 가 실패하거나 느려도 200 이다. 결과는 응답의 status 로 구분한다(Fallback). 새 ErrorType(503)을 만들지 않고,
 * 클라이언트가 볼 것을 status 하나로 줄인다. (2026-10-13 결제 설계 4.2 장)
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @PostMapping
    override fun request(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @RequestBody request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.request(request.toCommand(LoginId(loginId)))
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    /**
     * 인증하지 않는다 — PG 는 로그인 헤더를 보내지 않는다. 그래서 본문을 믿지 않고 PG 에 다시 묻는다.
     * 결제 건을 찾았는지와 무관하게 항상 200 이다. 무엇을 찾았는지 알려 줄 이유가 없다. (2026-10-13 결제 설계 5.1 장)
     */
    @PostMapping("/callback")
    override fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any> {
        paymentFacade.handleCallback(transactionKey = request.transactionKey, pgOrderId = request.orderId)
        return ApiResponse.success()
    }
}
