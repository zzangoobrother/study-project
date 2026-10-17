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
}
