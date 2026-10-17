package com.loopers.application.payment

import com.loopers.domain.payment.PaymentModel

/** 결제 계층 밖으로 나가는 결과. 카드 번호는 내보내지 않는다. */
data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val amount: Long,
    val status: String,
    val reason: String?,
) {
    companion object {
        fun of(model: PaymentModel): PaymentInfo = PaymentInfo(
            paymentId = model.id,
            orderId = model.orderId,
            amount = model.amount.value,
            status = model.status.name,
            reason = model.failureReason,
        )
    }
}
