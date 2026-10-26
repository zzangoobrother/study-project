package com.loopers.interfaces.api.admin.payment

import com.loopers.application.payment.PaymentInfo

class PaymentAdminV1Dto {
    /**
     * 수동 동기화 응답. 공개 PaymentV1Dto.PaymentResponse 와 타입을 나누는 이유는 transactionKey 때문이다.
     * 운영자는 이 키로 PG 거래를 직접 조회해 고아 거래 · 금액 불일치를 확인하지만(설계 9.1 장), 사용자에게는 필요 없다.
     * 카드 번호는 담지 않는다.
     *
     * status 가 REQUESTED · PENDING 이면 아직 확정되지 않았다. PG 가 아직 PENDING 이거나, 조회가 실패했거나,
     * 요청된 지 6 초가 안 지나 sync 가 건너뛴 것이다. 이 경우도 오류가 아니라 200 이다.
     */
    data class PaymentResponse(
        val paymentId: Long,
        val orderId: Long,
        val amount: Long,
        val status: String,
        val transactionKey: String?,
        val reason: String?,
    ) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse = PaymentResponse(
                paymentId = info.paymentId,
                orderId = info.orderId,
                amount = info.amount,
                status = info.status,
                transactionKey = info.transactionKey,
                reason = info.reason,
            )
        }
    }
}
