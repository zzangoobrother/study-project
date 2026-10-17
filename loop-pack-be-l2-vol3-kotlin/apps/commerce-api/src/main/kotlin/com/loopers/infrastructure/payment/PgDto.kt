package com.loopers.infrastructure.payment

/**
 * PG(pg-simulator) HTTP 형식. 우리 도메인 타입과 따로 둔다 — PG 형식이 바뀌어도 고칠 곳이 이 파일과 PgPaymentGateway 뿐이게.
 * 열거형도 문자열로 받는다. PG 가 새 상태를 추가하면 역직렬화가 아니라 번역 단계에서 걸러야 원인이 보인다.
 */
object PgDto {
    /**
     * PG 는 주문 ID 가 6 자 미만이면 400 이다. 우리 주문 ID 는 1 부터라 0 으로 채운다.
     * 되돌릴 때는 toLong() 이면 된다. (2026-10-13 결제 설계 4.4 장)
     */
    fun toPgOrderId(orderId: Long): String = orderId.toString().padStart(6, '0')

    data class PaymentRequest(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    data class Response<T>(
        val meta: Meta,
        val data: T?,
    ) {
        data class Meta(
            val result: String,
            val errorCode: String?,
            val message: String?,
        )
    }

    data class TransactionResponse(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )

    data class TransactionDetailResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )

    data class OrderResponse(
        val orderId: String,
        val transactions: List<TransactionResponse>,
    )
}
