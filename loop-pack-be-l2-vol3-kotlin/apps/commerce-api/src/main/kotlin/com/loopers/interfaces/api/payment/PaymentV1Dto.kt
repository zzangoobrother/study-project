package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.user.LoginId

class PaymentV1Dto {
    /**
     * 결제 요청. 금액은 받지 않는다 — 주문의 paidAmount 가 정한다. 클라이언트가 금액을 정하게 두면 위조할 수 있다.
     *
     * cardType 을 열거형으로 받는 것만으로 목록 밖 카드사는 JSON 파싱 단계에서 400 이 된다.
     * cardNo 를 CardNo 로 감싸는 것만으로 형식 검증이 끝난다.
     */
    data class PaymentRequest(
        val orderId: Long,
        val cardType: CardType,
        val cardNo: String,
    ) {
        fun toCommand(loginId: LoginId): PaymentCommand.Request = PaymentCommand.Request(
            loginId = loginId,
            orderId = orderId,
            cardType = cardType,
            cardNo = CardNo(cardNo),
        )
    }

    /**
     * status 가 REQUESTED · PENDING 이면 결과가 아직 없다. 클라이언트는 주문 조회의 status 로 결과를 확인한다.
     * (2026-10-13 결제 설계 5.4 장)
     */
    data class PaymentResponse(
        val paymentId: Long,
        val orderId: Long,
        val amount: Long,
        val status: String,
        val reason: String?,
    ) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse = PaymentResponse(
                paymentId = info.paymentId,
                orderId = info.orderId,
                amount = info.amount,
                status = info.status,
                reason = info.reason,
            )
        }
    }
}
