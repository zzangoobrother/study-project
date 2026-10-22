package com.loopers.domain.payment

/** PG 가 알고 있는 거래. amount · orderId 는 상세 조회에서만 채워진다. orderId 는 PG 의 거래가 이 결제의 주문 것인지 확인하는 데 쓴다. */
data class PgTransaction(
    val transactionKey: String,
    val status: PgTransactionStatus,
    val reason: String?,
    val amount: Long?,
    val orderId: Long? = null,
)

enum class PgTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
