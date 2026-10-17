package com.loopers.domain.payment

/** PG 가 알고 있는 거래. amount 는 상세 조회에서만 채워진다. */
data class PgTransaction(
    val transactionKey: String,
    val status: PgTransactionStatus,
    val reason: String?,
    val amount: Long?,
)

enum class PgTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
