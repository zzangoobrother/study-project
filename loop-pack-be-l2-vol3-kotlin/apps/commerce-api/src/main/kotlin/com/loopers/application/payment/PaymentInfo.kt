package com.loopers.application.payment

import com.loopers.domain.payment.PaymentModel

/**
 * 결제 계층 밖으로 나가는 결과. 카드 번호는 내보내지 않는다.
 * transactionKey 는 PG 거래를 찾는 키라 운영자의 수동 확인에 필요하다. 공개 응답(PaymentV1Dto)은 담지 않는다. (2026-10-26 결제 설계 5.5 장)
 */
data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val amount: Long,
    val status: String,
    val reason: String?,
    val transactionKey: String?,
) {
    companion object {
        fun of(model: PaymentModel): PaymentInfo = PaymentInfo(
            paymentId = model.id,
            orderId = model.orderId,
            amount = model.amount.value,
            status = model.status.name,
            reason = model.failureReason,
            transactionKey = model.transactionKey,
        )
    }
}
