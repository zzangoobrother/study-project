package com.loopers.domain.payment

import com.loopers.domain.product.Price

/**
 * 외부 PG 계약. domain 에 두고 infrastructure 가 구현한다(DIP).
 *
 * 예외를 던지지 않고 결과 타입을 돌려준다. 타임아웃 · 서킷 · HTTP 예외의 해석은 구현이 끝내고,
 * Facade 는 "보냈는지 / 안 보냈는지 / 모르는지" 만 안다. (2026-10-13 결제 설계 4.2 장)
 */
interface PaymentGateway {
    fun request(request: PgPaymentRequest): PgRequestResult

    fun findTransaction(userId: Long, transactionKey: String): PgQueryResult<PgTransaction>

    /** 주문에 엮인 거래 목록. 목록 원소에는 금액이 없다(amount = null). */
    fun findTransactionsByOrder(userId: Long, orderId: Long): PgQueryResult<List<PgTransaction>>
}

data class PgPaymentRequest(
    val userId: Long,
    val orderId: Long,
    val cardType: CardType,
    val cardNo: CardNo,
    val amount: Price,
)
